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
                return [_fmt(row, 1.0)]

        # 2) FULLTEXT / LIKE
        cur.execute(
            """
            SELECT id, code, name, status, amount_wan AS amountWan,
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

    return [_fmt(r, float(r.get("score") or 0.5)) for r in rows]


def _fmt(row: dict, conf: float) -> dict:
    return {
        "id": row["id"],
        "code": row["code"],
        "name": row["name"],
        "status": row.get("status"),
        "amountWan": row.get("amountWan"),
        "confidence": conf,
    }
