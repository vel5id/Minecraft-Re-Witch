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


def test_commit_then_cleanup_deletes_branch(tmp_path):
    import pathlib
    root = _init_repo(tmp_path)
    wt = worktree.create("HEAD", root)
    (pathlib.Path(wt["path"]) / "README.md").write_text("hello\nworld\n")
    assert worktree.commit(wt["path"], "deepseek: test")["ok"] is True

    def rev(where):
        return subprocess.run(["git", "-C", where, "rev-parse", "HEAD"],
                              capture_output=True, text=True).stdout.strip()
    assert rev(wt["path"]) != rev(root)  # branch advanced past base

    assert worktree.cleanup(wt["path"])["ok"] is True
    listed = subprocess.run(["git", "-C", root, "branch", "--list", wt["branch"]],
                            capture_output=True, text=True).stdout
    assert wt["branch"] not in listed  # branch deleted


def test_diff_against_base_sha_survives_commit(tmp_path):
    """Regression for the empty-diff bug: diffing the pinned base_sha must stay correct after a commit."""
    import pathlib
    root = _init_repo(tmp_path)
    wt = worktree.create("HEAD", root)
    base = wt["base_sha"]
    assert base  # create resolved and returned the concrete SHA
    (pathlib.Path(wt["path"]) / "README.md").write_text("hello\nworld\n")
    assert worktree.commit(wt["path"], "ds: change")["ok"] is True

    # After the commit, the symbolic "HEAD" would diff empty; the pinned SHA still shows the change.
    assert worktree.diff(wt["path"], base).find("+world") != -1
    assert worktree.changed_files(wt["path"], base) == ["README.md"]
    assert "README.md" in worktree.diff_stat(wt["path"], base)
    assert worktree.diff(wt["path"], "HEAD") == ""  # confirms why base_sha is needed

    worktree.cleanup(wt["path"])
