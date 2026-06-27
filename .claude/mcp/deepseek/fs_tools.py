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
            with p.open("rb") as fh:
                raw = fh.read(max_bytes + 1)
        except PathEscape as e:
            return {"ok": False, "error": f"path escapes root: {e}"}
        except (OSError, ValueError) as e:
            return {"ok": False, "error": str(e)}
        truncated = len(raw) > max_bytes
        return {"ok": True, "content": raw[:max_bytes].decode("utf-8", errors="replace"), "truncated": truncated}

    def write_file(self, path: str, content: str) -> dict:
        try:
            p = self._resolve(path)
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content, encoding="utf-8")
        except PathEscape as e:
            return {"ok": False, "error": f"path escapes root: {e}"}
        except (OSError, ValueError) as e:
            return {"ok": False, "error": str(e)}
        return {"ok": True, "path": path}
