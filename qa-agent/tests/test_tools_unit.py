"""Unit tests for qa-agent tools and registry."""

from __future__ import annotations

import json
from unittest.mock import MagicMock, patch

import pytest

from app.agent.tools import query_mysql
from app.agent.tools.registry import dispatch_tool


def test_query_mysql_rejects_unknown_table():
    with pytest.raises(ValueError, match="白名单"):
        query_mysql.run({"table": "users", "columns": ["id"]}, {})


def test_query_mysql_caps_limit():
    with patch("app.agent.tools.query_mysql.db_cursor") as db_mock:
        cur = MagicMock()
        cur.fetchall.return_value = []
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        query_mysql.run({"table": "project", "limit": 5000}, {})

    sql = cur.execute.call_args[0][0]
    assert "LIMIT 1000" in sql


def test_query_mysql_builds_where_clause():
    with patch("app.agent.tools.query_mysql.db_cursor") as db_mock:
        cur = MagicMock()
        cur.fetchall.return_value = [{"code": "PRJ-2026-001"}]
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        rows = query_mysql.run(
            {
                "table": "project",
                "columns": ["code", "name"],
                "where": [{"field": "code", "op": "=", "value": "PRJ-2026-001"}],
                "limit": 10,
            },
            {},
        )

    assert rows == [{"code": "PRJ-2026-001"}]
    sql = cur.execute.call_args[0][0]
    assert "FROM `project`" in sql
    assert "`code` = %s" in sql


def test_dispatch_unknown_tool():
    with pytest.raises(ValueError, match="未知工具"):
        dispatch_tool("delete_database", "{}", {})


def test_dispatch_find_project_sets_context():
    payload = [{"code": "PRJ-2026-001", "name": "demo", "confidence": 1.0}]
    ctx: dict = {}

    def fake_find_project(args, ctx):
        return payload

    with patch.dict(
        "app.agent.tools.registry._TOOLS",
        {"find_project": fake_find_project},
    ):
        result = dispatch_tool(
            "find_project",
            json.dumps({"query": "PRJ-2026-001", "topN": 1}),
            ctx,
        )
    assert result == payload
    assert ctx["project_code"] == "PRJ-2026-001"
