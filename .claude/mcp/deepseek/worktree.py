"""Git worktree lifecycle for the DeepSeek agent. The only place the server runs git."""
import os
import subprocess
import tempfile
import uuid


def _git(*args) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def create(base_ref: str, repo_root: str, *, prefix: str = "ds") -> dict:
    import shutil
    branch = f"{prefix}/{uuid.uuid4().hex[:8]}"
    path = tempfile.mkdtemp(prefix="ds-wt-")
    try:
        _git("-C", repo_root, "worktree", "add", path, "-b", branch, base_ref)
    except subprocess.CalledProcessError:
        shutil.rmtree(path, ignore_errors=True)
        raise
    return {"path": path, "branch": branch}


def diff(path: str, base_ref: str) -> str:
    _git("-C", path, "add", "-A")
    return _git("-C", path, "diff", "--cached", base_ref)


def changed_files(path: str, base_ref: str) -> list[str]:
    _git("-C", path, "add", "-A")
    out = _git("-C", path, "diff", "--cached", "--name-only", base_ref)
    return [line for line in out.splitlines() if line]


def commit(path: str, message: str) -> dict:
    _git("-C", path, "add", "-A")
    try:
        _git("-C", path, "commit", "-q", "-m", message)
        return {"ok": True}
    except subprocess.CalledProcessError as e:
        return {"ok": False, "error": e.stderr}


def cleanup(path: str) -> dict:
    branch = None
    owner_root = None
    try:
        branch = _git("-C", path, "rev-parse", "--abbrev-ref", "HEAD").strip()
        common = _git("-C", path, "rev-parse", "--path-format=absolute", "--git-common-dir").strip()
        owner_root = os.path.dirname(common)  # <owner>/.git -> <owner>
    except subprocess.CalledProcessError:
        pass
    try:
        _git("-C", path, "worktree", "remove", "--force", path)
    except subprocess.CalledProcessError as e:
        return {"ok": False, "error": e.stderr}
    if branch and owner_root:
        try:
            _git("-C", owner_root, "branch", "-D", branch)
        except subprocess.CalledProcessError:
            pass  # branch already gone / merged elsewhere — not fatal
    return {"ok": True, "branch_deleted": branch}
