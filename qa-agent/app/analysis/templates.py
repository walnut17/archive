"""加载分析模板 — DB 优先，内置兜底."""

from __future__ import annotations

import json
import logging
from typing import Any

from app.analysis.models import AnalysisScope, AnalysisTemplate
from app.analysis.templates_builtin import builtin_templates, get_builtin_template
from app.db.connection import db_cursor

logger = logging.getLogger(__name__)


def _row_to_template(row: dict[str, Any]) -> AnalysisTemplate:
    schema = row.get("output_schema")
    if isinstance(schema, str):
        try:
            schema = json.loads(schema)
        except json.JSONDecodeError:
            schema = {}
    if not isinstance(schema, dict):
        schema = {}
    return AnalysisTemplate(
        code=row["code"],
        name=row.get("name") or row["code"],
        scope=row.get("scope") or AnalysisScope.PROJECT,
        description=row.get("description") or "",
        prompt_template=row.get("prompt_template") or "",
        output_schema=schema,
        max_input_chars=int(row.get("max_input_chars") or 30000),
        enabled=bool(row.get("enabled", 1)),
        builtin=bool(row.get("builtin", 0)),
        sort_order=int(row.get("sort_order") or 0),
    )


def list_templates(*, scope: str | None = None, enabled_only: bool = True) -> list[AnalysisTemplate]:
    try:
        with db_cursor() as cur:
            sql = "SELECT * FROM analysis_template WHERE 1=1"
            params: list[Any] = []
            if enabled_only:
                sql += " AND enabled = 1"
            if scope:
                sql += " AND scope = %s"
                params.append(scope)
            sql += " ORDER BY sort_order, id"
            cur.execute(sql, params)
            rows = cur.fetchall() or []
        if rows:
            return [_row_to_template(r) for r in rows]
    except Exception as e:
        logger.debug("analysis_template 表不可用，使用内置模板: %s", e)

    templates = builtin_templates()
    if scope:
        templates = [t for t in templates if t.scope == scope]
    if enabled_only:
        templates = [t for t in templates if t.enabled]
    return templates


def get_template(code: str) -> AnalysisTemplate | None:
    try:
        with db_cursor() as cur:
            cur.execute("SELECT * FROM analysis_template WHERE code = %s", (code,))
            row = cur.fetchone()
        if row:
            return _row_to_template(row)
    except Exception as e:
        logger.debug("get_template DB 失败: %s", e)
    return get_builtin_template(code)
