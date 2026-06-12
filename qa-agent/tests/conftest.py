"""Shared fixtures for qa-agent tests."""

from __future__ import annotations

import os
from collections.abc import Iterator
from typing import Any
from unittest.mock import MagicMock, patch

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import app

BASE_URL = os.environ.get("QA_AGENT_BASE_URL", "http://127.0.0.1:8001").rstrip("/")
HTTP_TIMEOUT = float(os.environ.get("QA_AGENT_HTTP_TIMEOUT", "120"))


def qa_agent_reachable(base_url: str = BASE_URL) -> bool:
    try:
        resp = httpx.get(f"{base_url}/health", timeout=5.0)
        return resp.status_code == 200
    except (httpx.HTTPError, OSError):
        return False


@pytest.fixture(scope="session")
def live_base_url() -> str:
    return BASE_URL


@pytest.fixture(scope="session")
def live_client(live_base_url: str) -> Iterator[httpx.Client]:
    if not qa_agent_reachable(live_base_url):
        pytest.skip(f"qa-agent 未启动或不可达: {live_base_url}")
    with httpx.Client(base_url=live_base_url, timeout=HTTP_TIMEOUT) as client:
        yield client


@pytest.fixture
def api_client() -> Iterator[TestClient]:
    with TestClient(app) as client:
        yield client


@pytest.fixture
def mock_glm() -> Iterator[MagicMock]:
    with patch("app.agent.engine.glm_client.chat") as chat:
        yield chat


@pytest.fixture
def mock_db_cursor() -> Iterator[MagicMock]:
    with patch("app.agent.engine.db_cursor") as engine_db, patch(
        "app.services.extract.db_cursor"
    ) as extract_db:

        def _cursor(rows: list[dict[str, Any]] | None = None):
            cur = MagicMock()
            cur.fetchone.return_value = (rows or [None])[0] if rows is not None else None
            cur.fetchall.return_value = rows or []
            cm = MagicMock()
            cm.__enter__.return_value = cur
            cm.__exit__.return_value = False
            return cm

        engine_db.side_effect = lambda: _cursor([])
        extract_db.side_effect = lambda: _cursor([])
        yield engine_db


@pytest.fixture
def final_answer_glm(mock_glm: MagicMock) -> MagicMock:
    mock_glm.return_value = (
        '{"thought":"离题","tool":"FINAL_ANSWER","args":{"answer":"我是投委会档案助手，只回答项目档案相关问题。"}}'
    )
    return mock_glm
