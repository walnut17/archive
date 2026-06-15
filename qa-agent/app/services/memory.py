"""v1.2: 多轮项目记忆穿透 (chat_session_context + 指代词解析).

- chat_session_context 表存 session_id → project_code 锁
- 指代词检测: 「它/那/这个项目/剩余金额/金额多少」+ 已有 project_code → 注入
- 债权追问: 「这个债权/这笔债权」+ 上轮债权标的 → 注入检索锚点
- 完整 v2 留 (客户/业务方人名映射)
"""
import logging
import re
from typing import Any

from app.db.connection import db_cursor

logger = logging.getLogger(__name__)

MEMORY_TABLE = "spring_ai_chat_memory"

# 指代词/上下文敏感词: 出现这些词 + 已有 project_code → 自动锁定
_REFERENCE_PATTERNS = [
    re.compile(r"它|那个项目?|这笔|这项目|该项目|它项目"),
    re.compile(r"剩余金额|金额多少|还剩多少|余额多少|还款"),
    re.compile(r"当前|现在|当前状态|最新进展|进度"),
    re.compile(r"材料?|附件|尽调|议案|决议|抵押物|贷后|结清"),
    re.compile(r"这个债权|这笔债权|该债权|上述债权|此债权|该笔债权"),
]

_DEBT_REFERENCE_RE = re.compile(r"这个债权|这笔债权|该债权|上述债权|此债权|该笔债权")


def _detect_reference(question: str) -> bool:
    """检测问题是否含指代词/上下文词. 含任一即 True."""
    if re.search(r"\bPRJ-\d{4}-\d+\b", question, re.IGNORECASE):
        return False
    for p in _REFERENCE_PATTERNS:
        if p.search(question):
            return True
    return False


def detect_debt_reference(question: str) -> bool:
    return bool(_DEBT_REFERENCE_RE.search(question or ""))


def _debt_search_anchor(debt_target: str) -> str:
    """从完整债权标的提炼全文检索短锚点."""
    text = (debt_target or "").strip()
    if not text:
        return ""
    if "岭兜建材二厂" in text:
        return "岭兜建材二厂"
    m = re.search(r"([^，。；\n]{2,30}债权)", text)
    return m.group(1) if m else text[:24]


def load_context(session_id: str) -> dict[str, Any] | None:
    """从 chat_session_context 读 session 锁定的项目."""
    if not session_id:
        return None
    try:
        with db_cursor() as cur:
            try:
                cur.execute(
                    """
                    SELECT project_code, project_name, last_debt_target,
                           last_tool, last_confidence, updated_at
                    FROM chat_session_context WHERE session_id = %s
                    """,
                    (session_id,),
                )
            except Exception:
                cur.execute(
                    """
                    SELECT project_code, project_name, last_tool, last_confidence, updated_at
                    FROM chat_session_context WHERE session_id = %s
                    """,
                    (session_id,),
                )
            row = cur.fetchone()
            if not row:
                return None
            return {
                "project_code": row.get("project_code"),
                "project_name": row.get("project_name"),
                "last_debt_target": row.get("last_debt_target"),
                "last_tool": row.get("last_tool"),
                "last_confidence": row.get("last_confidence"),
                "updated_at": row.get("updated_at"),
            }
    except Exception as e:
        logger.debug("load_context 失败 (表可能不存在): %s", e)
        return None


def load_debt_target_from_history(session_id: str, limit: int = 6) -> str | None:
    """从近期 assistant 回复推断上一轮讨论的债权标的."""
    if not session_id:
        return None
    try:
        from app.agent.react_helpers import extract_debt_target_from_texts

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
        for row in rows:
            if (row.get("type") or "").lower() != "assistant":
                continue
            found = extract_debt_target_from_texts([row.get("content") or ""])
            if found:
                return found
    except Exception as e:
        logger.debug("load_debt_target_from_history 失败: %s", e)
    return None


def save_context(
    session_id: str,
    project_code: str,
    project_name: str = "",
    last_tool: str = "find_project",
    last_confidence: str = "SAME_CONFIRMED",
    last_debt_target: str | None = None,
) -> None:
    """写/更新 session 锁定的项目与可选债权主题."""
    if not session_id or not project_code:
        return
    try:
        with db_cursor() as cur:
            if last_debt_target:
                try:
                    cur.execute(
                        """
                        INSERT INTO chat_session_context
                            (session_id, project_code, project_name, last_debt_target,
                             last_tool, last_confidence)
                        VALUES (%s, %s, %s, %s, %s, %s)
                        ON DUPLICATE KEY UPDATE
                            project_code = VALUES(project_code),
                            project_name = VALUES(project_name),
                            last_debt_target = COALESCE(VALUES(last_debt_target), last_debt_target),
                            last_tool = VALUES(last_tool),
                            last_confidence = VALUES(last_confidence),
                            updated_at = CURRENT_TIMESTAMP
                        """,
                        (
                            session_id,
                            project_code,
                            project_name,
                            last_debt_target,
                            last_tool,
                            last_confidence,
                        ),
                    )
                    return
                except Exception:
                    logger.debug("save_context: last_debt_target 列不可用, 降级写入")
            cur.execute(
                """
                INSERT INTO chat_session_context
                    (session_id, project_code, project_name, last_tool, last_confidence)
                VALUES (%s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    project_code = VALUES(project_code),
                    project_name = VALUES(project_name),
                    last_tool = VALUES(last_tool),
                    last_confidence = VALUES(last_confidence),
                    updated_at = CURRENT_TIMESTAMP
                """,
                (session_id, project_code, project_name, last_tool, last_confidence),
            )
    except Exception as e:
        logger.warning("save_context 失败: %s", e)


def resolve_project_reference(question: str, project_code: str) -> str:
    """检测问题含指代词 → 在 question 前注入 project_code hint."""
    if not project_code:
        return question
    if not _detect_reference(question):
        return question
    if project_code in question:
        return question
    return f"{project_code} {question}"


def resolve_debt_reference(question: str, debt_target: str | None) -> str:
    """追问「这个债权」或抵押物细问时注入债权检索锚点."""
    if not debt_target:
        return question
    anchor = _debt_search_anchor(debt_target)
    if not anchor or anchor in question or debt_target in question:
        return question
    from app.agent.react_helpers import (
        extract_debt_anchor_from_question,
        question_needs_collateral_evidence,
    )

    q = question or ""
    needs_debt_ctx = (
        detect_debt_reference(q)
        or extract_debt_anchor_from_question(q)
        or question_needs_collateral_evidence(q)
    )
    if needs_debt_ctx:
        return f"{anchor} {question}"
    return question


def resolve_session_references(
    question: str,
    project_code: str | None = None,
    last_debt_target: str | None = None,
) -> str:
    """组合项目锁 + 债权主题解析（含问句内嵌债权名）."""
    from app.agent.react_helpers import extract_debt_anchor_from_question

    q = question or ""
    debt = last_debt_target or extract_debt_anchor_from_question(q)
    if project_code:
        q = resolve_project_reference(q, project_code)
    if debt:
        q = resolve_debt_reference(q, debt)
    return q


def clear_context(session_id: str) -> None:
    """用户主动换项目时调用, 清掉 session 锁."""
    if not session_id:
        return
    try:
        with db_cursor() as cur:
            cur.execute(
                "DELETE FROM chat_session_context WHERE session_id = %s",
                (session_id,),
            )
    except Exception as e:
        logger.warning("clear_context 失败: %s", e)
