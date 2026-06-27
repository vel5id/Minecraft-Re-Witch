"""The DeepSeek agent's tool-calling loop. The chat client is injected (testable)."""
import json

FINISH_TOOL = {"type": "function", "function": {
    "name": "finish",
    "description": "Call when the task is complete. Provide a short summary of what changed.",
    "parameters": {"type": "object", "properties": {"summary": {"type": "string"}},
                   "required": ["summary"]},
}}

SYSTEM = (
    "You are a coding agent editing a repository through the provided file tools. "
    "You have NO shell and cannot run commands or tests — only read, write, edit, list, glob, grep. "
    "Make the requested change across as many files as needed, then call `finish` with a summary. "
    "All paths are relative to the repository root."
)


def _to_openai_tool_call(tc: dict) -> dict:
    return {"id": tc["id"], "type": "function",
            "function": {"name": tc["name"], "arguments": json.dumps(tc.get("arguments", {}))}}


def run_agent(task: str, fs, chat, *, max_iterations: int = 40, context: str = "") -> dict:
    tools = fs.tool_schemas() + [FINISH_TOOL]
    user = task if not context else f"{task}\n\n--- Context files ---\n{context}"
    messages = [{"role": "system", "content": SYSTEM}, {"role": "user", "content": user}]

    for i in range(1, max_iterations + 1):
        resp = chat(messages, tools)
        tcs = resp.get("tool_calls") or []
        if not tcs:
            messages.append({"role": "assistant", "content": resp.get("content") or ""})
            messages.append({"role": "user", "content": "Use a file tool, or call finish when done."})
            continue
        messages.append({"role": "assistant", "content": resp.get("content"),
                         "tool_calls": [_to_openai_tool_call(tc) for tc in tcs]})
        for tc in tcs:
            if tc["name"] == "finish":
                return {"status": "completed", "iterations": i,
                        "summary": tc.get("arguments", {}).get("summary", "")}
            result = fs.dispatch(tc["name"], tc.get("arguments", {}))
            messages.append({"role": "tool", "tool_call_id": tc["id"],
                             "content": json.dumps(result)})
    return {"status": "max_iterations", "iterations": max_iterations, "summary": ""}
