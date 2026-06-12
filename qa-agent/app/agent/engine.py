import json
import logging
import re
from typing import Any

from app.agent.parser import FINAL_ANSWER, final_answer_from_args, parse_agent_step
from app.agent.prompts import SYSTEM_PROMPT
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
                f"步骤{s['iteration']}: tool={s['tool']} obs={_truncate(s.get('observation') or '', 300)}"
            )
    parts.append("\n请输出下一步 JSON（tool 或 FINAL_ANSWER）:")
    return "\n".join(parts)


def run_agent(question: str, session_id: str | None = None) -> dict[str, Any]:
    steps: list[dict[str, Any]] = []
    ctx: dict[str, Any] = {"project_code": None}
    final_answer: str | None = None

    if session_id:
        append_memory(session_id, "user", question)

    for i in range(1, settings.agent_max_iterations + 1):
        user_prompt = build_user_prompt(question, session_id, steps)
        try:
            llm_raw = glm_client.chat(SYSTEM_PROMPT, user_prompt)
        except Exception as e:
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
        tool = step["tool"]

        if tool == FINAL_ANSWER:
            final_answer = final_answer_from_args(step["toolArgs"])
            steps.append(step)
            break

        try:
            obs = dispatch_tool(tool, step["toolArgs"], ctx)
            step["observation"] = _truncate(obs if isinstance(obs, str) else json.dumps(obs, ensure_ascii=False))
        except Exception as e:
            logger.exception("Tool %s failed", tool)
            step["observation"] = _truncate(f"ERROR: {e}")

        steps.append(step)

        # 死循环：连续相同 tool+args
        if len(steps) >= 2:
            a, b = steps[-2], steps[-1]
            if a["tool"] == b["tool"] and a.get("toolArgs") == b.get("toolArgs"):
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
        "project_switch_hint": None,
        "confidence_badge": None,
        "agent_sources": [],
    }
