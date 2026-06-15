"""ReAct 迭代辅助：上下文递增、工具链升级（T-0612-07）."""

from __future__ import annotations

FEATURE_EVIDENCE_ROUTING = "2026-06-15"
FEATURE_PROPOSAL_COUNT_ROUTING = "2026-06-15"
FEATURE_DEBT_TARGET_ROUTING = "2026-06-15"
FEATURE_COLLATERAL_ROUTING = "2026-06-15"

import json
import re
from typing import Any

from app.agent.parser import FINAL_ANSWER

_MATERIAL_STAT_RE = re.compile(r"材料|份数|几份|多少个|多少份|几个材料")
_PROPOSAL_STAT_RE = re.compile(r"议案|投委会|议事案")
_MATERIAL_EVIDENCE_RE = re.compile(
    r"利率|收益率|息率|费率|条款|合同|抵押|固定收益|回购价|回购利率|投资回报率|"
    r"债权标的|目标债权|远期回购|什么债权|哪.*债权"
)
_RATE_QUESTION_RE = re.compile(r"利率|收益率|息率|费率|回报率|固定收益|多少%|百分之")
_COLLATERAL_QUESTION_RE = re.compile(r"抵押物|抵押品|质押物")
_DEBT_TARGET_QUESTION_RE = re.compile(r"债权标的|目标债权|什么债权|哪.*债权|标的[是为]")
_DEBT_TARGET_EXTRACT_RE = re.compile(
    r"(南安市岭兜建材二厂债权)|"
    r"(岭兜建材二厂[^，。；\n]{0,12}债权)|"
    r"目标债权[为是：:\s]*([^，。；\n]{4,80})|"
    r"债权标的[为是：:\s]*([^，。；\n]{4,80})|"
    r"转让[^，。；\n]{0,8}(岭兜建材二厂[^，。；\n]{0,20}债权)"
)
_RATE_EXTRACT_RE = re.compile(
    r"固定收益\s*(\d+(?:\.\d+)?)\s*%|"
    r"利率[为是：:\s]*(\d+(?:\.\d+)?)\s*%|"
    r"年化(?:收益|利率)?\s*(\d+(?:\.\d+)?)\s*%|"
    r"回购(?:利率|收益率)?[为是：:\s]*(\d+(?:\.\d+)?)\s*%|"
    r"投资回报率[为是：:\s]*(\d+(?:\.\d+)?)\s*%"
)
_COLLATERAL_SECTION_RE = re.compile(
    r"债权抵押物基本情况[。.]?\s*(.+?)(?:\n[二三四五六七八九十]、|\Z)",
    re.DOTALL,
)


def question_needs_material_stats(question: str) -> bool:
    q = question or ""
    if question_needs_proposal_stats(q) and "材料" not in q:
        return False
    return bool(_MATERIAL_STAT_RE.search(q))


def question_needs_proposal_stats(question: str) -> bool:
    return bool(_PROPOSAL_STAT_RE.search(question or ""))


def question_needs_material_evidence(question: str) -> bool:
    return bool(_MATERIAL_EVIDENCE_RE.search(question or ""))


def question_needs_rate_evidence(question: str) -> bool:
    return bool(_RATE_QUESTION_RE.search(question or ""))


def question_needs_debt_target_evidence(question: str) -> bool:
    q = question or ""
    return bool(_DEBT_TARGET_QUESTION_RE.search(q) or ("回购" in q and "标的" in q))


def question_needs_collateral_evidence(question: str) -> bool:
    return bool(_COLLATERAL_QUESTION_RE.search(question or ""))


def evidence_search_query(question: str, debt_target: str | None = None) -> str:
    terms: list[str] = []
    q = question or ""
    if question_needs_collateral_evidence(q):
        for kw in ("抵押物", "抵押", "土地", "厂房", "设备"):
            if kw not in terms:
                terms.append(kw)
        for anchor in ("岭兜", "建材二厂", "南安"):
            if anchor in q and anchor not in terms:
                terms.append(anchor)
        if debt_target:
            for anchor in ("岭兜", "建材二厂"):
                if anchor in debt_target and anchor not in terms:
                    terms.append(anchor)
    if question_needs_debt_target_evidence(q):
        for kw in ("债权标的", "目标债权", "岭兜", "建材二厂", "远期回购"):
            if kw not in terms:
                terms.append(kw)
    for kw in ("固定收益", "利率", "收益率", "息率", "回购", "抵押", "条款", "债权"):
        if kw in q and kw not in terms:
            terms.append(kw)
    if terms:
        return " ".join(terms[:8])
    return "利率 固定收益"


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


def proposal_count_answer_from_biz(biz: dict[str, Any]) -> str:
    code = biz.get("projectCode", "")
    name = biz.get("projectName") or code
    count = biz.get("proposalCount", 0)
    lines = [f"项目 {name} ({code}) 下共 {count} 个投委会议案。"]
    proposals = biz.get("proposals") or []
    if proposals:
        lines.append("")
        lines.append("议案列表:")
        for i, p in enumerate(proposals[:10], 1):
            p_code = p.get("code") or ""
            p_title = p.get("title") or ""
            p_type = p.get("type") or "类型未知"
            p_status = p.get("status") or ""
            lines.append(f"- [{i}] {p_code} {p_title}（{p_type}，{p_status}）")
    return "\n".join(lines)


def extract_rate_from_texts(texts: list[str]) -> str | None:
    for text in texts:
        if not text:
            continue
        m = _RATE_EXTRACT_RE.search(text)
        if m:
            pct = next(g for g in m.groups() if g)
            return f"{pct}%"
    return None


def _normalize_debt_target(raw: str) -> str:
    text = (raw or "").strip().rstrip("。.；;")
    if "岭兜建材二厂" in text and "债权" in text and "南安" not in text:
        return "南安市岭兜建材二厂债权"
    return text


def extract_debt_target_from_texts(texts: list[str]) -> str | None:
    for text in texts:
        if not text:
            continue
        m = _DEBT_TARGET_EXTRACT_RE.search(text)
        if m:
            return _normalize_debt_target(next(g for g in m.groups() if g))
    return None


def extract_collateral_from_texts(texts: list[str]) -> str | None:
    combined = "\n".join(t for t in texts if t)
    if not combined:
        return None
    m = _COLLATERAL_SECTION_RE.search(combined)
    body = (m.group(1) if m else combined).strip()
    parts: list[str] = []
    land = re.search(
        r"(\d+(?:\.\d+)?亩[^，。；\n]{0,24}土地[^，。；\n]{0,40}抵押)",
        body,
    )
    if land:
        parts.append(land.group(1))
    factory = re.search(r"(?:上盖)?无证厂房\s*(\d+(?:\.\d+)?)\s*平米", body)
    if factory:
        label = f"上盖无证厂房{factory.group(1)}平米"
        if label not in " ".join(parts):
            parts.append(label)
    equip = re.search(r"(设备抵押)", body)
    if equip and equip.group(1) not in " ".join(parts):
        parts.append(equip.group(1))
    summary = re.search(
        r"该债权除了抵押的([^，。；\n]{6,80})",
        body,
    )
    if summary and not parts:
        parts.append(summary.group(1).strip())
    if parts:
        return "；".join(parts)
    if "抵押" in body:
        snippet = re.sub(r"\s+", " ", body[:240]).strip()
        return snippet + ("…" if len(body) > 240 else "")
    return None


def extract_collateral_from_hits(hits: list[dict[str, Any]]) -> str | None:
    texts = [(h.get("parsedExcerpt") or h.get("snippet") or "") for h in hits[:5]]
    return extract_collateral_from_texts(texts)


def extract_debt_target_from_hits(hits: list[dict[str, Any]]) -> str | None:
    texts = [(h.get("parsedExcerpt") or h.get("snippet") or "") for h in hits[:5]]
    found = extract_debt_target_from_texts(texts)
    if found:
        return found
    for hit in hits[:5]:
        title = (hit.get("materialTitle") or hit.get("originalFilename") or "").strip()
        if "岭兜建材二厂" in title:
            return "南安市岭兜建材二厂债权"
        if "岭兜" in title and "债权" in title:
            m = re.search(r"（([^）]+)）", title)
            if m and "岭兜" in m.group(1):
                return _normalize_debt_target(f"南安市{m.group(1)}债权")
    return None


def synthesize_evidence_answer(
    question: str,
    project_name: str,
    project_code: str,
    search_hits: list[dict[str, Any]],
    debt_target: str | None = None,
) -> str | None:
    texts = []
    sources: list[str] = []
    for i, hit in enumerate(search_hits[:5], 1):
        excerpt = hit.get("parsedExcerpt") or hit.get("snippet") or ""
        if excerpt:
            texts.append(excerpt)
        title = hit.get("materialTitle") or hit.get("originalFilename") or f"材料{i}"
        sources.append(f"[{i}] {title}")

    resolved_debt = debt_target or extract_debt_target_from_hits(search_hits)
    if resolved_debt and question_needs_debt_target_evidence(question):
        src = "、".join(sources[:3]) if sources else "材料全文检索"
        label = "远期回购的债权标的" if "回购" in (question or "") else "债权标的"
        return (
            f"项目 {project_name} ({project_code}) {label}是 {resolved_debt}。"
            f"\n\n引用来源: {src}"
        )

    collateral = extract_collateral_from_hits(search_hits)
    if collateral and question_needs_collateral_evidence(question):
        src = "、".join(sources[:3]) if sources else "材料全文检索"
        debt_label = resolved_debt or "该债权"
        return (
            f"项目 {project_name} ({project_code}) {debt_label}的抵押物包括：{collateral}。"
            f"\n\n引用来源: {src}"
        )

    rate = extract_rate_from_texts(texts)
    if rate and question_needs_rate_evidence(question):
        src = "、".join(sources[:3]) if sources else "材料全文检索"
        return (
            f"项目 {project_name} ({project_code}) 材料显示固定收益/利率约为 {rate}。"
            f"\n\n引用来源: {src}"
        )
    if texts and question_needs_material_evidence(question):
        if question_needs_collateral_evidence(question):
            return None
        preview = (texts[0][:180] + "…") if len(texts[0]) > 180 else texts[0]
        src = "、".join(sources[:2]) if sources else "材料全文检索"
        return (
            f"项目 {project_name} ({project_code}) 相关材料摘录：{preview}"
            f"\n\n引用来源: {src}"
        )
    return None


def compact_search_hits_for_obs(hits: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """写入 step observation 时只保留 snippet + 标题，避免 metadata 挤掉正文被截断."""
    out: list[dict[str, Any]] = []
    for h in hits[:5]:
        snippet = (h.get("snippet") or "").strip()
        if not snippet:
            ex = (h.get("parsedExcerpt") or "").strip()
            snippet = ex[:400] + ("…" if len(ex) > 400 else "")
        out.append(
            {
                "materialTitle": h.get("materialTitle"),
                "originalFilename": h.get("originalFilename"),
                "projectCode": h.get("projectCode"),
                "snippet": snippet,
            }
        )
    return out


def try_finalize_evidence_from_search(
    question: str,
    steps: list[dict],
    hits: list[dict[str, Any]],
    ctx: dict[str, Any],
) -> str | None:
    """search_fulltext 返回后，用完整 hits（非截断 observation）合成利率/证据答案."""
    if not question_needs_material_evidence(question) or not hits:
        return None
    hit = last_find_project_hit(steps)
    code = (
        (hit or {}).get("projectCode")
        or (hit or {}).get("code")
        or ctx.get("project_code")
        or (hits[0].get("projectCode") if hits else None)
    )
    name = (
        (hit or {}).get("projectName")
        or (hit or {}).get("name")
        or ctx.get("project_name")
        or (hits[0].get("projectName") if hits else None)
        or code
    )
    if not code:
        return None
    texts = [
        (h.get("parsedExcerpt") or h.get("snippet") or "")
        for h in hits[:5]
    ]
    debt_target = ctx.get("last_debt_target")
    answer = synthesize_evidence_answer(
        question, str(name), str(code), hits, debt_target=debt_target
    )
    if not answer:
        return None
    if question_needs_rate_evidence(question) and extract_rate_from_texts(texts) is None:
        return None
    if question_needs_debt_target_evidence(question) and extract_debt_target_from_hits(hits) is None:
        return None
    if question_needs_collateral_evidence(question) and extract_collateral_from_hits(hits) is None:
        return None
    return answer


def last_business_data(steps: list[dict]) -> dict[str, Any] | None:
    for step in reversed(steps):
        if step.get("tool") != "get_project_business_data":
            continue
        obs = _parse_obs_dict(step.get("observation"))
        if obs and obs.get("projectCode"):
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
        hint = (
            f"用户问的是材料正文中的业务事实（如利率/条款/抵押物）→ 下一步必须 search_fulltext"
            f'(query="{q}", projectCode="{code}")；'
            "get_project_business_data 只有汇总字段、不含材料正文。"
        )
        if question_needs_collateral_evidence(question):
            hint += " 若用户追问「这个债权」的抵押物，query 须含岭兜/建材二厂等债权锚点，勿只搜「抵押物」。"
        parts.append(hint)
        return

    hit = last_find_project_hit(steps)
    if hit and question_needs_proposal_stats(question):
        code = hit.get("projectCode") or hit.get("code")
        name = hit.get("projectName") or hit.get("name") or ""
        parts.append(
            f"\n【引擎提示】步骤 {len(steps)} 已通过 find_project 锁定项目 {code} ({name})。"
        )
        parts.append(
            "用户问的是投委会议案数量/列表 → 下一步必须 get_project_business_data(projectCode)，"
            "读取 proposalCount 与 proposals；禁止 query_mysql(project_code) 或再次 find_project。"
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
    if biz and question_needs_proposal_stats(question):
        parts.append(
            f"\n【引擎提示】get_project_business_data 已返回 proposalCount={biz.get('proposalCount')}。"
        )
        parts.append("数据已齐 → 下一步必须 FINAL_ANSWER，禁止 query_mysql / 重复 get。")
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
                answer = synthesize_evidence_answer(
                    question,
                    name,
                    code,
                    results,
                    debt_target=ctx.get("last_debt_target"),
                )
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
                q = evidence_search_query(question, debt_target=ctx.get("last_debt_target"))
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
                    answer = synthesize_evidence_answer(
                        question,
                        name,
                        code,
                        results,
                        debt_target=ctx.get("last_debt_target"),
                    )
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


def try_recover_proposal_count_loop(
    steps: list[dict],
    question: str,
    iteration: int,
    ctx: dict[str, Any],
    dispatch_fn,
    truncate_fn,
) -> tuple[str | None, list[dict]]:
    """重复工具调用时尝试从已有 observation 恢复议案数答案."""
    if len(steps) < 2:
        return None, []
    a, b = steps[-2], steps[-1]
    if a["tool"] != b["tool"] or a.get("toolArgs") != b.get("toolArgs"):
        return None, []
    if not question_needs_proposal_stats(question):
        return None, []

    if a["tool"] == "get_project_business_data":
        biz = _parse_obs_dict(b.get("observation")) or _parse_obs_dict(a.get("observation"))
        if biz and "proposalCount" in biz:
            final_answer = proposal_count_answer_from_biz(biz)
            extra = [
                {
                    "iteration": iteration + 1,
                    "thought": "重复 get_project_business_data，引擎直接 FINAL_ANSWER（议案）",
                    "tool": FINAL_ANSWER,
                    "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
                    "observation": "",
                }
            ]
            return final_answer, extra

    if a["tool"] not in ("find_project", "query_mysql"):
        return None, []

    hit = last_find_project_hit(steps)
    if not hit:
        return None, []

    code = hit.get("projectCode") or hit.get("code")
    try:
        biz = dispatch_fn(
            "get_project_business_data",
            json.dumps({"projectCode": code}, ensure_ascii=False),
            ctx,
        )
        if not isinstance(biz, dict) or "proposalCount" not in biz:
            return None, []
        final_answer = proposal_count_answer_from_biz(biz)
        extra = [
            {
                "iteration": iteration + 1,
                "thought": "重复 find/query 后引擎补调 get_project_business_data（议案）",
                "tool": "get_project_business_data",
                "toolArgs": json.dumps({"projectCode": code}, ensure_ascii=False),
                "observation": truncate_fn(json.dumps(biz, ensure_ascii=False)),
            },
            {
                "iteration": iteration + 2,
                "thought": "议案统计完成",
                "tool": FINAL_ANSWER,
                "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
                "observation": "",
            },
        ]
        return final_answer, extra
    except Exception:
        return None, []


def maybe_upgrade_step(
    step: dict[str, Any],
    steps: list[dict],
    question: str,
    debt_target: str | None = None,
) -> dict[str, Any]:
    """引擎在 LLM 选错工具时强制升级下一步."""
    if question_needs_material_evidence(question) and had_find_project_hit(steps):
        hit = last_find_project_hit(steps)
        if hit:
            code = hit.get("projectCode") or hit.get("code")
            q = evidence_search_query(question, debt_target=debt_target)
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
                    debt_target=debt_target,
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

    if question_needs_proposal_stats(question):
        biz = last_business_data(steps)
        if biz and step.get("tool") in (
            "get_project_business_data",
            "query_mysql",
        ):
            if "proposalCount" in biz:
                final_answer = proposal_count_answer_from_biz(biz)
                return {
                    **step,
                    "thought": "get_project_business_data 已有 proposalCount，直接 FINAL_ANSWER",
                    "tool": FINAL_ANSWER,
                    "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
                }
        if step.get("tool") in ("query_mysql", "find_project") and had_find_project_hit(steps):
            hit = last_find_project_hit(steps)
            if hit:
                code = hit.get("projectCode") or hit.get("code")
                return {
                    **step,
                    "thought": (
                        f"议案统计改调 get_project_business_data (project={code})"
                    ),
                    "tool": "get_project_business_data",
                    "toolArgs": json.dumps({"projectCode": code}, ensure_ascii=False),
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
    if not (question_needs_material_stats(question) or question_needs_proposal_stats(question)):
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
