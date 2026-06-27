# DeepSeek Agent MCP — worktree-confined, file-only autonomous editor (design)

**Date:** 2026-06-27
**Type:** Dev tooling (MCP server) — **not** a Hexerei mod feature. The WARRANTLY Constitution gate
does not apply (it governs mod mechanics, not tooling).
**Goal:** Let the main Claude Code session (on Claude) delegate multi-file editing tasks to an autonomous
DeepSeek agent, kept inside a throwaway git worktree with file tools only (no shell). Claude reviews the
diff, runs the build/tests, and merges or iterates.

---

## Why / shape

Claude Code's `ANTHROPIC_BASE_URL` is process-global — you cannot route only subagents to a different
provider. So instead of routing, DeepSeek is exposed as an **MCP tool** the main Claude calls. The agent is
**agentic** (an iterative tool-calling loop over the repo), but deliberately **bash-free**: it can read and
write files within a worktree, nothing else. Verification (gradle/tests) and merge stay on Claude's side.

## Modules (5, one responsibility each)

### 1. `deepseek_client.py` — DeepSeek HTTP client
- Calls DeepSeek's **OpenAI-compatible** chat-completions endpoint (`https://api.deepseek.com/v1/chat/completions`)
  directly over HTTP — independent of Claude Code's base URL.
- Auth: `Authorization: Bearer ${DEEPSEEK_API_KEY}` (env). Model: `${DEEPSEEK_MODEL}` (env; the user set
  `deepseek-v4-pro` — **[UNVERIFIED]**: not confirmed against DeepSeek's catalog; if absent, swap to a real
  id like `deepseek-chat`/`deepseek-reasoner` — surfaces at smoke test).
- One function: `chat(messages, tools) -> {content, tool_calls}`. Uses OpenAI-style `tools` (function
  schemas) and parses `tool_calls` from the response. Thin; no agent logic here.

### 2. `fs_tools.py` — file tools, confined to a root (pure logic, unit-tested)
- Tools exposed to DeepSeek: `read_file`, `write_file`, `edit_file` (single-occurrence str_replace),
  `list_dir`, `glob`, `grep`. **No bash, no process spawning, no network.**
- **Path confinement is the security core.** Every tool resolves the model-supplied path against the
  worktree root (`Path.resolve()`), and rejects anything that escapes it: `..` traversal, absolute paths
  outside the root, and symlink escapes (resolve, then verify `is_relative_to(root)`). Mirrors the
  text-editor tool's security model.
- Read cap: refuse to return more than N bytes/lines per `read_file` (configurable) so a giant file can't
  blow the context.
- Returns structured results (`{ok, content|error}`) — the loop feeds these back as tool results.

### 3. `agent_loop.py` — the tool-calling loop (pure logic, fake-client unit-tested)
- Builds a system prompt ("you edit files in this repo via the given tools; when done, call `finish`") plus
  the task, then loops: `chat(messages, tools)` → if `tool_calls`, dispatch each through `fs_tools`, append
  results, repeat; stop when the model calls the `finish` tool or `max_iterations` is hit.
- The DeepSeek client is **injected** (interface), so the loop is tested with a fake client that scripts a
  `tool_call → finish` sequence — no network.
- Returns `{status: completed|max_iterations|error, iterations, transcript_summary}`.

### 4. `worktree.py` — git worktree lifecycle (the only place the server runs git)
- `create(base_ref) -> path`: `git worktree add <tmp> -b ds/<n> <base_ref>` under a temp dir; returns path.
- `diff(path, base_ref) -> str`: `git -C <path> add -A && git -C <path> diff --cached <base_ref>` (staged
  tree vs the base commit) — **this is the server's own fixed command, never a tool DeepSeek can call.**
- `changed_files(path) -> list[str]`, `cleanup(path)`: `git worktree remove`.
- Worktrees are **left in place** after a task for Claude to review/merge; cleanup is explicit.

### 5. `server.py` — MCP stdio server (one task tool + one cleanup tool)
- `deepseek_task(task: str, base_ref: str = "HEAD", context_files: list[str] = []) ->`
  `{summary, changed_files, diff, worktree_path, iterations, status}`.
  Flow: `worktree.create(base_ref)` → seed the agent with `task` + optionally the contents of
  `context_files` → run `agent_loop` bound to `fs_tools(root=worktree)` → assemble `worktree.diff()` →
  return. Blocking; returns the final result (no streaming of intermediate steps).
- `deepseek_cleanup(worktree_path: str) -> {ok}` — removes a worktree once Claude is done with it.

## Data flow
```
Claude → deepseek_task(task, base_ref)
  worktree.create(base_ref) → /tmp/…/ds-wt-N   (branch ds/N off base_ref)
  agent_loop:  DeepSeek ↔ fs_tools[root=worktree]   × up to max_iterations
  worktree.diff() / changed_files()
← {summary, changed_files, diff, worktree_path, iterations, status}
Claude: read diff → run gradle/tests → merge ds/N  OR  deepseek_task(...) again with feedback  OR  deepseek_cleanup
```

## Security & secrets
- **Confinement:** DeepSeek's file tools are locked to the worktree root, path-checked every call. No bash →
  it cannot run processes, reach the network, or read `~/.ssh`. The only process risk is code it *writes*
  that Claude later builds — mitigated because **Claude always reviews the diff and controls build/merge**.
- **Secret handling:** `DEEPSEEK_API_KEY` is read from the environment only. It is **never** written to a
  tracked file or commit. Recommended store: gitignored `.claude/mcp/deepseek/.env` (the repo's `.gitignore`
  already ignores `.env`), or a shell export. `.mcp.json` references `${DEEPSEEK_API_KEY}` — the value is not
  inlined (`.mcp.json` is untracked but not gitignored, so inlining would risk a leak on a stray `git add`).
- **Key already exposed:** the working key was pasted into the session transcript in plaintext. Recommend
  rotating it in the DeepSeek console after setup and using the rotated key as the real credential.

## Configuration
- `.mcp.json` adds a `deepseek` stdio server: `command: python3`, `args: [".claude/mcp/deepseek/server.py"]`,
  `env: { DEEPSEEK_MODEL: "deepseek-v4-pro", DEEPSEEK_API_KEY: "${DEEPSEEK_API_KEY}", DEEPSEEK_MAX_ITERATIONS:
  "40", DEEPSEEK_BASE_URL: "https://api.deepseek.com" }`.
- Tunables (env, with defaults): max iterations (40), per-request timeout, read cap.

## Testing (two-tier, matching the repo)
- **Unit (pytest, no network):**
  - `fs_tools` path-confinement: `..`, absolute-outside-root, and symlink-escape paths are **rejected**;
    `read/write/edit/glob/grep/list_dir` behave correctly inside the root; read cap enforced.
  - `agent_loop` with a fake DeepSeek client: a scripted `tool_call` is dispatched to `fs_tools`, its result
    is fed back, and a `finish` call terminates with `status: completed`; `max_iterations` path terminates
    with `status: max_iterations`.
- **Smoke (real key, trivial task):** `deepseek_task("append a line to README.md")` against a scratch
  worktree → confirm the diff shows the change, `status: completed`. This is also where `deepseek-v4-pro` is
  validated against DeepSeek's API.

## Out of scope (YAGNI)
- No bash / process execution (user's choice).
- No machine sandbox (worktree, not Docker — user's choice).
- No streaming of intermediate agent steps to Claude (blocking tool, final result only).
- No auto-merge, no auto-cleanup.
- No conversation persistence across `deepseek_task` calls (each call is a fresh agent in a fresh worktree;
  iterate by calling again with feedback + the prior `base_ref`/branch).

## Files
```
.claude/mcp/deepseek/
  server.py            # MCP stdio: deepseek_task, deepseek_cleanup
  deepseek_client.py   # DeepSeek HTTP (OpenAI-compatible) client
  fs_tools.py          # root-confined file tools (pure)
  agent_loop.py        # tool-calling loop (pure, client injected)
  worktree.py          # git worktree create/diff/cleanup
  tests/
    test_fs_tools.py
    test_agent_loop.py
.mcp.json              # + deepseek server entry (env-referenced key)
.claude/mcp/deepseek/.env   # gitignored; holds DEEPSEEK_API_KEY (not committed)
```
