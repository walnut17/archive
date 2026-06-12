"""In-process API contract tests (TestClient + mock GLM/DB)."""

from __future__ import annotations

import json
from unittest.mock import MagicMock, patch

from tests.http_helpers import assert_ask_response, assert_extract_response, assert_no_ugly_parse_fallback


def test_health(api_client):
    resp = api_client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["service"] == "qa-agent"


def test_ask_rejects_empty_question(api_client):
    resp = api_client.post("/v1/ask", json={"question": ""})
    assert resp.status_code == 422


def test_turn_rejects_blank_session_id(api_client):
    resp = api_client.post("/v1/turn/   ", json={"question": "你好"})
    assert resp.status_code == 400
    assert "session_id" in resp.json()["detail"]


def test_extract_rejects_missing_field(api_client):
    resp = api_client.post("/v1/extract/project-fields", json={})
    assert resp.status_code == 422


def test_ask_off_topic_uses_structured_reject(api_client, final_answer_glm):
    resp = api_client.post("/v1/ask", json={"question": "今天天气怎么样？"})
    assert resp.status_code == 200
    body = resp.json()
    assert_ask_response(body)
    assert "档案" in body["answer"]
    assert body["steps"][-1]["tool"] == "FINAL_ANSWER"
    assert_no_ugly_parse_fallback(body["steps"])
    final_answer_glm.assert_called()


def test_ask_invalid_llm_output_is_polite(api_client, mock_glm):
    mock_glm.return_value = "今天天气不错，适合出门。"
    resp = api_client.post("/v1/ask", json={"question": "今天天气怎么样？"})
    assert resp.status_code == 200
    body = resp.json()
    assert_ask_response(body)
    assert body["steps"][-1]["tool"] == "FINAL_ANSWER"
    assert_no_ugly_parse_fallback(body["steps"])


def test_multi_turn_same_session(api_client, mock_glm):
    calls = [
        '{"thought":"离题","tool":"FINAL_ANSWER","args":{"answer":"我只能回答档案问题。"}}',
        '{"thought":"查项目","tool":"find_project","args":{"query":"PRJ-2026-001","topN":1}}',
        '{"thought":"汇总","tool":"FINAL_ANSWER","args":{"answer":"剩余金额 100 万。"}}',
    ]
    mock_glm.side_effect = calls

    session_id = "contract-session-001"
    fake_project = [{"code": "PRJ-2026-001", "name": "demo", "confidence": 1.0}]
    with patch("app.agent.engine.db_cursor") as db_mock, patch(
        "app.agent.tools.find_project.run", return_value=fake_project
    ):
        cur = MagicMock()
        cur.fetchall.return_value = []
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        first = api_client.post(
            f"/v1/turn/{session_id}",
            json={"question": "今天天气怎么样？"},
        )
        second = api_client.post(
            f"/v1/turn/{session_id}",
            json={"question": "PRJ-2026-001 剩余金额多少？"},
        )

    assert first.status_code == 200
    assert second.status_code == 200
    assert_ask_response(first.json())
    assert_ask_response(second.json())
    assert mock_glm.call_count == 3


def test_extract_material_not_found(api_client):
    with patch("app.services.extract.db_cursor") as db_mock:
        cur = MagicMock()
        cur.fetchone.return_value = None
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        resp = api_client.post("/v1/extract/project-fields", json={"material_version_id": 404404})

    assert resp.status_code == 200
    body = resp.json()
    assert_extract_response(body)
    assert body["success"] is False
    assert body["failure_type"] == "FIELD_MISSING"


def test_extract_parse_pending(api_client):
    with patch("app.services.extract.db_cursor") as db_mock:
        cur = MagicMock()
        cur.fetchone.return_value = {
            "id": 1,
            "original_filename": "demo.pdf",
            "parsed_text": "",
            "parse_status": "PENDING",
        }
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        resp = api_client.post("/v1/extract/project-fields", json={"material_version_id": 1})

    body = resp.json()
    assert body["success"] is False
    assert body["failure_type"] == "PARSE_ERROR"
    assert body["retryable"] is True


def test_agent_tool_loop_guard(api_client, mock_glm, mock_db_cursor):
    repeated = json.dumps(
        {"thought": "再查一次", "tool": "find_project", "args": {"query": "新能源", "topN": 1}},
        ensure_ascii=False,
    )
    mock_glm.side_effect = [repeated, repeated]

    with patch("app.agent.tools.find_project.run", return_value=[]):
        resp = api_client.post("/v1/ask", json={"question": "查新能源项目"})

    body = resp.json()
    assert resp.status_code == 200
    assert body["steps"][-1]["tool"] == "FINAL_ANSWER"
    assert "多次尝试" in body["answer"]
