"""Thin DeepSeek client over the OpenAI-compatible chat-completions API."""
import json

import httpx


def build_payload(model: str, messages: list, tools: list) -> dict:
    payload = {"model": model, "messages": messages}
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = "auto"
    return payload


def chat(messages, tools, *, api_key, model,
         base_url: str = "https://api.deepseek.com", timeout: int = 120) -> dict:
    resp = httpx.post(
        f"{base_url}/v1/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json=build_payload(model, messages, tools),
        timeout=timeout,
    )
    resp.raise_for_status()
    msg = resp.json()["choices"][0]["message"]
    calls = []
    for tc in msg.get("tool_calls") or []:
        fn = tc["function"]
        try:
            args = json.loads(fn.get("arguments") or "{}")
        except json.JSONDecodeError:
            args = {}
        calls.append({"id": tc.get("id", ""), "name": fn["name"], "arguments": args})
    return {"content": msg.get("content"), "tool_calls": calls}
