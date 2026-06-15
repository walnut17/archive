"""get_project_business_data: 项目业务汇总 (需已知 projectCode)."""

import logging
from typing import Any

from app.db.connection import db_cursor

logger = logging.getLogger(__name__)


def run(args: dict[str, Any], ctx: dict[str, Any]) -> dict[str, Any]:
    project_code = args.get("projectCode", "")
    if not project_code:
        return {"error": "缺少 projectCode 参数"}

    with db_cursor() as cur:
        cur.execute(
            """SELECT p.id, p.code, p.name, p.status, p.amount_wan,
                      p.customer_name, p.category,
                      (SELECT COUNT(*) FROM todo t WHERE t.project_id = p.id AND t.status = 'pending') AS todo_count,
                      (SELECT COUNT(m.id) FROM material m
                       JOIN proposal pr ON pr.id = m.proposal_id
                       WHERE pr.project_id = p.id AND m.deleted_at IS NULL) AS material_count
               FROM project p WHERE p.code = %s AND p.deleted_at IS NULL""",
            (project_code,),
        )
        row = cur.fetchone()
        if not row:
            return {"error": f"项目 {project_code} 不存在"}

        return {
            "projectCode": row["code"],
            "projectName": row["name"],
            "status": row["status"],
            "amountWan": float(row["amount_wan"]) if row["amount_wan"] else 0,
            "customerName": row["customer_name"],
            "category": row["category"],
            "todoCount": row["todo_count"],
            "materialCount": int(row["material_count"] or 0),
        }
