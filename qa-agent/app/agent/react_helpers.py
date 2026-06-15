"""ReAct 迭代辅助：上下文递增、工具链升级（T-0612-07）."""

from __future__ import annotations

import json
import re
from typing import Any

from app.agent.parser import FINAL_ANSWER

_MATERIAL_STAT_RE = re.compile(r"材料|份数|几份|多少个|多少份|几个材料")
_MATERIAL_EVIDENCE_RE = re.compile(
    r"利率|收益率|息率|费率|条款|合同|抵押|固定收益|回购价|回购利率|投资回报率"
)
_RATE_EXTRACT_RE = re.compile(
    r"固定收益\s*(\d+(?:\.\d+)?)\s*%|"
    r"利率[为是：:\s]*(\d+(?:\.\d+)?)\s*%|"
    r"年化(?:收益|利率)?\s*(\d+(?:\.\d+)?)\s*%"
)


def question_needs_material_stats(question: str) -> bool:
    return bool(_MATERIAL_STAT_RE.search(question or ""))


def question_needs_material_evidence(question: str) -> bool:
    return bool(_MATERIAL_EVIDENCE_RE.search(question or ""))


def evidence_search_query(question: str) -> str:
    terms: list[str] = []
    for kw in ("固定收益", "利率", "收益率", "息率", "回购", "抵押", "条款"):
        if kw in (question or ""):
            terms.append(kw)
    return " ".join(terms) if terms else "利率 固定收益"


def _parse_obs_dict(observation: str | None) -> dict[str, Any] | None:
    if not observation:
        return None
    text = observation.strip()
    if not text.startswith("{"):
        return None
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def material_count_answer_from_biz(biz: dict[str, Any]) -> str:
    code = biz.get("projectCode", "")
    name = biz.get("projectName") or code
    count = biz.get("materialCount", 0)
    return f"项目 {name} ({code}) 下共 {count} 份材料。"


def extract_rate_from_texts(texts: list[str]) -> str | None:
    for text in texts:
        if not text:
            continue
        m = _RATE_EXTRACT_RE.search(text)
        if m:
            pct = next(g for g in m.groups() if g)
            return f"{pct}%"
    return None


def synthesize_evidence_answer(
    question: str,
    project_name: str,
    project_code: str,
    search_hits: list[dict[str, Any]],
) -> str | None:
    texts = []
    sources: list[str] = []
    for i, hit in enumerate(search_hits[:5], 1):
        excerpt = hit.get("parsedExcerpt") or hit.get("snippet") or ""
        if excerpt:
            texts.append(excerpt)
        title = hit.get("materialTitle") or hit.get("originalFilename") or f"材料{i}"
        sources.append(f"[{i}] {title}")

    rate = extract_rate_from_texts(texts)
    if rate and "利率" in (question or ""):
        src = "、".join(sources[:3]) if sources else "材料全文检索"
        return (
            f"项目 {project_name} ({project_code}) 材料显示固定收益/利率约为 {rate}。"
            f"\n\n引用来源: {src}"
        )
    if texts and question_needs_material_evidence(question):
        preview = (texts[0][:180] + "…") if len(texts[0]) > 180 else texts[0]
        src = "、".join(sources[:2]) if sources else "材料全文检索"
        return (
            f"项目 {project_name} ({project_code}) 相关材料摘录：{preview}"
            f"\n\n引用来源: {src}"
        )
    return None


def last_business_data(steps: list[dict]) -> dict[str, Any] | None:
    for step in reversed(steps):
        if step.get("tool") != "get_project_business_data":
            continue
        obs = _parse_obs_dict(step.get("observation"))
        if obs and "materialCount" in obs:
            return obs
    return None


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
    if hit and question_needs_material_evidence(question):
        code = hit.get("projectCode") or hit.get("code")
        name = hit.get("projectName") or hit.get("name") or ""
        q = evidence_search_query(question)
        parts.append(
            f"\n【引擎提示】步骤 {len(steps)} 已锁定项目 {code} ({name})。"
        )
        parts.append(
            f"用户问的是材料正文中的业务事实（如利率/条款）→ 下一步必须 search_fulltext"
            f'(query="{q}", projectCode="{code}")；'
            "get_project_business_data 只有汇总字段、不含材料利率正文。"
        )
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

    biz = last_business_data(steps)
    if biz and question_needs_material_stats(question):
        parts.append(
            f"\n【引擎提示】get_project_business_data 已返回 materialCount={biz.get('materialCount')}。"
        )
        parts.append("数据已齐 → 下一步必须 FINAL_ANSWER，禁止再次 get_project_business_data。")
        return

    last = steps[-1]
    if last.get("tool") == "search_fulltext" and question_needs_material_evidence(question):
        items = _parse_obs_list(last.get("observation"))
        if items:
            parts.append(
                "\n【引擎提示】search_fulltext 已返回材料摘录 → 下一步 FINAL_ANSWER，"
                "根据 observation 中的 parsedExcerpt/snippet 作答并引用材料。"
            )
        return

    last = steps[-1]
    if last.get("tool") == "find_project" and not _parse_obs_list(last.get("observation")):
        parts.append(
            "\n【引擎提示】上一步 find_project 未命中 → 可换检索词/变体再试，"
            "或 ask_clarification；禁止原样重复相同 args。"
        )


def try_recover_evidence_loop(
    steps: list[dict],
    question: str,
    iteration: int,
    ctx: dict[str, Any],
    dispatch_fn,
    truncate_fn,
) -> tuple[str | None, list[dict]]:
    """重复 get/search 时补调全文检索并合成材料证据答案."""
    if not question_needs_material_evidence(question):
        return None, []
    hit = last_find_project_hit(steps)
    if not hit:
        return None, []
    code = hit.get("projectCode") or hit.get("code")
    name = hit.get("projectName") or hit.get("name") or code

    if len(steps) >= 2:
        a, b = steps[-2], steps[-1]
        if a["tool"] == b["tool"] and a.get("toolArgs") == b.get("toolArgs"):
            if a["tool"] == "search_fulltext":
                results = _parse_obs_list(b.get("observation"))
                answer = synthesize_evidence_answer(question, name, code, results)
                if answer:
                    extra = [
                        {
                            "iteration": iteration + 1,
                            "thought": "重复 search_fulltext，引擎根据摘录合成答案",
                            "tool": FINAL_ANSWER,
                            "toolArgs": json.dumps({"answer": answer}, ensure_ascii=False),
                            "observation": "",
                        }
                    ]
                    return answer, extra

            if a["tool"] == "get_project_business_data":
                q = evidence_search_query(question)
                try:
                    raw = dispatch_fn(
                        "search_fulltext",
                        json.dumps(
                            {"query": q, "projectCode": code, "topN": 5},
                            ensure_ascii=False,
                        ),
                        ctx,
                    )
                    results = raw if isinstance(raw, list) else []
                    answer = synthesize_evidence_answer(question, name, code, results)
                    if not answer:
                        return None, []
                    extra = [
                        {
                            "iteration": iteration + 1,
                            "thought": "重复 get 后引擎改调 search_fulltext",
                            "tool": "search_fulltext",
                            "toolArgs": json.dumps(
                                {"query": q, "projectCode": code, "topN": 5},
                                ensure_ascii=False,
                            ),
                            "observation": truncate_fn(
                                json.dumps(results, ensure_ascii=False)
                            ),
                        },
                        {
                            "iteration": iteration + 2,
                            "thought": "根据材料摘录作答",
                            "tool": FINAL_ANSWER,
                            "toolArgs": json.dumps({"answer": answer}, ensure_ascii=False),
                            "observation": "",
                        },
                    ]
                    return answer, extra
                except Exception:
                    return None, []

    return None, []


def try_recover_material_count_loop(
    steps: list[dict],
    question: str,
    iteration: int,
    ctx: dict[str, Any],
    dispatch_fn,
    truncate_fn,
) -> tuple[str | None, list[dict]]:
    """重复工具调用时尝试从已有 observation 恢复材料数答案."""
    if len(steps) < 2:
        return None, []
    a, b = steps[-2], steps[-1]
    if a["tool"] != b["tool"] or a.get("toolArgs") != b.get("toolArgs"):
        return None, []
    if not question_needs_material_stats(question):
        return None, []

    if a["tool"] == "get_project_business_data":
        biz = _parse_obs_dict(b.get("observation")) or _parse_obs_dict(a.get("observation"))
        if biz and "materialCount" in biz:
            final_answer = material_count_answer_from_biz(biz)
            extra = [
                {
                    "iteration": iteration + 1,
                    "thought": "重复 get_project_business_data，引擎直接 FINAL_ANSWER",
                    "tool": FINAL_ANSWER,
                    "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
                    "observation": "",
                }
            ]
            return final_answer, extra

    if a["tool"] != "find_project":
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
    """引擎在 LLM 选错工具时强制升级下一步."""
    if question_needs_material_evidence(question) and had_find_project_hit(steps):
        hit = last_find_project_hit(steps)
        if hit:
            code = hit.get("projectCode") or hit.get("code")
            q = evidence_search_query(question)
            if step.get("tool") in ("get_project_business_data", "find_project"):
                return {
                    **step,
                    "thought": (
                        f"材料正文问题改调 search_fulltext 检索利率/条款 (project={code})"
                    ),
                    "tool": "search_fulltext",
                    "toolArgs": json.dumps(
                        {"query": q, "projectCode": code, "topN": 5},
                        ensure_ascii=False,
                    ),
                }
            if steps and steps[-1].get("tool") == "search_fulltext":
                hits = _parse_obs_list(steps[-1].get("observation"))
                answer = synthesize_evidence_answer(
                    question,
                    hit.get("projectName") or hit.get("name") or code,
                    code,
                    hits,
                )
                if answer and step.get("tool") in (
                    "get_project_business_data",
                    "search_fulltext",
                    "find_project",
                ):
                    return {
                        **step,
                        "thought": "search_fulltext 已有摘录，直接 FINAL_ANSWER",
                        "tool": FINAL_ANSWER,
                        "toolArgs": json.dumps({"answer": answer}, ensure_ascii=False),
                    }

    if question_needs_material_stats(question):
        biz = last_business_data(steps)
        if biz and step.get("tool") == "get_project_business_data":
            final_answer = material_count_answer_from_biz(biz)
            return {
                **step,
                "thought": "get_project_business_data 已有 materialCount，直接 FINAL_ANSWER",
                "tool": FINAL_ANSWER,
                "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
            }

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
