from typing import Any

from app.db.connection import db_cursor


def run(args: dict[str, Any], ctx: dict[str, Any]) -> list[dict[str, Any]]:
    query = (args.get("query") or "").strip()
    top_n = int(args.get("topN") or 3)
    if not query:
        return []

    with db_cursor() as cur:
        # 1) 精确编号
        if query.upper().startswith("PRJ-"):
            cur.execute(
                """
                SELECT id, code, name, status, amount_wan AS amountWan
                FROM project WHERE code = %s AND deleted_at IS NULL LIMIT 1
                """,
                (query.upper(),),
            )
            row = cur.fetchone()
            if row:
                return [_fmt(row, 1.0, ctx)]

        # 2) FULLTEXT / LIKE
        cur.execute(
            """
            SELECT id, code, name, status, amount_wan AS amountWan, customer_name,
                   MATCH(name, customer_name) AGAINST (%s IN NATURAL LANGUAGE MODE) AS score
            FROM project
            WHERE deleted_at IS NULL
              AND (MATCH(name, customer_name) AGAINST (%s IN NATURAL LANGUAGE MODE)
                   OR name LIKE %s OR code LIKE %s)
            ORDER BY score DESC
            LIMIT %s
            """,
            (query, query, f"%{query}%", f"%{query}%", top_n),
        )
        rows = cur.fetchall() or []

    return [_fmt(r, float(r.get("score") or 0.5), ctx) for r in rows]


def _fmt(row: dict, conf: float, ctx: dict[str, Any] | None = None) -> dict:
    """格式化项目行，包含 Java FindProjectTool 兼容字段与 5 级隐式切换."""
    code = row["code"]
    name = row["name"]

    # 5 级隐式切换判定（对齐 Java applyImplicitSwitchRule + SwitchDecision）
    locked = (ctx or {}).get("project_code")
    switch_decision = "SAME_CONFIRMED"  # 默认
    if locked and locked != code:
        if conf >= 0.7:
            switch_decision = "DIFFERENT_PROBABLY"
        else:
            switch_decision = "UNCLEAR"
    elif locked == code:
        if conf >= 0.95:
            switch_decision = "SAME_CONFIRMED"
        elif conf >= 0.7:
            switch_decision = "SAME_PROBABLY"
        else:
            switch_decision = "UNCLEAR"
    else:
        # 无锁定：高置信自动锁定
        if conf >= 0.7 and ctx is not None:
            switch_decision = "SAME_CONFIRMED"
            ctx["project_code"] = code

    return {
        "id": row["id"],
        "code": code,
        "name": name,
        "projectCode": code,
        "projectName": name,
        "customerName": row.get("customer_name"),
        "status": row.get("status"),
        "amountWan": row.get("amountWan"),
        "confidence": conf,
        "switchDecision": switch_decision,
    }
