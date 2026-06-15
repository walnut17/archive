"""Shared HTTP assertions and smoke-case runners for qa-agent."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

import httpx

ASK_RESPONSE_KEYS = {
    "answer",
    "agent_mode",
    "steps",
    "tool_calls",
    "project_switch_hint",
    "confidence_badge",
    "agent_sources",
}
EXTRACT_RESPONSE_KEYS = {"success", "data", "failure_type", "message", "retryable"}
UGLY_PARSE_MARKERS = ("无法解析 LLM 输出", "直接返回原文")


@dataclass
class SmokeResult:
    name: str
    passed: bool
    detail: str = ""


def assert_health_payload(data: dict[str, Any]) -> None:
    assert data.get("status") == "ok"
    assert data.get("service") == "qa-agent"
    assert "git_sha" in data
    assert isinstance(data.get("features"), dict)


def assert_ask_response(data: dict[str, Any]) -> None:
    missing = ASK_RESPONSE_KEYS - data.keys()
    assert not missing, f"AskResponse 缺字段: {missing}"
    assert isinstance(data["answer"], str)
    assert isinstance(data["agent_mode"], bool)
    assert isinstance(data["steps"], list)
    assert isinstance(data["tool_calls"], int)
    assert isinstance(data["agent_sources"], list)
    for step in data["steps"]:
        for key in ("iteration", "thought", "tool", "toolArgs", "observation"):
            assert key in step, f"AgentStep 缺字段 {key}"


def assert_extract_response(data: dict[str, Any]) -> None:
    missing = EXTRACT_RESPONSE_KEYS - data.keys()
    assert not missing, f"ExtractResponse 缺字段: {missing}"
    assert isinstance(data["success"], bool)


def assert_no_ugly_parse_fallback(steps: list[dict[str, Any]]) -> None:
    blob = " ".join(
        str(step.get(field) or "")
        for step in steps
        for field in ("thought", "tool", "toolArgs", "observation")
    )
    for marker in UGLY_PARSE_MARKERS:
        assert marker not in blob, f"思考链含旧版 parse fallback 文案: {marker}"


def run_smoke_suite(client: httpx.Client) -> list[SmokeResult]:
    results: list[SmokeResult] = []

    def record(name: str, fn) -> None:
        try:
            fn()
            results.append(SmokeResult(name, True))
        except AssertionError as exc:
            results.append(SmokeResult(name, False, str(exc)))
        except httpx.HTTPError as exc:
            results.append(SmokeResult(name, False, f"HTTP 错误: {exc}"))

    def test_health() -> None:
        resp = client.get("/health")
        assert resp.status_code == 200, resp.text
        assert_health_payload(resp.json())

    def test_ask_validation() -> None:
        resp = client.post("/v1/ask", json={"question": ""})
        assert resp.status_code == 422, resp.text

    def test_turn_validation() -> None:
        resp = client.post("/v1/turn/   ", json={"question": "测试"})
        assert resp.status_code == 400, resp.text

    def test_extract_validation() -> None:
        resp = client.post("/v1/extract/project-fields", json={})
        assert resp.status_code == 422, resp.text

    def test_extract_not_found() -> None:
        resp = client.post("/v1/extract/project-fields", json={"material_version_id": 999999999})
        assert resp.status_code == 200, resp.text
        body = resp.json()
        assert_extract_response(body)
        assert body["success"] is False
        assert body["failure_type"] == "FIELD_MISSING"

    def test_ask_off_topic() -> None:
        resp = client.post("/v1/ask", json={"question": "今天天气怎么样？"})
        assert resp.status_code == 200, resp.text
        body = resp.json()
        assert_ask_response(body)
        assert body["answer"].strip()
        assert body["agent_mode"] is True
        assert_no_ugly_parse_fallback(body["steps"])

    def test_multi_turn() -> None:
        session_id = f"smoke-{uuid.uuid4()}"
        first = client.post(
            f"/v1/turn/{session_id}",
            json={"question": "今天天气怎么样？"},
        )
        assert first.status_code == 200, first.text
        first_body = first.json()
        assert_ask_response(first_body)
        assert_no_ugly_parse_fallback(first_body["steps"])

        second = client.post(
            f"/v1/turn/{session_id}",
            json={"question": "PRJ-2026-001 剩余金额多少？"},
        )
        assert second.status_code == 200, second.text
        second_body = second.json()
        assert_ask_response(second_body)
        assert second_body["answer"].strip()

    record("GET /health", test_health)
    record("POST /v1/ask 空问题 422", test_ask_validation)
    record("POST /v1/turn 空 session 400", test_turn_validation)
    record("POST /v1/extract 缺字段 422", test_extract_validation)
    record("POST /v1/extract 不存在 ID", test_extract_not_found)
    record("POST /v1/ask 离题拒答", test_ask_off_topic)
    record("POST /v1/turn 同 session 连续 2 问", test_multi_turn)
    return results
