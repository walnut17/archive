import json
import logging
import re
import time
from typing import Any

from app.agent.parser import FINAL_ANSWER, final_answer_from_args, parse_agent_step
from app.agent.prompts import SYSTEM_PROMPT
from app.agent.react_helpers import (
    append_step_hints,
    maybe_upgrade_step,
    try_recover_material_count_loop,
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
    ctx: dict[str, Any] = {"project_code": None}
    final_answer: str | None = None
    project_switch_hint: str | None = None
    confidence_badge: str | None = None
    agent_sources: list[dict[str, Any]] = []

    if session_id:
        append_memory(session_id, "user", question)

    for i in range(1, settings.agent_max_iterations + 1):
        user_prompt = build_user_prompt(question, session_id, steps)
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
        step = maybe_upgrade_step(step, steps, question)
        tool = step["tool"]

        if tool == FINAL_ANSWER:
            final_answer = final_answer_from_args(step["toolArgs"])
            steps.append(step)
            break

        obs: Any = None
        try:
            obs = dispatch_tool(tool, step["toolArgs"], ctx)
            step["observation"] = _truncate(obs if isinstance(obs, str) else json.dumps(obs, ensure_ascii=False))
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

        # ask_clarification 中断 ReAct 循环
        if ctx.get("interrupted") and isinstance(obs, dict):
            final_answer = json.dumps({"clarification": True, "question": obs.get("question", "")}, ensure_ascii=False)
            break

        # 死循环：连续相同 tool+args
        if len(steps) >= 2:
            a, b = steps[-2], steps[-1]
            if a["tool"] == b["tool"] and a.get("toolArgs") == b.get("toolArgs"):
                recovered, extra = try_recover_material_count_loop(
                    steps, question, i, ctx, dispatch_tool, _truncate
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
    project_code: str | None = None
    project_name: str | None = None
    if session_id:
        try:
            from app.services.memory import load_context
            ctx_row = load_context(session_id)
            if ctx_row:
                project_code = ctx_row.get("project_code")
                project_name = ctx_row.get("project_name")
        except Exception as e:
            logger.debug("load_context 失败: %s", e)

    # v1.2: 指代词解析
    question_resolved = question
    if session_id and project_code:
        try:
            from app.services.memory import resolve_project_reference
            question_resolved = resolve_project_reference(question, project_code)
            if question_resolved != question:
                logger.info("指代词解析: %r → %r", question, question_resolved)
                # step 事件前置: 让前端看到指代词解析过程
                yield _sse_event("step", {
                    "iteration": 0,
                    "thought": f"识别到指代词, 自动锁定项目 {project_code} ({project_name})",
                    "tool": "_resolve_reference",
                    "toolArgs": _json.dumps({"original": question, "resolved": question_resolved}, ensure_ascii=False),
                    "observation": project_code,
                })
        except Exception as e:
            logger.debug("resolve_project_reference 失败: %s", e)

    ctx: dict[str, Any] = {
        "project_code": project_code,
        "project_name": project_name,
        "interrupted": False,
    }

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
        step = maybe_upgrade_step(step, steps, question_resolved)
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
            step["observation"] = _truncate(obs if isinstance(obs, str) else _json.dumps(obs, ensure_ascii=False))
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

        # ask_clarification 中断
        if ctx.get("interrupted") and isinstance(obs, dict):
            final_answer = _json.dumps({"clarification": True, "question": obs.get("question", "")}, ensure_ascii=False)
            break

        # 死循环: 连续相同 tool+args
        if len(steps) >= 2:
            a, b = steps[-2], steps[-1]
            if a["tool"] == b["tool"] and a.get("toolArgs") == b.get("toolArgs"):
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

    # v1.2: 项目锁写回
    if session_id and final_answer and not ctx.get("interrupted"):
        try:
            from app.services.memory import save_context
            # 找最后一次 find_project 命中的 PROJECT
            for src in reversed(agent_sources):
                if src.get("type") == "PROJECT" and src.get("id"):
                    save_context(session_id, src["id"], src.get("title", ""))
                    break
        except Exception as e:
            logger.debug("save_context 失败: %s", e)

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
