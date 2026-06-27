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
