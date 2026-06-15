import json
import logging
import re
import time
from typing import Any

from app.agent.parser import FINAL_ANSWER, final_answer_from_args, parse_agent_step
from app.agent.prompts import SYSTEM_PROMPT
from app.agent.react_helpers import (
    append_step_hints,
    compact_search_hits_for_obs,
    maybe_upgrade_step,
    try_finalize_evidence_from_search,
    try_recover_evidence_loop,
    try_recover_material_count_loop,
    try_recover_proposal_count_loop,
)
from app.agent.tools.registry import dispatch_tool
from app.config import settings
from app.db.connection import db_cursor
from app.llm.glm import glm_client

logger = logging.getLogger(__name__)

MEMORY_TABLE = "spring_ai_chat_memory"
MAX_OBS_CHARS = 2000


def _truncate(s: str, n: int = MAX_OBS_CHARS) -> str:
    if len(s) <= n:
        return s
    return s[: n - 3] + "..."


def _store_tool_observation(tool: str, obs: Any) -> str:
    if isinstance(obs, str):
        return _truncate(obs)
    if tool == "search_fulltext" and isinstance(obs, list):
        return _truncate(json.dumps(compact_search_hits_for_obs(obs), ensure_ascii=False))
    return _truncate(json.dumps(obs, ensure_ascii=False))


def _append_final_step(steps: list[dict], iteration: int, thought: str, answer: str) -> None:
    steps.append(
        {
            "iteration": iteration,
            "thought": thought,
            "tool": FINAL_ANSWER,
            "toolArgs": json.dumps({"answer": answer}, ensure_ascii=False),
            "observation": "",
        }
    )


def _prepare_session_context(
    session_id: str | None, question: str
) -> tuple[str, dict[str, Any], list[dict] | None]:
    """加载项目锁/债权主题，解析指代词，返回 (resolved_question, ctx, pre_steps)."""
    project_code: str | None = None
    project_name: str | None = None
    last_debt_target: str | None = None
    pre_steps: list[dict] | None = None

    if session_id:
        try:
            from app.services.memory import (
                load_context,
                load_debt_target_from_history,
                resolve_session_references,
            )

            ctx_row = load_context(session_id)
            if ctx_row:
                project_code = ctx_row.get("project_code")
                project_name = ctx_row.get("project_name")
                last_debt_target = ctx_row.get("last_debt_target")
            if not last_debt_target:
                last_debt_target = load_debt_target_from_history(session_id)
            from app.agent.react_helpers import extract_debt_anchor_from_question

            if not last_debt_target:
                last_debt_target = extract_debt_anchor_from_question(question)
            question_resolved = resolve_session_references(
                question, project_code, last_debt_target
            )
            if question_resolved != question:
                logger.info("会话指代解析: %r → %r", question, question_resolved)
                pre_steps = [
                    {
                        "iteration": 0,
                        "thought": (
                            f"识别到上下文指代, 锁定项目 {project_code} ({project_name})"
                            + (
                                f", 债权主题 {last_debt_target}"
                                if last_debt_target
                                else ""
                            )
                        ),
                        "tool": "_resolve_reference",
                        "toolArgs": json.dumps(
                            {
                                "original": question,
                                "resolved": question_resolved,
                                "lastDebtTarget": last_debt_target,
                            },
                            ensure_ascii=False,
                        ),
                        "observation": project_code or "",
                    }
                ]
            else:
                question_resolved = question
        except Exception as e:
            logger.debug("_prepare_session_context 失败: %s", e)
            question_resolved = question
    else:
        question_resolved = question

    ctx: dict[str, Any] = {
        "project_code": project_code,
        "project_name": project_name,
        "last_debt_target": last_debt_target,
        "interrupted": False,
    }
    return question_resolved, ctx, pre_steps


def _topic_debt_target_from_answer(answer: str) -> str | None:
    from app.agent.react_helpers import extract_debt_target_from_texts

    return extract_debt_target_from_texts([answer or ""])


def _persist_session_lock(
    session_id: str | None,
    final_answer: str | None,
    ctx: dict[str, Any],
    agent_sources: list[dict[str, Any]],
) -> None:
    if not session_id or not final_answer or ctx.get("interrupted"):
        return
    try:
        from app.services.memory import save_context

        project_code = None
        project_name = ""
        for src in reversed(agent_sources):
            if src.get("type") == "PROJECT" and src.get("id"):
                project_code = src["id"]
                project_name = src.get("title") or ""
                break
        if not project_code:
            project_code = ctx.get("project_code")
            project_name = ctx.get("project_name") or ""
        if not project_code:
            return
        debt = (
            ctx.get("resolved_debt_target")
            or _topic_debt_target_from_answer(final_answer)
            or ctx.get("last_debt_target")
        )
        save_context(
            session_id,
            project_code,
            project_name,
            last_debt_target=debt,
        )
    except Exception as e:
        logger.debug("_persist_session_lock 失败: %s", e)


def load_memory(session_id: str, limit: int = 10) -> list[dict[str, str]]:
    if not session_id:
        return []
    with db_cursor() as cur:
        cur.execute(
            f"""
            SELECT type, content FROM {MEMORY_TABLE}
            WHERE conversation_id = %s
            ORDER BY `timestamp` DESC
            LIMIT %s
            """,
            (session_id, limit),
        )
        rows = cur.fetchall() or []
    rows.reverse()
    return [{"role": r["type"], "content": r["content"]} for r in rows]


def append_memory(session_id: str, role: str, content: str) -> None:
    if not session_id:
        return
    with db_cursor() as cur:
        cur.execute(
            f"""
            INSERT INTO {MEMORY_TABLE} (conversation_id, content, type)
            VALUES (%s, %s, %s)
            """,
            (session_id, content[:8000], role),
        )


def build_user_prompt(question: str, session_id: str | None, steps: list[dict]) -> str:
    parts = [SYSTEM_PROMPT, "", f"用户问题: {question}"]
    if session_id:
        history = load_memory(session_id, 6)
        if history:
            parts.append("\n近期对话:")
            for h in history:
                parts.append(f"- {h['role']}: {h['content'][:500]}")
    if steps:
        parts.append("\n本轮已执行步骤:")
        for s in steps:
            parts.append(
                f"步骤{s['iteration']}: tool={s['tool']} "
                f"args={_truncate(s.get('toolArgs') or '', 120)} "
                f"obs={_truncate(s.get('observation') or '', 300)}"
            )
        append_step_hints(parts, question, steps)
    parts.append("\n请输出下一步 JSON（tool 或 FINAL_ANSWER）:")
    return "\n".join(parts)


def _log_llm_call(scenario: str, status: str, duration_ms: int, model: str = settings.glm_chat_model) -> None:
    """写入 llm_call_log 表。"""
    try:
        with db_cursor() as cur:
            cur.execute(
                "INSERT INTO llm_call_log (username, scenario, model, duration_ms, status) VALUES (%s, %s, %s, %s, %s)",
                ("qa-agent", scenario, model, duration_ms, status),
            )
    except Exception:
        logger.exception("写入 llm_call_log 失败")


def run_agent(question: str, session_id: str | None = None) -> dict[str, Any]:
    steps: list[dict[str, Any]] = []
    question_resolved, ctx, pre_steps = _prepare_session_context(session_id, question)
    if pre_steps:
        steps.extend(pre_steps)
    final_answer: str | None = None
    project_switch_hint: str | None = None
    confidence_badge: str | None = None
    agent_sources: list[dict[str, Any]] = []

    if session_id:
        append_memory(session_id, "user", question)

    for i in range(1, settings.agent_max_iterations + 1):
        user_prompt = build_user_prompt(question_resolved, session_id, steps)
        try:
            t0 = time.time()
            llm_raw = glm_client.chat(SYSTEM_PROMPT, user_prompt)
            ms = int((time.time() - t0) * 1000)
            _log_llm_call("AGENT_STEP", "OK", ms)
        except Exception as e:
            _log_llm_call("AGENT_STEP", "ERROR", 0)
            logger.exception("GLM call failed")
            final_answer = f"LLM 服务暂不可用: {e}"
            steps.append(
                {
                    "iteration": i,
                    "thought": "LLM 调用失败",
                    "tool": FINAL_ANSWER,
                    "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
                    "observation": "",
                }
            )
            break

        step = parse_agent_step(llm_raw, i)
        step = maybe_upgrade_step(
            step,
            steps,
            question_resolved,
            debt_target=ctx.get("last_debt_target"),
        )
        tool = step["tool"]

        if tool == FINAL_ANSWER:
            final_answer = final_answer_from_args(step["toolArgs"])
            steps.append(step)
            break

        obs: Any = None
        try:
            obs = dispatch_tool(tool, step["toolArgs"], ctx)
            step["observation"] = _store_tool_observation(tool, obs)
        except Exception as e:
            logger.exception("Tool %s failed", tool)
            step["observation"] = _truncate(f"ERROR: {e}")

        # v1.1: 从 find_project 结果提取切换 hint + 置信徽章 + 来源
        if tool == "find_project" and isinstance(obs, list):
            for item in obs[:1]:
                if isinstance(item, dict):
                    code = item.get("projectCode") or item.get("code")
                    if code:
                        ctx["project_code"] = code
                    sd = item.get("switchDecision")
                    if sd:
                        # 非 SAME_CONFIRMED 才设 hint
                        if sd != "SAME_CONFIRMED":
                            project_switch_hint = sd
                        # badge 根据 switchDecision 映射（对齐 Java populateV11Fields）
                        if sd in ("SAME_PROBABLY", "DIFFERENT_PROBABLY"):
                            confidence_badge = "AI_INFERRED"
                        elif sd == "UNCLEAR":
                            confidence_badge = "PENDING_REVIEW"
                        # SAME_CONFIRMED → badge 为 None（不显示）
                    agent_sources.append({
                        "type": "PROJECT",
                        "id": item.get("projectCode") or item.get("code", ""),
                        "title": item.get("projectName") or item.get("name", ""),
                    })
        # v1.1: search_fulltext → MATERIAL 来源
        if tool == "search_fulltext" and isinstance(obs, list):
            for item in obs[:3]:
                if isinstance(item, dict) and item.get("materialId"):
                    agent_sources.append({
                        "type": "MATERIAL",
                        "id": str(item.get("materialId", "")),
                        "title": item.get("materialTitle", ""),
                    })

        steps.append(step)

        if tool == "search_fulltext" and isinstance(obs, list) and obs:
            evidence_answer = try_finalize_evidence_from_search(
                question_resolved, steps[:-1], obs, ctx
            )
            if evidence_answer:
                from app.agent.react_helpers import extract_debt_target_from_texts

                debt = extract_debt_target_from_texts([evidence_answer])
                if debt:
                    ctx["resolved_debt_target"] = debt
                _append_final_step(
                    steps,
                    i + 1,
                    "search_fulltext 已命中材料，引擎根据摘录合成答案",
                    evidence_answer,
                )
                final_answer = evidence_answer
                break

        # ask_clarification 中断 ReAct 循环
        if ctx.get("interrupted") and isinstance(obs, dict):
            final_answer = json.dumps({"clarification": True, "question": obs.get("question", "")}, ensure_ascii=False)
            break

        # 死循环：连续相同 tool+args
        if len(steps) >= 2:
            a, b = steps[-2], steps[-1]
            if a["tool"] == b["tool"] and a.get("toolArgs") == b.get("toolArgs"):
                recovered, extra = try_recover_evidence_loop(
                    steps, question_resolved, i, ctx, dispatch_tool, _truncate
                )
                if not recovered:
                    recovered, extra = try_recover_proposal_count_loop(
                        steps, question_resolved, i, ctx, dispatch_tool, _truncate
                    )
                if not recovered:
                    recovered, extra = try_recover_material_count_loop(
                        steps, question_resolved, i, ctx, dispatch_tool, _truncate
                    )
                if recovered:
                    final_answer = recovered
                    steps.extend(extra)
                    break
                final_answer = "抱歉，多次尝试未找到匹配结果。请换更具体的关键词或联系档案管理员。"
                steps.append(
                    {
                        "iteration": i + 1,
                        "thought": "检测到重复工具调用，强制结束",
                        "tool": FINAL_ANSWER,
                        "toolArgs": json.dumps({"answer": final_answer}, ensure_ascii=False),
                        "observation": "",
                    }
                )
                break

    if final_answer is None:
        final_answer = "抱歉，我无法在限定步数内找到答案。请尝试更具体的提问。"

    if session_id:
        append_memory(session_id, "assistant", final_answer)

    _persist_session_lock(session_id, final_answer, ctx, agent_sources)

    return {
        "answer": final_answer,
        "agent_mode": True,
        "steps": steps,
        "tool_calls": len(steps),
        "project_switch_hint": project_switch_hint,
        "confidence_badge": confidence_badge,
        "agent_sources": agent_sources,
    }


# =============================================================================
# v1.2 流式接口 (SSE): run_agent_stream
# =============================================================================

import json as _json
from collections.abc import Iterator


def _sse_event(event: str, data: dict) -> str:
    """格式化为 SSE: event: <name>\\ndata: <json>\\n\\n"""
    return f"event: {event}\ndata: {_json.dumps(data, ensure_ascii=False)}\n\n"


def run_agent_stream(question: str, session_id: str | None = None) -> Iterator[str]:
    """SSE 流式 ReAct: yield event 字符串.

    事件类型 4 种:
    - step: ReAct 步完成 (含 tool/args/observation)
    - token: LLM 生成的 token
    - source: 来源命中 (PROJECT/MATERIAL/...)
    - done: 结束 (含 answer/tool_calls/agent_sources/...)

    与 run_agent() 共享核心逻辑 (项目锁 / 死循环 / 5 级链 / 降级), 区别:
    - LLM 调用 chat_stream() 逐 token yield
    - 每 ReAct 步完成 yield step 事件
    - 来源命中 yield source 事件
    - 最终 yield done 事件
    """
    steps: list[dict] = []
    agent_sources: list[dict] = []
    project_switch_hint: str | None = None
    confidence_badge: str | None = None
    final_answer: str | None = None
    answer_tokens_streamed = False
    question_resolved, ctx, pre_steps = _prepare_session_context(session_id, question)
    if pre_steps:
        steps.extend(pre_steps)
        for s in pre_steps:
            yield _sse_event("step", {
                "iteration": s["iteration"],
                "thought": s["thought"],
                "tool": s["tool"],
                "toolArgs": s["toolArgs"],
                "observation": s.get("observation", ""),
            })

    for i in range(1, settings.agent_max_iterations + 1):
        user_prompt = build_user_prompt(question_resolved, session_id, steps)

        # ReAct 中间步不向客户端泄漏 LLM 原始 JSON token
        llm_raw_chunks: list[str] = []
        try:
            for token in glm_client.chat_stream(SYSTEM_PROMPT, user_prompt):
                llm_raw_chunks.append(token)
        except Exception as e:
            logger.exception("GLM 流式调用失败")
            yield _sse_event("step", {
                "iteration": i,
                "thought": f"LLM 调用失败: {e}",
                "tool": FINAL_ANSWER,
                "toolArgs": "",
                "observation": "",
            })
            final_answer = "抱歉, 我暂时无法回答 (LLM 调用失败)。请稍后再试或联系运维。"
            break

        llm_raw = "".join(llm_raw_chunks)
        step = parse_agent_step(llm_raw, i)
        step = maybe_upgrade_step(
            step,
            steps,
            question_resolved,
            debt_target=ctx.get("last_debt_target"),
        )
        tool = step["tool"]

        if tool == FINAL_ANSWER:
            final_answer = final_answer_from_args(step["toolArgs"])
            steps.append(step)
            yield _sse_event("step", {
                "iteration": i,
                "thought": step["thought"],
                "tool": FINAL_ANSWER,
                "toolArgs": step["toolArgs"],
                "observation": "",
            })
            for ch in final_answer:
                yield _sse_event("token", {"iteration": i, "delta": ch})
            answer_tokens_streamed = True
            break

        # 调工具
        obs: Any = None
        try:
            obs = dispatch_tool(tool, step["toolArgs"], ctx)
            step["observation"] = _store_tool_observation(tool, obs)
        except Exception as e:
            logger.exception("Tool %s failed", tool)
            step["observation"] = _truncate(f"ERROR: {e}")

        # 来源提取 (与 run_agent() 同逻辑)
        new_sources = []
        if tool == "find_project" and isinstance(obs, list):
            for item in obs[:1]:
                if isinstance(item, dict):
                    code = item.get("projectCode") or item.get("code")
                    if code:
                        ctx["project_code"] = code
                    sd = item.get("switchDecision")
                    if sd:
                        if sd != "SAME_CONFIRMED":
                            project_switch_hint = sd
                        if sd in ("SAME_PROBABLY", "DIFFERENT_PROBABLY"):
                            confidence_badge = "AI_INFERRED"
                        elif sd == "UNCLEAR":
                            confidence_badge = "PENDING_REVIEW"
                    src = {
                        "type": "PROJECT",
                        "id": item.get("projectCode") or item.get("code", ""),
                        "title": item.get("projectName") or item.get("name", ""),
                    }
                    agent_sources.append(src)
                    new_sources.append(src)
        elif tool == "search_fulltext" and isinstance(obs, list):
            for item in obs[:3]:
                if isinstance(item, dict) and item.get("materialId"):
                    src = {
                        "type": "MATERIAL",
                        "id": str(item.get("materialId", "")),
                        "title": item.get("materialTitle", ""),
                    }
                    agent_sources.append(src)
                    new_sources.append(src)

        # yield step 事件
        steps.append(step)
        yield _sse_event("step", {
            "iteration": i,
            "thought": step["thought"],
            "tool": tool,
            "toolArgs": step["toolArgs"],
            "observation": step["observation"],
        })
        # yield source 事件
        for src in new_sources:
            yield _sse_event("source", src)

        if tool == "search_fulltext" and isinstance(obs, list) and obs:
            evidence_answer = try_finalize_evidence_from_search(
                question_resolved, steps, obs, ctx
            )
            if evidence_answer:
                from app.agent.react_helpers import extract_debt_target_from_texts

                debt = extract_debt_target_from_texts([evidence_answer])
                if debt:
                    ctx["resolved_debt_target"] = debt
                final_step = {
                    "iteration": i + 1,
                    "thought": "search_fulltext 已命中材料，引擎根据摘录合成答案",
                    "tool": FINAL_ANSWER,
                    "toolArgs": _json.dumps({"answer": evidence_answer}, ensure_ascii=False),
                    "observation": "",
                }
                steps.append(final_step)
                yield _sse_event("step", final_step)
                final_answer = evidence_answer
                for ch in evidence_answer:
                    yield _sse_event("token", {"iteration": i + 1, "delta": ch})
                answer_tokens_streamed = True
                break

        # ask_clarification 中断
        if ctx.get("interrupted") and isinstance(obs, dict):
            final_answer = _json.dumps({"clarification": True, "question": obs.get("question", "")}, ensure_ascii=False)
            break

        # 死循环: 连续相同 tool+args
        if len(steps) >= 2:
            a, b = steps[-2], steps[-1]
            if a["tool"] == b["tool"] and a.get("toolArgs") == b.get("toolArgs"):
                recovered, extra = try_recover_evidence_loop(
                    steps, question_resolved, i, ctx, dispatch_tool, _truncate
                )
                if not recovered:
                    recovered, extra = try_recover_proposal_count_loop(
                        steps, question_resolved, i, ctx, dispatch_tool, _truncate
                    )
                if not recovered:
                    recovered, extra = try_recover_material_count_loop(
                        steps, question_resolved, i, ctx, dispatch_tool, _truncate
                    )
                if recovered:
                    final_answer = recovered
                    steps.extend(extra)
                    for s in extra:
                        yield _sse_event("step", {
                            "iteration": s["iteration"],
                            "thought": s["thought"],
                            "tool": s["tool"],
                            "toolArgs": s["toolArgs"],
                            "observation": s.get("observation", ""),
                        })
                    for ch in final_answer:
                        yield _sse_event("token", {"iteration": i, "delta": ch})
                    answer_tokens_streamed = True
                    break
                final_answer = "抱歉，多次尝试未找到匹配结果。请换更具体的关键词或联系档案管理员。"
                yield _sse_event("step", {
                    "iteration": i + 1,
                    "thought": "检测到重复工具调用，强制结束",
                    "tool": FINAL_ANSWER,
                    "toolArgs": _json.dumps({"answer": final_answer}, ensure_ascii=False),
                    "observation": "",
                })
                for ch in final_answer:
                    yield _sse_event("token", {"iteration": i + 1, "delta": ch})
                answer_tokens_streamed = True
                break

    if final_answer is None:
        final_answer = "抱歉，我无法在限定步数内找到答案。请尝试更具体的提问。"

    if final_answer and not answer_tokens_streamed:
        for ch in final_answer:
            yield _sse_event("token", {"iteration": settings.agent_max_iterations, "delta": ch})

    if session_id:
        append_memory(session_id, "assistant", final_answer)

    _persist_session_lock(session_id, final_answer, ctx, agent_sources)

    # yield done 事件
    yield _sse_event("done", {
        "answer": final_answer,
        "agent_mode": True,
        "steps": steps,
        "tool_calls": len(steps),
        "project_switch_hint": project_switch_hint,
        "confidence_badge": confidence_badge,
        "agent_sources": agent_sources,
    })
