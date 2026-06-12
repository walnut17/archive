from typing import Any

from app.db.connection import db_cursor

ALLOWED_TABLES = frozenset(
    {"project", "proposal", "material", "material_version", "todo", "project_fact"}
)
ALLOWED_OPS = frozenset({"=", "!=", ">", "<", ">=", "<=", "LIKE", "IN"})
MAX_LIMIT = 1000


def run(args: dict[str, Any], ctx: dict[str, Any]) -> list[dict[str, Any]]:
    table = (args.get("table") or "").strip()
    if table not in ALLOWED_TABLES:
        raise ValueError(f"表不在白名单: {table}")

    columns = args.get("columns") or ["*"]
    if not isinstance(columns, list) or not columns:
        columns = ["*"]
    col_sql = ", ".join(columns) if columns != ["*"] else "*"

    limit = min(int(args.get("limit") or 50), MAX_LIMIT)
    where = args.get("where") or []
    clauses: list[str] = []
    params: list[Any] = []
    for w in where:
        field = w.get("column") or w.get("field")
        op = w.get("operator") or w.get("op", "=")
        value = w.get("value")
        if not field or op not in ALLOWED_OPS:
            continue
        if op == "IN" and isinstance(value, list):
            placeholders = ", ".join(["%s"] * len(value))
            clauses.append(f"`{field}` IN ({placeholders})")
            params.extend(value)
        else:
            clauses.append(f"`{field}` {op} %s")
            params.append(value)

    sql = f"SELECT {col_sql} FROM `{table}`"
    if clauses:
        sql += " WHERE " + " AND ".join(clauses)
    sql += f" LIMIT {limit}"

    with db_cursor() as cur:
        cur.execute(sql, params)
        return list(cur.fetchall() or [])
