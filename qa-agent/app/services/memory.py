"""v1.2: 多轮项目记忆穿透 (chat_session_context + 指代词解析).

- chat_session_context 表存 session_id → project_code 锁
- 指代词检测: 「它/那/这个项目/剩余金额/金额多少」+ 已有 project_code → 注入
- 完整 v2 留 (客户/业务方人名映射)
"""
import logging
import re
from typing import Any

from app.db.connection import db_cursor

logger = logging.getLogger(__name__)

# 指代词/上下文敏感词: 出现这些词 + 已有 project_code → 自动锁定
_REFERENCE_PATTERNS = [
    re.compile(r"它|那个项目?|这笔|这项目|该项目|它项目"),
    re.compile(r"剩余金额|金额多少|还剩多少|余额多少|还款"),
    re.compile(r"当前|现在|当前状态|最新进展|进度"),
    re.compile(r"材料?|附件|尽调|议案|决议|抵押物|贷后|结清"),
]


def _detect_reference(question: str) -> bool:
    """检测问题是否含指代词/上下文词. 含任一即 True."""
    if re.search(r"\bPRJ-\d{4}-\d+\b", question, re.IGNORECASE):
        return False
    for p in _REFERENCE_PATTERNS:
        if p.search(question):
            return True
    return False


def load_context(session_id: str) -> dict[str, Any] | None:
    """从 chat_session_context 读 session 锁定的项目."""
    if not session_id:
        return None
    try:
        with db_cursor() as cur:
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
                "last_tool": row.get("last_tool"),
                "last_confidence": row.get("last_confidence"),
                "updated_at": row.get("updated_at"),
            }
    except Exception as e:
        # 表不存在 (迁移未跑) — 静默降级
        logger.debug("load_context 失败 (表可能不存在): %s", e)
        return None


def save_context(session_id: str, project_code: str, project_name: str = "",
                 last_tool: str = "find_project", last_confidence: str = "SAME_CONFIRMED") -> None:
    """写/更新 session 锁定的项目."""
    if not session_id or not project_code:
        return
    try:
        with db_cursor() as cur:
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
    """检测问题含指代词 → 在 question 前注入 project_code hint.

    例如:
        question = "剩余金额多少?", project_code = "PRJ-2026-001"
        返回 "PRJ-2026-001 剩余金额多少?"

    不会破坏 question 原有内容, 只是在前面加 hint 增强 find_project 命中率.
    """
    if not project_code:
        return question
    if not _detect_reference(question):
        return question
    if project_code in question:
        return question
    return f"{project_code} {question}"


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
