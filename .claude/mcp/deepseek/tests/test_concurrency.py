"""Parallel-execution contract for the delegation server.

The bug class this guards: `deepseek_task` was a *synchronous* FastMCP tool, executed inline on the
server's single event loop — so a long task (agent loop + a full Gradle build) blocked the whole
server and serialized every other call. The fix offloads the work to a worker thread under a shared
`CapacityLimiter`, so up to `MAX_CONCURRENCY` delegations run at once and the rest queue.
"""
import threading
import time

import anyio

import server


def test_deepseek_task_offloads_and_caps_concurrency(monkeypatch):
    monkeypatch.setattr(server, "API_KEY", "test-key")   # pass the early guard
    monkeypatch.setattr(server, "MAX_CONCURRENCY", 2)
    monkeypatch.setattr(server, "_LIMITER", None)         # force a fresh limiter at cap=2

    active = {"n": 0, "max": 0}
    lock = threading.Lock()

    def fake_run_task(*args, **kwargs):
        with lock:
            active["n"] += 1
            active["max"] = max(active["max"], active["n"])
        time.sleep(0.2)
        with lock:
            active["n"] -= 1
        return {"status": "verified"}

    monkeypatch.setattr(server, "_run_task", fake_run_task)

    async def main():
        results = []

        async def one():
            results.append(await server._dispatch_task("do a thing"))

        async with anyio.create_task_group() as tg:
            for _ in range(6):
                tg.start_soon(one)
        return results

    results = anyio.run(main)
    assert len(results) == 6
    assert all(r["status"] == "verified" for r in results)
    # >1 proves true concurrency (not serialized); ==cap proves the limiter bounds it.
    assert active["max"] == 2


def test_dispatch_short_circuits_without_api_key(monkeypatch):
    monkeypatch.setattr(server, "API_KEY", "")
    called = {"n": 0}

    def fake_run_task(*a, **k):
        called["n"] += 1
        return {}

    monkeypatch.setattr(server, "_run_task", fake_run_task)
    r = anyio.run(server._dispatch_task, "x")
    assert r["status"] == "error"
    assert called["n"] == 0   # never reached the worker thread
