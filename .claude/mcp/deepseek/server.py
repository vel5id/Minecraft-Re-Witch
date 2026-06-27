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
def deepseek_task(task: str, base_ref: str = "HEAD", context_files: list | None = None) -> dict:
    """Run the DeepSeek agent on `task` in a fresh worktree off `base_ref`; return the diff."""
    if not API_KEY:
        return {"status": "error", "error": "DEEPSEEK_API_KEY is not set"}
    root = _repo_root()
    wt = worktree.create(base_ref, root)
    try:
        fs = FsTools(wt["path"])
        context = ""
        for rel in (context_files or []):
            r = fs.read_file(rel)
            if r["ok"]:
                context += f"\n### {rel}\n{r['content']}\n"
        result = run_agent(task, fs, _chat, max_iterations=MAX_ITERS, context=context)
        files = worktree.changed_files(wt["path"], base_ref)
        if files:
            worktree.commit(wt["path"], (f"deepseek: {result['summary'] or task}")[:200])
        return {
            "status": result["status"],
            "iterations": result["iterations"],
            "summary": result["summary"],
            "worktree_path": wt["path"],
            "branch": wt["branch"],
            "changed_files": files,
            "diff": worktree.diff(wt["path"], base_ref),
        }
    except Exception as e:
        return {"status": "error", "error": str(e),
                "worktree_path": wt["path"], "branch": wt["branch"], "iterations": 0}


@mcp.tool()
def deepseek_cleanup(worktree_path: str) -> dict:
    """Remove a worktree created by deepseek_task once you're done reviewing it."""
    return worktree.cleanup(worktree_path)


if __name__ == "__main__":
    mcp.run()  # stdio transport — how Claude Code launches it
