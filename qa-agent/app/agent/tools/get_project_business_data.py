"""get_project_business_data: 项目业务汇总 (需已知 projectCode)."""

import logging
from typing import Any

from app.db.connection import db_cursor

logger = logging.getLogger(__name__)

_MAX_PROPOSALS = 10
_MAINTENANCE_TYPES = frozenset({"维护", "材料维护"})


def run(args: dict[str, Any], ctx: dict[str, Any]) -> dict[str, Any]:
    project_code = args.get("projectCode", "")
    if not project_code:
        return {"error": "缺少 projectCode 参数"}

    with db_cursor() as cur:
        cur.execute(
            """SELECT p.id, p.code, p.name, p.status, p.amount_wan,
                      p.customer_name, p.category,
                      (SELECT COUNT(*) FROM todo t
                       WHERE t.project_id = p.id AND t.status = 'pending') AS todo_count,
                      (SELECT COUNT(m.id) FROM material m
                       JOIN proposal pr ON pr.id = m.proposal_id
                       WHERE pr.project_id = p.id AND m.deleted_at IS NULL) AS material_count,
                      (SELECT COUNT(*) FROM proposal pr
                       WHERE pr.project_id = p.id AND pr.deleted_at IS NULL
                         AND pr.status <> '草稿'
                         AND (pr.type IS NULL OR pr.type NOT IN ('维护', '材料维护'))) AS committee_count,
                      (SELECT COUNT(*) FROM proposal pr
                       WHERE pr.project_id = p.id AND pr.deleted_at IS NULL
                         AND pr.type IN ('维护', '材料维护')) AS maintenance_count
               FROM project p WHERE p.code = %s AND p.deleted_at IS NULL""",
            (project_code,),
        )
        row = cur.fetchone()
        if not row:
            return {"error": f"项目 {project_code} 不存在"}

        project_id = row["id"]
        cur.execute(
            """SELECT code, title, type, status
               FROM proposal
               WHERE project_id = %s AND deleted_at IS NULL
                 AND status <> '草稿'
                 AND (type IS NULL OR type NOT IN ('维护', '材料维护'))
               ORDER BY id ASC
               LIMIT %s""",
            (project_id, _MAX_PROPOSALS),
        )
        proposals = [
            {
                "code": r["code"],
                "title": r["title"],
                "type": r.get("type"),
                "status": r.get("status"),
            }
            for r in (cur.fetchall() or [])
        ]

        committee_count = int(row["committee_count"] or 0)
        maintenance_count = int(row["maintenance_count"] or 0)

        logger.info("get_project_business_data: project=%s committee=%d maintenance=%d",
                     project_code, committee_count, maintenance_count)

        return {
            "projectCode": row["code"],
            "projectName": row["name"],
            "status": row["status"],
            "amountWan": float(row["amount_wan"]) if row["amount_wan"] else 0,
            "customerName": row["customer_name"],
            "category": row["category"],
            "todoCount": row["todo_count"],
            "materialCount": int(row["material_count"] or 0),
            "proposalCount": committee_count,  # deprecated alias = committeeProposalCount
            "committeeProposalCount": committee_count,
            "maintenanceBundleCount": maintenance_count,
            "proposals": proposals,
        }
