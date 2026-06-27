"""Classify changed files as test vs source.

The delegation contract gives the caller a green verify gate to trust — but only if the
agent did not weaken the tests to make them pass. The caller never needs to read the whole
diff; it reads the diff of the TEST files only (cheap, bounded) to confirm the gate wasn't
gamed. This module surfaces that subset.
"""
import re

_TEST_PATTERNS = (
    re.compile(r"(^|/)src/test/"),
    re.compile(r"(^|/)tests?/"),
    re.compile(r"[^/]*Test[^/]*\.java$"),
    re.compile(r"(^|/)test_[^/]*\.py$"),
    re.compile(r"[^/]*_test\.py$"),
    re.compile(r"[^/]*\.(test|spec)\.[tj]sx?$"),
)


def is_test_file(path: str) -> bool:
    return any(p.search(path) for p in _TEST_PATTERNS)


def partition(paths):
    """Return (test_files, source_files), preserving order."""
    test = [p for p in paths if is_test_file(p)]
    src = [p for p in paths if not is_test_file(p)]
    return test, src
