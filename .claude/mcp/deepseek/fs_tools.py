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
