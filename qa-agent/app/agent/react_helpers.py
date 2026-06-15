"""ReAct 迭代辅助：上下文递增、工具链升级（T-0612-07）."""

from __future__ import annotations

import json
import re
from typing import Any

_MATERIAL_STAT_RE = re.compile(r"材料|份数|几份|多少个|多少份|几个材料")


def question_needs_material_stats(question: str) -> bool:
    return bool(_MATERIAL_STAT_RE.search(question or ""))


def _parse_obs_list(observation: str | None) -> list[dict[str, Any]]:
    if not observation:
        return []
    text = observation.strip()
    if not text.startswith("["):
        return []
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return []
    return data if isinstance(data, list) else []


def last_find_project_hit(steps: list[dict]) -> dict[str, Any] | None:
    for step in reversed(steps):
        if step.get("tool") != "find_project":
            continue
        items = _parse_obs_list(step.get("observation"))
        if not items:
            continue
        item = items[0]
        if not isinstance(item, dict):
            continue
        code = item.get("projectCode") or item.get("code")
        if code:
            return item
    return None


def had_find_project_hit(steps: list[dict]) -> bool:
    return last_find_project_hit(steps) is not None


def append_step_hints(parts: list[str], question: str, steps: list[dict]) -> None:
    """每轮在 prompt 末尾追加引擎提示，确保 LLM 看到递增约束."""
    if not steps:
        return

    hit = last_find_project_hit(steps)
    if hit and question_needs_material_stats(question):
        code = hit.get("projectCode") or hit.get("code")
        name = hit.get("projectName") or hit.get("name") or ""
        parts.append(
            f"\n【引擎提示】步骤 {len(steps)} 已通过 find_project 锁定项目 {code} ({name})。"
        )
        parts.append(
            "用户问的是材料份数/统计 → 下一步必须调用 get_project_business_data(projectCode)，"
            "禁止再次 find_project（即使 query 不同）。"
        )
        return

    last = steps[-1]
    if last.get("tool") == "find_project" and not _parse_obs_list(last.get("observation")):
        parts.append(
            "\n【引擎提示】上一步 find_project 未命中 → 可换检索词/变体再试，"
            "或 ask_clarification；禁止原样重复相同 args。"
        )


def try_recover_material_count_loop(
    steps: list[dict],
    question: str,
    iteration: int,
    ctx: dict[str, Any],
    dispatch_fn,
    truncate_fn,
) -> tuple[str | None, list[dict]]:
    """重复 find_project 时补调 get_project_business_data 并生成材料数答案."""
    if len(steps) < 2:
        return None, []
    a, b = steps[-2], steps[-1]
    if a["tool"] != b["tool"] or a.get("toolArgs") != b.get("toolArgs"):
        return None, []
    if a["tool"] != "find_project" or not question_needs_material_stats(question):
        return None, []

    hit = last_find_project_hit(steps[:-1])
    if not hit:
        return None, []

    code = hit.get("projectCode") or hit.get("code")
    name = hit.get("projectName") or hit.get("name") or code
    try:
        biz = dispatch_fn(
            "get_project_business_data",
            json.dumps({"projectCode": code}, ensure_ascii=False),
            ctx,
        )
        count = biz.get("materialCount", 0) if isinstance(biz, dict) else 0
        final_answer = f"项目 {name} ({code}) 下共 {count} 份材料。"
        extra = [
            {
                "iteration": iteration + 1,
                "thought": "重复 find_project 后引擎补调 get_project_business_data",
                "tool": "get_project_business_data",
                "toolArgs": json.dumps({"projectCode": code}, ensure_ascii=False),
                "observation": truncate_fn(
                    json.dumps(biz, ensure_ascii=False) if isinstance(biz, dict) else str(biz)
                ),
            },
            {
                "iteration": iteration + 2,
                "thought": "材料统计完成",
                "tool": "FINAL_ANSWER",
                "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
                "observation": "",
            },
        ]
        return final_answer, extra
    except Exception:
        return None, []


def maybe_upgrade_step(step: dict[str, Any], steps: list[dict], question: str) -> dict[str, Any]:
    """若 LLM 在已有 find 命中后仍选 find_project，引擎强制升级为 get_project_business_data."""
    if step.get("tool") != "find_project":
        return step
    if not question_needs_material_stats(question):
        return step
    if not had_find_project_hit(steps):
        return step

    hit = last_find_project_hit(steps)
    if not hit:
        return step

    code = hit.get("projectCode") or hit.get("code")
    return {
        **step,
        "thought": (
            f"上一步 find_project 已命中 {code}，统计类问题改调 get_project_business_data"
        ),
        "tool": "get_project_business_data",
        "toolArgs": json.dumps({"projectCode": code}, ensure_ascii=False),
    }
