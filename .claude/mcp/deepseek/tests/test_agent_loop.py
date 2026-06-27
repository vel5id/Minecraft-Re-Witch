from agent_loop import run_agent
from fs_tools import FsTools


class FakeChat:
    """Scripts a fixed sequence of normalized responses."""
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = 0

    def __call__(self, messages, tools):
        self.calls += 1
        return self.responses.pop(0)


def test_loop_dispatches_then_finishes(tmp_path):
    fs = FsTools(tmp_path)
    chat = FakeChat([
        {"content": None, "tool_calls": [
            {"id": "1", "name": "write_file", "arguments": {"path": "out.txt", "content": "done"}}]},
        {"content": None, "tool_calls": [
            {"id": "2", "name": "finish", "arguments": {"summary": "wrote out.txt"}}]},
    ])
    result = run_agent("make out.txt", fs, chat)
    assert result["status"] == "completed"
    assert result["summary"] == "wrote out.txt"
    assert (tmp_path / "out.txt").read_text() == "done"


def test_loop_hits_max_iterations(tmp_path):
    fs = FsTools(tmp_path)
    forever = FakeChat([{"content": None, "tool_calls": [
        {"id": "x", "name": "list_dir", "arguments": {}}]}] * 5)
    result = run_agent("loop", fs, forever, max_iterations=3)
    assert result["status"] == "max_iterations"
    assert result["iterations"] == 3
