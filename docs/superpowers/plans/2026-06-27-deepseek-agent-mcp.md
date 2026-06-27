# DeepSeek Agent MCP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an MCP stdio server exposing an autonomous DeepSeek file-editing agent confined to a throwaway git worktree (file tools only, no bash); the main Claude session delegates a task, reviews the returned diff, runs build/tests, and merges or iterates.

**Architecture:** Five focused Python modules under `.claude/mcp/deepseek/` — a thin DeepSeek HTTP client (OpenAI-compatible), root-confined file tools, an injected-client tool-calling loop, a git-worktree lifecycle helper, and a `FastMCP` server wiring two tools (`deepseek_task`, `deepseek_cleanup`). Pure logic (file tools, loop, payload-building) is unit-tested with no network; the worktree helper is tested against a real temp git repo; the live path is smoke-tested with the real key.

**Tech Stack:** Python 3, `mcp[cli]` (`FastMCP`), `httpx` (DeepSeek HTTP), `pytest` (tests), git CLI via `subprocess`.

## Global Constraints

- Server framework is **`FastMCP`** (`from mcp.server.fastmcp import FastMCP`; tools via `@mcp.tool()`; launched with `mcp.run()`), matching `.claude/mcp/image_mcp.py`.
- DeepSeek is called over its **OpenAI-compatible** API: `POST {DEEPSEEK_BASE_URL}/v1/chat/completions`, `Authorization: Bearer {DEEPSEEK_API_KEY}`, default base `https://api.deepseek.com`. Model from `DEEPSEEK_MODEL` (user set `deepseek-v4-pro` — **[UNVERIFIED]**; validated at smoke test).
- The DeepSeek agent gets **file tools only — NO bash, NO process spawn, NO network tool.**
- **Path confinement is mandatory**: every file-tool path resolves against the worktree root and rejects `..` traversal, absolute-outside-root, and symlink escapes.
- **Secrets never touch tracked files or commits.** `DEEPSEEK_API_KEY` comes only from the environment; `.mcp.json` references `${DEEPSEEK_API_KEY}` (no inline value); the key lives in gitignored `.claude/mcp/deepseek/.env`.
- All work happens in the main repo at `/home/h621l/minecraft` (tooling lives at repo root, not in the Grimoire worktree).
- Tests run with: `python3 -m pytest .claude/mcp/deepseek -v` (a `conftest.py` in the package dir puts it on `sys.path`).
- Commit convention: `type(tooling): summary` (e.g. `feat(tooling): …`). End commit bodies with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: Scaffold + root-confined `read_file`/`write_file`

**Files:**
- Create: `.claude/mcp/deepseek/conftest.py`
- Create: `.claude/mcp/deepseek/fs_tools.py`
- Test: `.claude/mcp/deepseek/tests/test_fs_tools.py`

**Interfaces:**
- Produces:
  - `class PathEscape(Exception)`
  - `class FsTools` with `__init__(self, root)`, `_resolve(self, rel: str) -> pathlib.Path` (raises `PathEscape`), `read_file(self, path: str, max_bytes: int = 65536) -> dict`, `write_file(self, path: str, content: str) -> dict`. Result dicts are `{"ok": True, ...}` or `{"ok": False, "error": str}`.

- [ ] **Step 1: Write `conftest.py`** (so tests can `import fs_tools`)

```python
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
```

- [ ] **Step 2: Write the failing test**

```python
# .claude/mcp/deepseek/tests/test_fs_tools.py
import os
import pathlib

import pytest

from fs_tools import FsTools, PathEscape


def _tools(tmp_path):
    return FsTools(tmp_path)


def test_write_then_read_roundtrip(tmp_path):
    fs = _tools(tmp_path)
    assert fs.write_file("a/b.txt", "hello")["ok"] is True
    r = fs.read_file("a/b.txt")
    assert r["ok"] is True
    assert r["content"] == "hello"


def test_read_missing_file_is_error_not_exception(tmp_path):
    r = _tools(tmp_path).read_file("nope.txt")
    assert r["ok"] is False
    assert "error" in r


def test_parent_traversal_rejected(tmp_path):
    with pytest.raises(PathEscape):
        _tools(tmp_path)._resolve("../escape.txt")


def test_absolute_outside_root_rejected(tmp_path):
    with pytest.raises(PathEscape):
        _tools(tmp_path)._resolve("/etc/passwd")


def test_symlink_escape_rejected(tmp_path):
    outside = tmp_path.parent / "outside.txt"
    outside.write_text("secret")
    link = tmp_path / "link.txt"
    os.symlink(outside, link)
    with pytest.raises(PathEscape):
        _tools(tmp_path)._resolve("link.txt")


def test_read_cap_truncates(tmp_path):
    fs = _tools(tmp_path)
    fs.write_file("big.txt", "x" * 100)
    r = fs.read_file("big.txt", max_bytes=10)
    assert r["ok"] is True
    assert len(r["content"]) == 10
    assert r["truncated"] is True
```

- [ ] **Step 3: Run test to verify it fails**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_fs_tools.py -v`
Expected: FAIL — `fs_tools`/`FsTools` not importable.

- [ ] **Step 4: Implement `fs_tools.py` (this task's slice)**

```python
"""Root-confined file tools for the DeepSeek agent. No bash, no network."""
import pathlib


class PathEscape(Exception):
    """Raised when a model-supplied path resolves outside the confinement root."""


class FsTools:
    def __init__(self, root):
        self.root = pathlib.Path(root).resolve()

    def _resolve(self, rel: str) -> pathlib.Path:
        target = (self.root / rel).resolve()
        if target != self.root and self.root not in target.parents:
            raise PathEscape(rel)
        return target

    def read_file(self, path: str, max_bytes: int = 65536) -> dict:
        try:
            p = self._resolve(path)
            data = p.read_text(encoding="utf-8", errors="replace")
        except PathEscape as e:
            return {"ok": False, "error": f"path escapes root: {e}"}
        except (OSError, UnicodeError) as e:
            return {"ok": False, "error": str(e)}
        truncated = len(data) > max_bytes
        return {"ok": True, "content": data[:max_bytes], "truncated": truncated}

    def write_file(self, path: str, content: str) -> dict:
        try:
            p = self._resolve(path)
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content, encoding="utf-8")
        except PathEscape as e:
            return {"ok": False, "error": f"path escapes root: {e}"}
        except OSError as e:
            return {"ok": False, "error": str(e)}
        return {"ok": True, "path": path}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_fs_tools.py -v`
Expected: PASS (6 tests). If `pytest` is missing, install it first — **ask the user before installing** (`pip install pytest`), per the repo's no-autonomous-install rule.

- [ ] **Step 6: Commit**

```bash
git add .claude/mcp/deepseek/conftest.py .claude/mcp/deepseek/fs_tools.py .claude/mcp/deepseek/tests/test_fs_tools.py
git commit -m "feat(tooling): deepseek MCP — root-confined read/write file tools"
```

---

### Task 2: `fs_tools` — `edit_file`/`list_dir`/`glob`/`grep` + schemas + dispatch

**Files:**
- Modify: `.claude/mcp/deepseek/fs_tools.py`
- Test: `.claude/mcp/deepseek/tests/test_fs_tools.py` (append)

**Interfaces:**
- Consumes: Task 1 `FsTools`, `PathEscape`.
- Produces (added to `FsTools`):
  - `edit_file(self, path, old, new) -> dict` (replaces exactly one occurrence; error if 0 or >1)
  - `list_dir(self, path=".") -> dict` → `{"ok": True, "entries": [{"name", "type"}]}`
  - `glob(self, pattern) -> dict` → `{"ok": True, "matches": [relposix, ...]}`
  - `grep(self, pattern, path=".", max_hits=200) -> dict` → `{"ok": True, "hits": [{"file","line","text"}]}`
  - `dispatch(self, name: str, args: dict) -> dict` (routes a tool name to its method; unknown name → error)
  - `@staticmethod tool_schemas() -> list[dict]` — OpenAI-style function tool defs for the six file tools (NOT `finish`).

- [ ] **Step 1: Write the failing tests (append)**

```python
import re


def test_edit_single_occurrence(tmp_path):
    fs = _tools(tmp_path)
    fs.write_file("f.txt", "a b a")
    assert fs.edit_file("f.txt", "b", "B")["ok"] is True
    assert fs.read_file("f.txt")["content"] == "a B a"


def test_edit_rejects_ambiguous(tmp_path):
    fs = _tools(tmp_path)
    fs.write_file("f.txt", "a a")
    r = fs.edit_file("f.txt", "a", "X")
    assert r["ok"] is False  # 2 occurrences


def test_list_and_glob(tmp_path):
    fs = _tools(tmp_path)
    fs.write_file("src/one.java", "x")
    fs.write_file("src/two.java", "y")
    names = {e["name"] for e in fs.list_dir("src")["entries"]}
    assert names == {"one.java", "two.java"}
    assert sorted(fs.glob("src/*.java")["matches"]) == ["src/one.java", "src/two.java"]


def test_grep(tmp_path):
    fs = _tools(tmp_path)
    fs.write_file("a.txt", "alpha\nbeta\n")
    hits = fs.grep("be", ".")["hits"]
    assert any(h["text"].strip() == "beta" for h in hits)


def test_dispatch_unknown_tool(tmp_path):
    r = _tools(tmp_path).dispatch("rm_rf", {})
    assert r["ok"] is False


def test_dispatch_routes_write(tmp_path):
    fs = _tools(tmp_path)
    assert fs.dispatch("write_file", {"path": "x.txt", "content": "hi"})["ok"] is True
    assert fs.read_file("x.txt")["content"] == "hi"


def test_tool_schemas_shape():
    names = {s["function"]["name"] for s in FsTools.tool_schemas()}
    assert names == {"read_file", "write_file", "edit_file", "list_dir", "glob", "grep"}
```

- [ ] **Step 2: Run to verify it fails**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_fs_tools.py -v`
Expected: FAIL — `edit_file`/`dispatch`/`tool_schemas` undefined.

- [ ] **Step 3: Add the methods to `FsTools`**

```python
    def edit_file(self, path: str, old: str, new: str) -> dict:
        r = self.read_file(path)
        if not r["ok"]:
            return r
        text = r["content"]
        count = text.count(old)
        if count != 1:
            return {"ok": False, "error": f"expected exactly 1 match of old, found {count}"}
        return self.write_file(path, text.replace(old, new, 1))

    def list_dir(self, path: str = ".") -> dict:
        try:
            p = self._resolve(path)
            entries = [
                {"name": c.name, "type": "dir" if c.is_dir() else "file"}
                for c in sorted(p.iterdir())
            ]
        except PathEscape as e:
            return {"ok": False, "error": f"path escapes root: {e}"}
        except OSError as e:
            return {"ok": False, "error": str(e)}
        return {"ok": True, "entries": entries}

    def glob(self, pattern: str) -> dict:
        matches = []
        for c in self.root.glob(pattern):
            try:
                rel = c.resolve().relative_to(self.root)
            except ValueError:
                continue  # globbed outside root via a symlink — skip
            matches.append(rel.as_posix())
        return {"ok": True, "matches": matches}

    def grep(self, pattern: str, path: str = ".", max_hits: int = 200) -> dict:
        import re
        try:
            base = self._resolve(path)
            rx = re.compile(pattern)
        except PathEscape as e:
            return {"ok": False, "error": f"path escapes root: {e}"}
        except re.error as e:
            return {"ok": False, "error": f"bad regex: {e}"}
        files = [base] if base.is_file() else [f for f in base.rglob("*") if f.is_file()]
        hits = []
        for f in files:
            try:
                for i, line in enumerate(f.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                    if rx.search(line):
                        hits.append({"file": f.resolve().relative_to(self.root).as_posix(),
                                     "line": i, "text": line})
                        if len(hits) >= max_hits:
                            return {"ok": True, "hits": hits, "truncated": True}
            except (OSError, ValueError):
                continue
        return {"ok": True, "hits": hits}

    def dispatch(self, name: str, args: dict) -> dict:
        fn = {
            "read_file": self.read_file, "write_file": self.write_file,
            "edit_file": self.edit_file, "list_dir": self.list_dir,
            "glob": self.glob, "grep": self.grep,
        }.get(name)
        if fn is None:
            return {"ok": False, "error": f"unknown tool: {name}"}
        try:
            return fn(**args)
        except TypeError as e:
            return {"ok": False, "error": f"bad arguments for {name}: {e}"}

    @staticmethod
    def tool_schemas() -> list:
        def fn(name, desc, props, required):
            return {"type": "function", "function": {
                "name": name, "description": desc,
                "parameters": {"type": "object", "properties": props, "required": required},
            }}
        s = {"type": "string"}
        return [
            fn("read_file", "Read a UTF-8 text file (path relative to the repo root).",
               {"path": s}, ["path"]),
            fn("write_file", "Create or overwrite a text file with full content.",
               {"path": s, "content": s}, ["path", "content"]),
            fn("edit_file", "Replace exactly one occurrence of old with new in a file.",
               {"path": s, "old": s, "new": s}, ["path", "old", "new"]),
            fn("list_dir", "List the entries of a directory.", {"path": s}, []),
            fn("glob", "Find files by glob pattern (e.g. 'src/**/*.java').", {"pattern": s}, ["pattern"]),
            fn("grep", "Search files for a regex; returns file/line/text hits.",
               {"pattern": s, "path": s}, ["pattern"]),
        ]
```

- [ ] **Step 4: Run to verify it passes**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_fs_tools.py -v`
Expected: PASS (13 tests total).

- [ ] **Step 5: Commit**

```bash
git add .claude/mcp/deepseek/fs_tools.py .claude/mcp/deepseek/tests/test_fs_tools.py
git commit -m "feat(tooling): deepseek MCP — edit/list/glob/grep + schemas + dispatch"
```

---

### Task 3: `agent_loop.py` — injected-client tool-calling loop

**Files:**
- Create: `.claude/mcp/deepseek/agent_loop.py`
- Test: `.claude/mcp/deepseek/tests/test_agent_loop.py`

**Interfaces:**
- Consumes: Task 1–2 `FsTools` (uses `.tool_schemas()` and `.dispatch()`).
- Produces:
  - `FINISH_TOOL` (an OpenAI function schema dict named `finish`, arg `summary`).
  - `run_agent(task: str, fs, chat, *, max_iterations: int = 40, context: str = "") -> dict`
    - `chat` is `Callable[[list[dict], list[dict]], dict]` returning `{"content": str|None, "tool_calls": list}` where each tool_call is `{"id": str, "name": str, "arguments": dict}`.
    - Returns `{"status": "completed"|"max_iterations", "iterations": int, "summary": str}`.

- [ ] **Step 1: Write the failing test**

```python
# .claude/mcp/deepseek/tests/test_agent_loop.py
from agent_loop import run_agent
from fs_tools import FsTools


class FakeChat:
    """Scripts a fixed sequence of normalized responses."""
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = 0

    def __call__(self, messages, tools):
        self.calls += 1
        return self.responses.pop(0)


def test_loop_dispatches_then_finishes(tmp_path):
    fs = FsTools(tmp_path)
    chat = FakeChat([
        {"content": None, "tool_calls": [
            {"id": "1", "name": "write_file", "arguments": {"path": "out.txt", "content": "done"}}]},
        {"content": None, "tool_calls": [
            {"id": "2", "name": "finish", "arguments": {"summary": "wrote out.txt"}}]},
    ])
    result = run_agent("make out.txt", fs, chat)
    assert result["status"] == "completed"
    assert result["summary"] == "wrote out.txt"
    assert (tmp_path / "out.txt").read_text() == "done"


def test_loop_hits_max_iterations(tmp_path):
    fs = FsTools(tmp_path)
    forever = FakeChat([{"content": None, "tool_calls": [
        {"id": "x", "name": "list_dir", "arguments": {}}]}] * 5)
    result = run_agent("loop", fs, forever, max_iterations=3)
    assert result["status"] == "max_iterations"
    assert result["iterations"] == 3
```

- [ ] **Step 2: Run to verify it fails**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_agent_loop.py -v`
Expected: FAIL — `agent_loop` not importable.

- [ ] **Step 3: Implement `agent_loop.py`**

```python
"""The DeepSeek agent's tool-calling loop. The chat client is injected (testable)."""
import json

FINISH_TOOL = {"type": "function", "function": {
    "name": "finish",
    "description": "Call when the task is complete. Provide a short summary of what changed.",
    "parameters": {"type": "object", "properties": {"summary": {"type": "string"}},
                   "required": ["summary"]},
}}

SYSTEM = (
    "You are a coding agent editing a repository through the provided file tools. "
    "You have NO shell and cannot run commands or tests — only read, write, edit, list, glob, grep. "
    "Make the requested change across as many files as needed, then call `finish` with a summary. "
    "All paths are relative to the repository root."
)


def _to_openai_tool_call(tc: dict) -> dict:
    return {"id": tc["id"], "type": "function",
            "function": {"name": tc["name"], "arguments": json.dumps(tc.get("arguments", {}))}}


def run_agent(task: str, fs, chat, *, max_iterations: int = 40, context: str = "") -> dict:
    tools = fs.tool_schemas() + [FINISH_TOOL]
    user = task if not context else f"{task}\n\n--- Context files ---\n{context}"
    messages = [{"role": "system", "content": SYSTEM}, {"role": "user", "content": user}]

    for i in range(1, max_iterations + 1):
        resp = chat(messages, tools)
        tcs = resp.get("tool_calls") or []
        if not tcs:
            messages.append({"role": "assistant", "content": resp.get("content") or ""})
            messages.append({"role": "user", "content": "Use a file tool, or call finish when done."})
            continue
        messages.append({"role": "assistant", "content": resp.get("content"),
                         "tool_calls": [_to_openai_tool_call(tc) for tc in tcs]})
        for tc in tcs:
            if tc["name"] == "finish":
                return {"status": "completed", "iterations": i,
                        "summary": tc.get("arguments", {}).get("summary", "")}
            result = fs.dispatch(tc["name"], tc.get("arguments", {}))
            messages.append({"role": "tool", "tool_call_id": tc["id"],
                             "content": json.dumps(result)})
    return {"status": "max_iterations", "iterations": max_iterations, "summary": ""}
```

- [ ] **Step 4: Run to verify it passes**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_agent_loop.py -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add .claude/mcp/deepseek/agent_loop.py .claude/mcp/deepseek/tests/test_agent_loop.py
git commit -m "feat(tooling): deepseek MCP — tool-calling agent loop (injected client)"
```

---

### Task 4: `deepseek_client.py` — OpenAI-compatible HTTP client

**Files:**
- Create: `.claude/mcp/deepseek/deepseek_client.py`
- Test: `.claude/mcp/deepseek/tests/test_deepseek_client.py`

**Interfaces:**
- Produces:
  - `build_payload(model: str, messages: list, tools: list) -> dict` (pure).
  - `chat(messages, tools, *, api_key, model, base_url="https://api.deepseek.com", timeout=120) -> dict` — POSTs `{base_url}/v1/chat/completions`, normalizes the first choice into `{"content": str|None, "tool_calls": [{"id","name","arguments(dict)"}]}` (parses OpenAI `function.arguments` JSON string into a dict).

- [ ] **Step 1: Write the failing test** (mocks `httpx.post`; no network)

```python
# .claude/mcp/deepseek/tests/test_deepseek_client.py
import deepseek_client


def test_build_payload_includes_model_and_tools():
    p = deepseek_client.build_payload("deepseek-x", [{"role": "user", "content": "hi"}], [{"type": "function"}])
    assert p["model"] == "deepseek-x"
    assert p["tools"] == [{"type": "function"}]
    assert p["messages"][0]["content"] == "hi"


def test_chat_normalizes_tool_calls(monkeypatch):
    class FakeResp:
        def raise_for_status(self): pass
        def json(self):
            return {"choices": [{"message": {
                "content": None,
                "tool_calls": [{"id": "abc", "type": "function",
                                "function": {"name": "write_file",
                                             "arguments": '{"path": "x.txt", "content": "y"}'}}],
            }}]}

    def fake_post(url, headers=None, json=None, timeout=None):
        assert url.endswith("/v1/chat/completions")
        assert headers["Authorization"] == "Bearer KEY"
        return FakeResp()

    monkeypatch.setattr(deepseek_client.httpx, "post", fake_post)
    out = deepseek_client.chat([{"role": "user", "content": "go"}], [], api_key="KEY", model="m")
    assert out["content"] is None
    assert out["tool_calls"] == [{"id": "abc", "name": "write_file",
                                  "arguments": {"path": "x.txt", "content": "y"}}]
```

- [ ] **Step 2: Run to verify it fails**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_deepseek_client.py -v`
Expected: FAIL — `deepseek_client` not importable.

- [ ] **Step 3: Implement `deepseek_client.py`**

```python
"""Thin DeepSeek client over the OpenAI-compatible chat-completions API."""
import json

import httpx


def build_payload(model: str, messages: list, tools: list) -> dict:
    payload = {"model": model, "messages": messages}
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = "auto"
    return payload


def chat(messages, tools, *, api_key, model,
         base_url: str = "https://api.deepseek.com", timeout: int = 120) -> dict:
    resp = httpx.post(
        f"{base_url}/v1/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json=build_payload(model, messages, tools),
        timeout=timeout,
    )
    resp.raise_for_status()
    msg = resp.json()["choices"][0]["message"]
    calls = []
    for tc in msg.get("tool_calls") or []:
        fn = tc["function"]
        try:
            args = json.loads(fn.get("arguments") or "{}")
        except json.JSONDecodeError:
            args = {}
        calls.append({"id": tc.get("id", ""), "name": fn["name"], "arguments": args})
    return {"content": msg.get("content"), "tool_calls": calls}
```

- [ ] **Step 4: Run to verify it passes**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_deepseek_client.py -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add .claude/mcp/deepseek/deepseek_client.py .claude/mcp/deepseek/tests/test_deepseek_client.py
git commit -m "feat(tooling): deepseek MCP — OpenAI-compatible HTTP client"
```

---

### Task 5: `worktree.py` — git worktree lifecycle

**Files:**
- Create: `.claude/mcp/deepseek/worktree.py`
- Test: `.claude/mcp/deepseek/tests/test_worktree.py`

**Interfaces:**
- Produces:
  - `create(base_ref: str, repo_root: str, *, prefix: str = "ds") -> dict` → `{"path": str, "branch": str}` (runs `git worktree add <tmp> -b <branch> <base_ref>`; branch = `<prefix>/<uuid8>`; worktree dir under the system temp dir).
  - `diff(path: str, base_ref: str) -> str` → `git -C <path> add -A` then `git -C <path> diff --cached <base_ref>`.
  - `changed_files(path: str, base_ref: str) -> list[str]` → `git -C <path> diff --cached --name-only <base_ref>` (after `add -A`).
  - `cleanup(path: str) -> dict` → `git worktree remove --force <path>`; returns `{"ok": bool}`.

- [ ] **Step 1: Write the failing test** (creates a real temp git repo)

```python
# .claude/mcp/deepseek/tests/test_worktree.py
import subprocess

import worktree


def _init_repo(tmp_path):
    def g(*a):
        subprocess.run(["git", "-C", str(tmp_path), *a], check=True,
                       capture_output=True, text=True)
    g("init", "-q")
    g("config", "user.email", "t@t")
    g("config", "user.name", "t")
    (tmp_path / "README.md").write_text("hello\n")
    g("add", "-A")
    g("commit", "-qm", "init")
    return str(tmp_path)


def test_create_diff_changed_cleanup(tmp_path):
    root = _init_repo(tmp_path)
    wt = worktree.create("HEAD", root)
    assert wt["branch"].startswith("ds/")

    # edit inside the worktree
    import pathlib
    (pathlib.Path(wt["path"]) / "README.md").write_text("hello\nworld\n")

    d = worktree.diff(wt["path"], "HEAD")
    assert "+world" in d
    assert worktree.changed_files(wt["path"], "HEAD") == ["README.md"]

    assert worktree.cleanup(wt["path"])["ok"] is True
```

- [ ] **Step 2: Run to verify it fails**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_worktree.py -v`
Expected: FAIL — `worktree` not importable.

- [ ] **Step 3: Implement `worktree.py`**

```python
"""Git worktree lifecycle for the DeepSeek agent. The only place the server runs git."""
import subprocess
import tempfile
import uuid


def _git(*args) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def create(base_ref: str, repo_root: str, *, prefix: str = "ds") -> dict:
    branch = f"{prefix}/{uuid.uuid4().hex[:8]}"
    path = tempfile.mkdtemp(prefix="ds-wt-")
    _git("-C", repo_root, "worktree", "add", path, "-b", branch, base_ref)
    return {"path": path, "branch": branch}


def diff(path: str, base_ref: str) -> str:
    _git("-C", path, "add", "-A")
    return _git("-C", path, "diff", "--cached", base_ref)


def changed_files(path: str, base_ref: str) -> list:
    _git("-C", path, "add", "-A")
    out = _git("-C", path, "diff", "--cached", "--name-only", base_ref)
    return [line for line in out.splitlines() if line]


def cleanup(path: str) -> dict:
    try:
        _git("worktree", "remove", "--force", path)
        return {"ok": True}
    except subprocess.CalledProcessError as e:
        return {"ok": False, "error": e.stderr}
```

- [ ] **Step 4: Run to verify it passes**

Run: `python3 -m pytest .claude/mcp/deepseek/tests/test_worktree.py -v`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add .claude/mcp/deepseek/worktree.py .claude/mcp/deepseek/tests/test_worktree.py
git commit -m "feat(tooling): deepseek MCP — git worktree lifecycle helper"
```

---

### Task 6: `server.py` + config wiring (`.mcp.json`, `.env`, deps)

**Files:**
- Create: `.claude/mcp/deepseek/server.py`
- Create: `.claude/mcp/deepseek/.env.example`
- Modify: `.mcp.json` (add the `deepseek` server)

**Interfaces:**
- Consumes: Tasks 1–5 modules.
- Produces: a `FastMCP("deepseek")` server exposing `deepseek_task(task, base_ref="HEAD", context_files=[]) -> dict` and `deepseek_cleanup(worktree_path) -> dict`. No new tested logic (it composes tested modules), so this task is verified by **import + a config check**, with the live behavior covered by Task 7.

- [ ] **Step 1: Write `server.py`**

```python
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
import os
import subprocess

from mcp.server.fastmcp import FastMCP

import deepseek_client
import worktree
from agent_loop import run_agent
from fs_tools import FsTools

mcp = FastMCP("deepseek")

API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-chat")
BASE_URL = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
MAX_ITERS = int(os.environ.get("DEEPSEEK_MAX_ITERATIONS", "40"))


def _repo_root() -> str:
    return subprocess.run(["git", "rev-parse", "--show-toplevel"],
                          check=True, capture_output=True, text=True).stdout.strip()


def _chat(messages, tools):
    return deepseek_client.chat(messages, tools, api_key=API_KEY, model=MODEL, base_url=BASE_URL)


@mcp.tool()
def deepseek_task(task: str, base_ref: str = "HEAD", context_files: list = []) -> dict:
    """Run the DeepSeek agent on `task` in a fresh worktree off `base_ref`; return the diff."""
    if not API_KEY:
        return {"status": "error", "error": "DEEPSEEK_API_KEY is not set"}
    root = _repo_root()
    wt = worktree.create(base_ref, root)
    fs = FsTools(wt["path"])
    context = ""
    for rel in context_files:
        r = fs.read_file(rel)
        if r["ok"]:
            context += f"\n### {rel}\n{r['content']}\n"
    result = run_agent(task, fs, _chat, max_iterations=MAX_ITERS, context=context)
    return {
        "status": result["status"],
        "iterations": result["iterations"],
        "summary": result["summary"],
        "worktree_path": wt["path"],
        "branch": wt["branch"],
        "changed_files": worktree.changed_files(wt["path"], base_ref),
        "diff": worktree.diff(wt["path"], base_ref),
    }


@mcp.tool()
def deepseek_cleanup(worktree_path: str) -> dict:
    """Remove a worktree created by deepseek_task once you're done reviewing it."""
    return worktree.cleanup(worktree_path)


if __name__ == "__main__":
    mcp.run()  # stdio transport — how Claude Code launches it
```

- [ ] **Step 2: Write `.env.example`** (documents the secret; the real `.env` is gitignored and created by the user)

```bash
# Copy to .env (gitignored) and fill in. Never commit the real key.
DEEPSEEK_API_KEY=sk-your-key-here
DEEPSEEK_MODEL=deepseek-v4-pro
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MAX_ITERATIONS=40
```

- [ ] **Step 3: Add the server to `.mcp.json`** (reference the key by env var, do NOT inline it)

Add this entry under `mcpServers` (alongside `texture-gen` and `hexerei-sprite`):

```json
    "deepseek": {
      "command": "python3",
      "args": ["/home/h621l/minecraft/.claude/mcp/deepseek/server.py"],
      "env": {
        "DEEPSEEK_API_KEY": "${DEEPSEEK_API_KEY}",
        "DEEPSEEK_MODEL": "deepseek-v4-pro",
        "DEEPSEEK_BASE_URL": "https://api.deepseek.com",
        "DEEPSEEK_MAX_ITERATIONS": "40"
      }
    }
```

- [ ] **Step 4: Verify import + JSON validity (no live call)**

Run:
```bash
cd /home/h621l/minecraft/.claude/mcp/deepseek && python3 -c "import server; print('tools:', sorted(t.name for t in __import__('asyncio').get_event_loop().run_until_complete(server.mcp.list_tools())))"
python3 -c "import json; json.load(open('/home/h621l/minecraft/.mcp.json')); print('mcp.json OK')"
```
Expected: prints the two tool names (`deepseek_cleanup`, `deepseek_task`) and `mcp.json OK`. If the `list_tools` introspection form errors on this `mcp` version, fall back to `python3 -c "import server; print('import OK')"` — import success is the bar for this task.

- [ ] **Step 5: Confirm the key is not committed**

Run: `git status --porcelain .claude/mcp/deepseek/.env` → expect empty (no `.env` exists yet) or ignored. `grep -rn "sk-" .claude/mcp/deepseek/*.py` → expect no matches.

- [ ] **Step 6: Commit** (server + example only; never `.env`)

```bash
git add .claude/mcp/deepseek/server.py .claude/mcp/deepseek/.env.example
git commit -m "feat(tooling): deepseek MCP — FastMCP server + config (env-referenced key)"
```

> Note: `.mcp.json` is untracked in this repo and is intentionally left untracked (it carries local server config). Do not `git add` it unless the user asks.

---

### Task 7: Live smoke test (real key) — orchestrator-run

**Files:** none (verification only).

**Interfaces:** Consumes the full server (Tasks 1–6).

This task needs the real `DEEPSEEK_API_KEY` in the environment and is run by the orchestrator (or the user), not a sandboxed subagent.

- [ ] **Step 1: Export the key** (the user provides it; keep it out of files)

Run (user types this with the `!` prefix in their session, or exports in the shell): `export DEEPSEEK_API_KEY=<key>`

- [ ] **Step 2: Drive one trivial task directly** (bypasses MCP transport; exercises the real loop)

```bash
cd /home/h621l/minecraft/.claude/mcp/deepseek
DEEPSEEK_MODEL=deepseek-v4-pro python3 -c "
import server
out = server.deepseek_task('Append a line that says BUILT-BY-DEEPSEEK to README.md', base_ref='HEAD')
print('status:', out['status'], 'iters:', out['iterations'])
print('changed:', out['changed_files'])
print(out['diff'][:800])
print('worktree:', out['worktree_path'])
"
```
Expected: `status: completed`, `changed_files` includes `README.md`, the diff shows the appended line.
**This is where `deepseek-v4-pro` is validated.** If DeepSeek returns a model-not-found / 404, set `DEEPSEEK_MODEL=deepseek-chat` (or the correct id from api-docs.deepseek.com) and re-run; update `.env.example` and the `.mcp.json` entry to the working id.

- [ ] **Step 3: Clean up the smoke worktree**

```bash
python3 -c "import server; print(server.deepseek_cleanup('<worktree_path printed above>'))"
```

- [ ] **Step 4: Full unit suite green**

Run: `python3 -m pytest .claude/mcp/deepseek -v`
Expected: all tests pass (Tasks 1–5).

- [ ] **Step 5: (optional) Restart Claude Code** to load the new `deepseek` MCP server from `.mcp.json`, then confirm the `deepseek_task`/`deepseek_cleanup` tools are listed.

---

## Self-review (spec coverage)

- **5 modules, one responsibility each** → Tasks 1–6 (`fs_tools` split across 1–2). ✅
- **DeepSeek OpenAI-compatible client, Bearer auth, `/v1/chat/completions`** → Task 4. ✅
- **File tools only, no bash** → Tasks 1–2 (no bash tool defined; loop sends only file tools + finish, Task 3). ✅
- **Path confinement (`..`/absolute/symlink rejected)** → Task 1 tests (the security core). ✅
- **Agent loop, injected client, max-iterations** → Task 3. ✅
- **Worktree create/diff/changed/cleanup, server-only git** → Task 5; consumed in Task 6. ✅
- **One `deepseek_task` + `deepseek_cleanup`, blocking, returns summary+diff** → Task 6. ✅
- **Secret only from env, never committed; `${DEEPSEEK_API_KEY}` in `.mcp.json`; `.env` gitignored** → Task 6 (config) + Task 6 Step 5 check. ✅
- **`deepseek-v4-pro` [UNVERIFIED] validated at smoke** → Task 7. ✅
- **Two-tier testing (unit + smoke)** → Tasks 1–5 unit; Task 7 smoke. ✅
- **YAGNI: no bash, no Docker, no streaming, no auto-merge** → none of these are built. ✅
