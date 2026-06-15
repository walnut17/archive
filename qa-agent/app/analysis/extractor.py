"""LLM 结构化提取执行器."""

from __future__ import annotations

import json
import logging
import re
import time
from typing import Any

from app.analysis.models import AnalysisTemplate, ExtractResult, ProjectContext
from app.analysis.templates_builtin import schema_hint
from app.config import settings
from app.db.connection import db_cursor
from app.llm.glm import glm_client

logger = logging.getLogger(__name__)

_SYSTEM = (
    "你是投委会档案深度分析助手。严格根据材料提取结构化信息。"
    "不确定的字段填 null 或空数组，不要编造。只输出合法 JSON，不要 markdown 代码块。"
)


def _strip_json_fence(raw: str) -> str:
    cleaned = (raw or "").strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```\s*$", "", cleaned)
    return cleaned.strip()


def _render_prompt(template: AnalysisTemplate, ctx: ProjectContext, asset_name: str = "") -> str:
    text = template.prompt_template
    replacements = {
        "{project_name}": ctx.project_name,
        "{project_code}": ctx.project_code,
        "{materials}": ctx.materials_text[: template.max_input_chars],
        "{asset_name}": asset_name or "（未指定，请从材料识别）",
        "{schema_hint}": schema_hint(template.output_schema),
    }
    for key, val in replacements.items():
        text = text.replace(key, val)
    if "{schema_hint}" not in template.prompt_template:
        text += f"\n\n{schema_hint(template.output_schema)}"
    return text


def _log_llm_call(status: str, duration_ms: int) -> None:
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                INSERT INTO llm_call_log (username, scenario, model, duration_ms, status)
                VALUES (%s, %s, %s, %s, %s)
                """,
                ("qa-agent", "ANALYSIS_EXTRACT", settings.glm_chat_model, duration_ms, status),
            )
    except Exception:
        logger.debug("llm_call_log 写入失败", exc_info=True)


def run_template_extract(
    template: AnalysisTemplate,
    ctx: ProjectContext,
    *,
    asset_name: str = "",
) -> ExtractResult:
    if not ctx.materials_text.strip():
        return ExtractResult(success=False, data=None, message="项目无可用 parsed_text 材料")

    user = _render_prompt(template, ctx, asset_name=asset_name)
    t0 = time.time()
    try:
        raw = glm_client.chat(_SYSTEM, user)
        _log_llm_call("OK", int((time.time() - t0) * 1000))
    except Exception as e:
        _log_llm_call("ERROR", int((time.time() - t0) * 1000))
        return ExtractResult(success=False, data=None, message=str(e), raw_text="")

    cleaned = _strip_json_fence(raw)
    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        return ExtractResult(
            success=False,
            data=None,
            message="LLM 返回无法解析为 JSON",
            raw_text=raw[:2000],
        )

    if not isinstance(data, dict):
        return ExtractResult(
            success=False,
            data=None,
            message="LLM 返回不是 JSON 对象",
            raw_text=raw[:2000],
        )

    confidence = None
    if isinstance(data.get("confidence"), (int, float)):
        confidence = float(data["confidence"])

    return ExtractResult(
        success=True,
        data=data,
        raw_text=raw[:4000],
        confidence=confidence,
        confidence_level="AI_INFERRED",
    )


def summarize_result(template: AnalysisTemplate, data: dict[str, Any]) -> str:
    """生成一句话摘要供 Agent 直读."""
    if template.code == "project.interest_rate_schedule":
        rate = data.get("currentRate")
        if rate:
            return f"当前约定利率/收益: {rate}{data.get('rateUnit') or ''}"
        sched = data.get("rateSchedule") or []
        if sched and isinstance(sched, list):
            first = sched[0] if isinstance(sched[0], dict) else {}
            r = first.get("rate")
            if r:
                return f"利率约定: {r}"
    if template.code == "project.investment_structure":
        tx = data.get("transactionForm") or data.get("investmentStructure")
        if tx:
            return f"交易形式/投资结构: {tx}"[:200]
    if template.code == "asset.credit_profile":
        debtor = data.get("debtor")
        legal = data.get("legalStatus")
        parts = [p for p in (data.get("assetName"), debtor, legal) if p]
        if parts:
            return " / ".join(str(p) for p in parts)[:240]
    if template.code == "project.credit_inventory":
        credits = data.get("credits") or []
        if isinstance(credits, list) and credits:
            names = []
            for c in credits[:5]:
                if isinstance(c, dict) and c.get("name"):
                    names.append(str(c["name"]))
                elif isinstance(c, str):
                    names.append(c)
            if names:
                return "债权标的: " + "、".join(names)
    notes = data.get("notes") or data.get("summary")
    if notes:
        return str(notes)[:240]
    return json.dumps(data, ensure_ascii=False)[:240]
