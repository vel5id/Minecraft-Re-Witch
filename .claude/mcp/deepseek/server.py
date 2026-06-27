#!/usr/bin/env python3
"""MCP server: an autonomous DeepSeek file-editing agent confined to a git worktree.

Exposes:
  deepseek_task(task, base_ref="HEAD", context_files=[]) -> {summary, changed_files, diff,
                                                             worktree_path, branch, iterations, status}
  deepseek_cleanup(worktree_path) -> {ok}

The agent has file tools only (no bash). Verification and merge stay with the calling session.
Env: DEEPSEEK_API_KEY (required), DEEPSEEK_MODEL, DEEPSEEK_BASE_URL, DEEPSEEK_MAX_ITERATIONS.
Deps: pip install "mcp[cli]" httpx
"""
import functools
import os
import subprocess

import anyio
from mcp.server.fastmcp import FastMCP

import deepseek_client
import testfiles
import verify
import worktree
from agent_loop import run_agent
from fs_tools import FsTools

mcp = FastMCP("deepseek")

API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-chat")
BASE_URL = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
MAX_ITERS = int(os.environ.get("DEEPSEEK_MAX_ITERATIONS", "40"))
MAX_ROUNDS = int(os.environ.get("DEEPSEEK_MAX_ROUNDS", "3"))
# How many delegations may run AT ONCE. Each one is heavy (an agent loop + a full build per
# round), so the cap bounds concurrent worker threads / Gradle-JVM builds rather than letting a
# fan-out spawn dozens. Excess calls queue on the limiter and run as slots free up.
MAX_CONCURRENCY = int(os.environ.get("DEEPSEEK_MAX_CONCURRENCY", "3"))
_LIMITER = None


def _get_limiter():
    """Lazily build the shared CapacityLimiter (first call runs inside the event loop)."""
    global _LIMITER
    if _LIMITER is None:
        _LIMITER = anyio.CapacityLimiter(MAX_CONCURRENCY)
    return _LIMITER


def _repo_root() -> str:
    return subprocess.run(["git", "rev-parse", "--show-toplevel"],
                          check=True, capture_output=True, text=True).stdout.strip()


def _chat(messages, tools):
    return deepseek_client.chat(messages, tools, api_key=API_KEY, model=MODEL, base_url=BASE_URL)


def _derive_status(gate_configured: bool, ran: bool, passed) -> str:
    """Map a verify-gate outcome to a caller-facing status.

    A gate that could not EXECUTE (`ran=False`) is its own `gate_error` state — never
    `verify_failed` (the gate ran and the code failed) and never `verified`. Conflating them
    would let a harness/environment problem (e.g. a non-executable `./gradlew`, a missing binary,
    a malformed command) masquerade as a code verdict, or let the agent's untrustworthy
    self-report stand in for a real gate.
    """
    if not gate_configured:
        return "unverified"      # no gate — caller must treat as amber
    if not ran:
        return "gate_error"      # gate could not execute — NOT a code verdict; fix the command/env
    return "verified" if passed else "verify_failed"


def _run_task(task, base_ref, verify_command, verify_cwd, verify_env,
              verify_timeout, context_files, max_rounds) -> dict:
    """Synchronous worker: create a worktree, iterate the agent to a green gate, return the result.

    Runs in a worker thread (offloaded by `_dispatch_task` under a CapacityLimiter), so concurrent
    delegations execute in parallel instead of blocking the server's event loop. Every git op is
    scoped to this task's own worktree/branch, so parallel runs don't collide.
    """
    rounds_cap = max_rounds if max_rounds is not None else MAX_ROUNDS
    root = _repo_root()
    wt = worktree.create(base_ref, root)
    base_sha = wt["base_sha"]
    try:
        fs = FsTools(wt["path"])
        context = ""
        for rel in (context_files or []):
            r = fs.read_file(rel)
            if r["ok"]:
                context += f"\n### {rel}\n{r['content']}\n"

        brief = task
        rounds = 0
        result = None
        gate = {"gate_configured": bool(verify_command), "ran": False, "passed": None,
                "timed_out": False, "excerpt": ""}
        for rnd in range(1, rounds_cap + 1):
            rounds = rnd
            # Context files only seed the first round; later rounds carry the prior failure instead.
            result = run_agent(brief, fs, _chat, max_iterations=MAX_ITERS,
                               context=context if rnd == 1 else "")
            if not verify_command:
                break
            v = verify.run(verify_command, cwd=os.path.join(wt["path"], verify_cwd),
                           env=verify_env, timeout=verify_timeout)
            gate = {"gate_configured": True, **v}
            if v["passed"]:
                break
            if not v["ran"]:
                # The gate could not execute (bad command, missing/non-runnable entry point, bad
                # env) — a harness/caller problem the agent cannot fix by iterating. Stop now
                # instead of burning rounds feeding back an error the model never caused.
                break
            brief = (task + "\n\n--- The automated verification FAILED on your previous attempt. "
                     "Output excerpt:\n" + v["excerpt"] +
                     "\nFix the implementation so the verification passes. "
                     "Do NOT modify, delete, disable, or weaken any test to make it pass.")

        files = worktree.changed_files(wt["path"], base_sha)
        if files:
            worktree.commit(wt["path"], (f"deepseek: {result['summary'] or task}")[:200])
        test_changed, _ = testfiles.partition(files)

        status = _derive_status(bool(verify_command), gate.get("ran", False), gate.get("passed"))

        return {
            "status": status,
            "verify": gate,                # {gate_configured, ran, passed, timed_out, excerpt}
            "summary": result["summary"],
            "rounds": rounds,
            "agent_iterations": result["iterations"],
            "changed_files": files,
            "test_files_changed": test_changed,   # non-empty -> caller reads these diffs (anti-gaming)
            "diff_stat": worktree.diff_stat(wt["path"], base_sha),
            "diff": worktree.diff(wt["path"], base_sha),  # full diff — fetch on demand, not the default read
            "worktree_path": wt["path"],
            "branch": wt["branch"],
            "base_sha": base_sha,
        }
    except Exception as e:
        return {"status": "error", "error": str(e),
                "worktree_path": wt["path"], "branch": wt["branch"], "rounds": 0}


async def _dispatch_task(task, base_ref="HEAD", verify_command=None, verify_cwd=".",
                         verify_env=None, verify_timeout=300, context_files=None,
                         max_rounds=None) -> dict:
    """Guard the API key, then offload the blocking worker to a thread under the shared limiter.

    This is what makes the tool non-blocking: FastMCP runs a sync tool INLINE on the server's one
    event loop (`fn_is_async` -> `fn(...)`), so a long delegation would serialize every other call.
    Offloading to `anyio.to_thread.run_sync(..., limiter=...)` lets up to `MAX_CONCURRENCY` run at
    once; the rest queue on the limiter.
    """
    if not API_KEY:
        return {"status": "error", "error": "DEEPSEEK_API_KEY is not set"}
    return await anyio.to_thread.run_sync(
        functools.partial(_run_task, task, base_ref, verify_command, verify_cwd,
                          verify_env, verify_timeout, context_files, max_rounds),
        limiter=_get_limiter(), abandon_on_cancel=False,
    )


@mcp.tool()
async def deepseek_task(task: str, base_ref: str = "HEAD", verify_command: list | str | None = None,
                        verify_cwd: str = ".", verify_env: dict | None = None,
                        verify_timeout: int = 300, context_files: list | None = None,
                        max_rounds: int | None = None) -> dict:
    """Delegate an implementation task to the DeepSeek agent in an isolated worktree off `base_ref`,
    iterate it to a GREEN gate, and return a compact result the caller can accept WITHOUT re-reading
    the implementation.

    The delegation contract (token economy): the caller writes the test(s) that define "done" and
    passes them as the `verify_command` gate; the agent writes only the implementation. This server
    runs `verify_command` itself (the authoritative gate — never the agent's self-report) and, on
    failure, feeds the error back to the agent and retries, up to `max_rounds` — all in-process, so
    the iterate loop costs the caller ZERO tokens. The caller reads `status` + `verify.passed` +
    `summary` + `diff_stat` + `test_files_changed`; it reads the full `diff` only on a red gate or
    when `test_files_changed` is non-empty (the anti-gaming check). Keep the tests in `base_ref`
    immutable so a green gate is trustworthy.

    `verify_command` is an arg LIST (or a shlex-split string) run with no shell. `verify_cwd` is
    relative to the worktree root; `verify_env` is merged over the environment (e.g. JAVA_HOME).

    Status values: "verified" (gate ran green — accept), "verify_failed" (gate ran and the code
    failed — read the excerpt/diff), "unverified" (no `verify_command` — amber), "gate_error" (the
    gate could NOT execute — a command/env problem, NOT a code verdict; fix it and re-delegate,
    do not trust the agent's self-report), "error" (the delegation itself threw). A non-executable
    script entry point (e.g. a fresh-worktree mode-644 `./gradlew`) is auto-run via its interpreter
    rather than failing the gate.

    CONCURRENCY: this tool is async and offloads its work, so up to `DEEPSEEK_MAX_CONCURRENCY`
    (default 3) delegations run in parallel; further calls queue. Each gets an isolated
    worktree/branch (`ds/<hex>`), so parallel runs never collide. Integration stays caller-side:
    accept and `deepseek_cleanup` the returned branches one at a time (naturally serialized) — the
    server never auto-merges, so there is no cross-task merge race to resolve.
    """
    return await _dispatch_task(task, base_ref, verify_command, verify_cwd, verify_env,
                                verify_timeout, context_files, max_rounds)


@mcp.tool()
def deepseek_cleanup(worktree_path: str) -> dict:
    """Remove a worktree created by deepseek_task once you're done reviewing it."""
    return worktree.cleanup(worktree_path)


if __name__ == "__main__":
    mcp.run()  # stdio transport — how Claude Code launches it
