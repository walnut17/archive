"""Direct HTTP integration tests against a running qa-agent service."""

from __future__ import annotations

import uuid

import pytest

from tests.http_helpers import (
    assert_ask_response,
    assert_extract_response,
    assert_health_payload,
    assert_no_ugly_parse_fallback,
    run_smoke_suite,
)

pytestmark = pytest.mark.live


def test_live_health(live_client):
    resp = live_client.get("/health")
    assert resp.status_code == 200
    assert_health_payload(resp.json())


def test_live_ask_validation(live_client):
    resp = live_client.post("/v1/ask", json={"question": ""})
    assert resp.status_code == 422


def test_live_turn_validation(live_client):
    resp = live_client.post("/v1/turn/   ", json={"question": "测试"})
    assert resp.status_code == 400


def test_live_extract_validation(live_client):
    resp = live_client.post("/v1/extract/project-fields", json={})
    assert resp.status_code == 422


def test_live_extract_not_found(live_client):
    resp = live_client.post("/v1/extract/project-fields", json={"material_version_id": 999999999})
    assert resp.status_code == 200
    body = resp.json()
    assert_extract_response(body)
    assert body["success"] is False
    assert body["failure_type"] == "FIELD_MISSING"


def test_live_ask_off_topic(live_client):
    resp = live_client.post("/v1/ask", json={"question": "今天天气怎么样？"})
    assert resp.status_code == 200
    body = resp.json()
    assert_ask_response(body)
    assert body["answer"].strip()
    assert body["agent_mode"] is True
    assert_no_ugly_parse_fallback(body["steps"])


def test_live_multi_turn_same_session(live_client):
    """Covers T-0612-04 at Python service layer: two turns must both return 200."""
    session_id = f"pytest-{uuid.uuid4()}"
    first = live_client.post(
        f"/v1/turn/{session_id}",
        json={"question": "今天天气怎么样？"},
    )
    assert first.status_code == 200, first.text
    first_body = first.json()
    assert_ask_response(first_body)
    assert_no_ugly_parse_fallback(first_body["steps"])

    second = live_client.post(
        f"/v1/turn/{session_id}",
        json={"question": "PRJ-2026-001 剩余金额多少？"},
    )
    assert second.status_code == 200, second.text
    second_body = second.json()
    assert_ask_response(second_body)
    assert second_body["answer"].strip()


def test_live_smoke_suite_runner(live_client):
    results = run_smoke_suite(live_client)
    failed = [item for item in results if not item.passed]
    assert not failed, "\n".join(f"{item.name}: {item.detail}" for item in failed)
