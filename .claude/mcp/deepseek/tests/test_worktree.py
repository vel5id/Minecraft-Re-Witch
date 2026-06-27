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
