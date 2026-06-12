"""v1.2 流式 SSE 单测.

mock GLM 流式返回 → 验证 run_agent_stream yield 顺序.
"""
import json
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from app.agent.engine import run_agent_stream, _sse_event


class FakeGlmStream:
    """mock 流式 LLM: 给个 token 列表, 模拟 SSE chunk 序列."""

    def __init__(self, tokens):
        self.tokens = tokens
        self.idx = 0

    def chat_stream(self, system, user):
        for t in self.tokens:
            yield t


def test_sse_event_format():
    """SSE 格式: event: X\\ndata: {...}\\n\\n"""
    out = _sse_event("step", {"iteration": 1, "tool": "find_project"})
    assert out.startswith("event: step\n")
    assert "data: " in out
    assert out.endswith("\n\n")
    parsed = json.loads(out.split("data: ", 1)[1].strip())
    assert parsed["iteration"] == 1


def test_run_agent_stream_yields_token_events(monkeypatch):
    """run_agent_stream 应该 yield token 事件 + step + done."""
    from app.agent import engine

    # 替换 glm_client.chat_stream 为 fake
    tokens = ["hello", " ", "world"]
    monkeypatch.setattr(engine.glm_client, "chat_stream", lambda s, u: iter(tokens))

    # 模拟 parse_agent_step 让它解析 FINAL_ANSWER (避免工具调用)
    monkeypatch.setattr(engine, "parse_agent_step", lambda raw, i: {
        "iteration": i,
        "thought": "ok",
        "tool": "FINAL_ANSWER",
        "toolArgs": json.dumps({"answer": "hello world"}, ensure_ascii=False),
        "observation": "",
    })

    events = list(run_agent_stream("test question", session_id="test-session"))
    event_types = []
    for raw in events:
        if raw.startswith("event: "):
            event_name = raw.split("\n")[0].split("event: ", 1)[1]
            event_types.append(event_name)
    # 期望: 至少 1 个 token 事件 + 1 个 step 事件 + 1 个 done 事件
    assert "token" in event_types, f"应至少 1 个 token 事件, 实际: {event_types}"
    assert "step" in event_types
    assert event_types[-1] == "done"


if __name__ == "__main__":
    # 简单跑（不依赖 pytest 时）
    print("test_sse_event_format ok")
    e = _sse_event("step", {"i": 1})
    assert "event: step" in e
    print("  pass")

    # 跑 token 测
    from app.agent import engine
    engine.glm_client.chat_stream = lambda s, u: iter(["hi", " ", "there"])
    engine.parse_agent_step = lambda raw, i: {
        "iteration": i, "thought": "x", "tool": "FINAL_ANSWER",
        "toolArgs": json.dumps({"answer": "hi there"}, ensure_ascii=False),
        "observation": "",
    }
    evs = list(run_agent_stream("q", session_id="s"))
    types = []
    for e in evs:
        if e.startswith("event: "):
            types.append(e.split("\n")[0].replace("event: ", ""))
    assert "token" in types and "step" in types and types[-1] == "done"
    print("test_run_agent_stream_yields_token_events pass")
    print("\n✓ All streaming tests passed")
