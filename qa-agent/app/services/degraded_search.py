"""v1.2: 降级搜索 - GLM 失败时回 FULLTEXT 检索 + 模板答案.

当 GLM 不可用时 (key 未配 / 配额 / 网络), 端点返 200 + 简单模板答案,
不让用户看到 500.
"""
import logging
from typing import Any

from app.db.connection import db_cursor

logger = logging.getLogger(__name__)


def degraded_search_and_answer(question: str) -> dict[str, Any]:
    """GLM 失败时: 走 FULLTEXT + 模板答案.

    返回: {
        "answer": "模板拼的答案",
        "degraded": True,
        "confidence_badge": "DEGRADED",
        "sources": [SearchResult 列表],
        "tool_calls": 1,
        "steps": [{"tool": "search_fulltext", ...}],
    }
    """
    try:
        with db_cursor() as cur:
            # FULLTEXT 检索 + 项目信息
            cur.execute(
                """
                SELECT
                    p.id, p.code, p.name, p.status, p.amount_wan, p.customer_name,
                    (SELECT COUNT(*) FROM material_version mv WHERE mv.project_id = p.id AND mv.deleted_at IS NULL) AS material_count,
                    (SELECT MAX(t.due_date) FROM todo t WHERE t.project_id = p.id AND t.status != 'DONE') AS next_due,
                    p.updated_at,
                    MATCH(p.name, p.customer_name) AGAINST (%s IN NATURAL LANGUAGE MODE) AS score
                FROM project p
                WHERE p.deleted_at IS NULL
                  AND (MATCH(p.name, p.customer_name) AGAINST (%s IN NATURAL LANGUAGE MODE)
                       OR p.name LIKE %s OR p.code LIKE %s)
                ORDER BY score DESC
                LIMIT 3
                """,
                (question, question, f"%{question}%", f"%{question}%"),
            )
            rows = cur.fetchall() or []
    except Exception as e:
        logger.warning("degraded_search DB query failed: %s", e)
        return {
            "answer": "抱歉, 我暂时无法回答 (数据库查询失败)。请稍后重试或联系运维。",
            "degraded": True,
            "confidence_badge": "DEGRADED",
            "sources": [],
            "tool_calls": 0,
            "steps": [],
        }

    if not rows:
        return {
            "answer": f"抱歉, 我暂时无法完整回答您的问题 (LLM 暂时不可用, 降级检索也未找到匹配项目)。建议: 1) 换个更具体的项目名 2) 联系运维检查 GLM 服务。",
            "degraded": True,
            "confidence_badge": "DEGRADED",
            "sources": [],
            "tool_calls": 1,
            "steps": [{
                "iteration": 1,
                "thought": "LLM 不可用, 走降级 FULLTEXT 检索",
                "tool": "search_fulltext",
                "toolArgs": f'{{"query": "{question[:50]}", "topN": 3}}',
                "observation": "未找到匹配项目",
            }],
        }

    # 模板拼答案
    top = rows[0]
    project_name = top.get("name") or top.get("customer_name") or "未知项目"
    project_code = top.get("code") or ""
    material_count = top.get("material_count", 0)
    next_due = top.get("next_due")
    amount_wan = top.get("amount_wan")

    answer_lines = [
        f"⚠️ 当前 LLM 服务暂时不可用, 以下是基于降级搜索的简要信息:",
        f"",
        f"**项目**: {project_name} ({project_code})",
        f"**状态**: {top.get('status', '未知')}",
    ]
    if amount_wan is not None:
        answer_lines.append(f"**金额**: {amount_wan} 万元")
    answer_lines.append(f"**材料数**: {material_count} 份")
    if next_due:
        answer_lines.append(f"**最近待办**: {next_due}")
    answer_lines.append("")
    answer_lines.append("(完整分析需要 LLM 服务恢复, 请稍后重试)")

    return {
        "answer": "\n".join(answer_lines),
        "degraded": True,
        "confidence_badge": "DEGRADED",
        "sources": [_row_to_search_result(r) for r in rows],
        "tool_calls": 1,
        "steps": [{
            "iteration": 1,
            "thought": "LLM 不可用, 走降级 FULLTEXT 检索",
            "tool": "search_fulltext",
            "toolArgs": f'{{"query": "{question[:50]}", "topN": 3}}',
            "observation": f"找到 {len(rows)} 个匹配项目 (降级模式)",
        }],
    }


def _row_to_search_result(row: dict) -> dict:
    """row → SearchResult 兼容格式."""
    return {
        "versionId": 0,
        "materialId": 0,
        "materialTitle": row.get("name", ""),
        "versionNo": 0,
        "originalFilename": "",
        "projectCode": row.get("code", ""),
        "projectName": row.get("name", ""),
        "proposalCode": "",
        "proposalTitle": "",
        "snippet": f"降级模式: 项目 {row.get('code', '')} ({row.get('name', '')})",
        "score": float(row.get("score") or 0.5),
    }
