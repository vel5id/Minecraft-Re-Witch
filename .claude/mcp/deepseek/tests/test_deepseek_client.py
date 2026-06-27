import deepseek_client


def test_build_payload_includes_model_and_tools():
    p = deepseek_client.build_payload("deepseek-x", [{"role": "user", "content": "hi"}], [{"type": "function"}])
    assert p["model"] == "deepseek-x"
    assert p["tools"] == [{"type": "function"}]
    assert p["messages"][0]["content"] == "hi"


def test_chat_normalizes_tool_calls(monkeypatch):
    class FakeResp:
        def raise_for_status(self): pass
        def json(self):
            return {"choices": [{"message": {
                "content": None,
                "tool_calls": [{"id": "abc", "type": "function",
                                "function": {"name": "write_file",
                                             "arguments": '{"path": "x.txt", "content": "y"}'}}],
            }}]}

    def fake_post(url, headers=None, json=None, timeout=None):
        assert url.endswith("/v1/chat/completions")
        assert headers["Authorization"] == "Bearer KEY"
        return FakeResp()

    monkeypatch.setattr(deepseek_client.httpx, "post", fake_post)
    out = deepseek_client.chat([{"role": "user", "content": "go"}], [], api_key="KEY", model="m")
    assert out["content"] is None
    assert out["tool_calls"] == [{"id": "abc", "name": "write_file",
                                  "arguments": {"path": "x.txt", "content": "y"}}]
