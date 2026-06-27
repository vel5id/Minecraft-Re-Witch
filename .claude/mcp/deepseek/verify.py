"""Server-run authoritative verification for a delegated task.

This is the gate the delegation economy depends on: the calling session trusts a green
verdict here instead of re-reading the implementation. It runs a LOCAL build/test command
(no data leaves the machine) with a hard timeout, and extracts a compact error excerpt so
the failure can be fed back to the agent (or read cheaply by the caller) without shipping the
whole build log.
"""
import os
import shlex
import subprocess

_ERR_KEYS = (
    "error:", "ERROR", "FAILED", "Exception", "Caused by", "BUILD FAILED",
    "cannot find symbol", "AssertionError", "Expected", "but was",
)


def _excerpt(output: str, *, max_err_lines: int = 40, max_chars: int = 4000) -> str:
    """Pull the diagnostic lines (errors/failures) plus a fixed tail — the real cause lives mid-log."""
    lines = output.splitlines()
    errs = [l for l in lines if any(k in l for k in _ERR_KEYS)][:max_err_lines]
    tail = lines[-20:]
    text = "\n".join(errs + ["--- tail ---"] + tail) if errs else "\n".join(tail)
    return text[-max_chars:]


def run(command, cwd: str, env: dict | None = None, timeout: int = 300) -> dict:
    """Run `command` (list of args, or a string that is shlex-split) in `cwd`.

    No shell (`shell=False`) — the command is an arg list, never an interpolated string, so a
    task-derived value cannot inject `;`/`|`/`$(...)`. `env` is MERGED over the current
    environment (e.g. JAVA_HOME for Gradle builds). Returns:
        {ran: bool, passed: bool, timed_out: bool, excerpt: str}
    """
    if isinstance(command, str):
        command = shlex.split(command)
    full_env = dict(os.environ)
    if env:
        full_env.update(env)
    try:
        p = subprocess.run(command, cwd=cwd, env=full_env, capture_output=True, text=True,
                           timeout=timeout, start_new_session=True)
    except subprocess.TimeoutExpired as e:
        out = (e.stdout or "") + "\n" + (e.stderr or "")
        return {"ran": True, "passed": False, "timed_out": True,
                "excerpt": _excerpt(out + "\n[VERIFY TIMED OUT]")}
    except (OSError, ValueError) as e:
        return {"ran": False, "passed": False, "timed_out": False,
                "excerpt": f"could not run verify command: {e}"}
    out = p.stdout + "\n" + p.stderr
    return {"ran": True, "passed": p.returncode == 0, "timed_out": False, "excerpt": _excerpt(out)}
