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
        _git("-C", path, "worktree", "remove", "--force", path)
        return {"ok": True}
    except subprocess.CalledProcessError as e:
        return {"ok": False, "error": e.stderr}
