"""内置分析模板 — DB 未迁移或表为空时的兜底."""

from __future__ import annotations

from typing import Any

from app.analysis.models import AnalysisTemplate

_BUILTIN: list[AnalysisTemplate] = [
    AnalysisTemplate(
        code="project.credit_inventory",
        name="项目债权清单",
        scope="project",
        description="识别项目涉及的全部债权标的",
        sort_order=5,
        prompt_template=(
            "你是投委会档案分析助手。列出本项目材料中涉及的全部债权/底层资产名称。\n"
            "项目: {project_name} ({project_code})\n\n材料正文:\n{materials}\n\n"
            "输出 JSON 格式:\n"
            '{{"credits": [{{"name": "债权名称", "confidence": 0.9}}], "notes": ""}}\n'
            "只输出 JSON，不要 markdown。"
        ),
        output_schema={
            "type": "object",
            "fields": ["credits", "notes"],
            "credits": {"type": "array", "items": {"name": "string", "confidence": "number"}},
        },
    ),
    AnalysisTemplate(
        code="project.investment_structure",
        name="项目投资结构",
        scope="project",
        description="投资结构、交易形式、核心资产",
        sort_order=10,
        prompt_template=(
            "你是投委会档案分析助手。根据材料提取项目投资结构与交易形式。\n"
            "项目: {project_name} ({project_code})\n\n材料正文:\n{materials}\n\n"
            "输出 JSON 字段: investmentStructure, transactionForm, coreAssets, "
            "financingAmountWan, counterparty, notes。只输出 JSON。"
        ),
        output_schema={
            "type": "object",
            "fields": [
                "investmentStructure",
                "transactionForm",
                "coreAssets",
                "financingAmountWan",
                "counterparty",
                "notes",
            ],
        },
    ),
    AnalysisTemplate(
        code="project.interest_rate_schedule",
        name="项目利率历程",
        scope="project",
        description="利率/固定收益约定及变更",
        sort_order=20,
        prompt_template=(
            "你是投委会档案分析助手。从材料提取利率、固定收益、回购收益率约定。\n"
            "项目: {project_name} ({project_code})\n\n材料正文:\n{materials}\n\n"
            "利率可能在不同阶段变化。输出 JSON 字段: currentRate, rateUnit, "
            'rateSchedule(数组: {{stage, rate, effectiveFrom, source}}), notes。只输出 JSON。'
        ),
        output_schema={
            "type": "object",
            "fields": ["currentRate", "rateUnit", "rateSchedule", "notes"],
        },
    ),
    AnalysisTemplate(
        code="asset.credit_profile",
        name="债权资产画像",
        scope="asset",
        description="债务人、担保、抵押、转让、利率、法律状态",
        sort_order=30,
        max_input_chars=35000,
        prompt_template=(
            "你是不良资产/债权分析助手。针对下列债权底层资产提取关键信息。\n"
            "项目: {project_name} ({project_code})\n目标资产: {asset_name}\n\n"
            "材料正文:\n{materials}\n\n"
            "输出 JSON 字段: assetName, debtor, guarantors(数组), collateral(对象或数组), "
            "originalCreditor, transferChain(数组), principalWan, "
            "interestRates(含 initial/compound/penalty), startDate, "
            "legalStatus(未诉/诉讼中/已裁定), unsuedDetails(通知时间/诉讼时效等), notes。"
            "只输出 JSON。"
        ),
        output_schema={
            "type": "object",
            "fields": [
                "assetName",
                "debtor",
                "guarantors",
                "collateral",
                "originalCreditor",
                "transferChain",
                "principalWan",
                "interestRates",
                "startDate",
                "legalStatus",
                "unsuedDetails",
                "notes",
            ],
        },
    ),
]

_BUILTIN_BY_CODE = {t.code: t for t in _BUILTIN}


def builtin_templates() -> list[AnalysisTemplate]:
    return list(_BUILTIN)


def get_builtin_template(code: str) -> AnalysisTemplate | None:
    return _BUILTIN_BY_CODE.get(code)


def schema_hint(schema: dict[str, Any]) -> str:
    fields = schema.get("fields") or []
    if not fields:
        return "按 output_schema 输出合法 JSON。"
    return "JSON 须包含字段: " + ", ".join(str(f) for f in fields)
