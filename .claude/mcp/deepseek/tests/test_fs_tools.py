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


def test_null_byte_path_is_error_not_exception(tmp_path):
    fs = _tools(tmp_path)
    assert fs.read_file("foo\x00bar")["ok"] is False


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
