from typing import Any

from app.config import settings
from app.db.connection import db_cursor

ALLOWED_TABLES = frozenset(
    {"project", "proposal", "material", "material_version", "todo", "project_fact"}
)
ALLOWED_OPS = frozenset({"=", "!=", ">", "<", ">=", "<=", "LIKE", "IN"})
ALLOWED_ORDER_DIRS = frozenset({"ASC", "DESC"})
MAX_LIMIT = 1000

_COLUMNS_WHITELIST: dict[str, set[str]] = {
    "project": {"id", "code", "name", "status", "amount_wan", "created_at", "updated_at"},
    "proposal": {
        "id",
        "code",
        "title",
        "status",
        "type",
        "project_id",
        "reviewed_at",
        "created_at",
    },
    "material": {"id", "name", "type", "proposal_id", "created_at"},
    "material_version": {"id", "material_id", "version_no", "created_at"},
    "todo": {"id", "title", "status", "due_date", "project_id", "created_at"},
    "project_fact": {"id", "project_id", "fact_type", "occurred_at"},
}

_ORDER_COLUMNS_WHITELIST = _COLUMNS_WHITELIST


def _resolve_project_id(project_code: str) -> int | None:
    with db_cursor() as cur:
        cur.execute(
            "SELECT id FROM project WHERE code = %s AND deleted_at IS NULL LIMIT 1",
            (project_code,),
        )
        row = cur.fetchone()
        return int(row["id"]) if row else None


def run(args: dict[str, Any], ctx: dict[str, Any]) -> list[dict[str, Any]]:
    table = (args.get("table") or "").strip()
    if table not in ALLOWED_TABLES:
        raise ValueError(f"表不在白名单: {table}")

    columns = args.get("columns") or ["*"]
    if not isinstance(columns, list) or not columns:
        columns = ["*"]
    if columns != ["*"]:
        allowed = _COLUMNS_WHITELIST.get(table, set())
        bad = [c for c in columns if c not in allowed]
        if bad:
            raise ValueError(f"SELECT 列不在白名单: table={table}, cols={bad}")
    col_sql = ", ".join(f"`{c}`" for c in columns) if columns != ["*"] else "*"

    limit = min(int(args.get("limit") or 50), settings.query_mysql_max_rows)
    where = args.get("where") or []
    allowed_where = _COLUMNS_WHITELIST.get(table, set())
    clauses: list[str] = []
    params: list[Any] = []
    for w in where:
        field = w.get("column") or w.get("field")
        op = w.get("operator") or w.get("op", "=")
        value = w.get("value")
        if not field or op not in ALLOWED_OPS:
            continue
        if field == "project_code" and table == "proposal":
            pid = _resolve_project_id(str(value))
            if pid is None:
                raise ValueError(f"项目不存在: {value}")
            clauses.append("`project_id` = %s")
            params.append(pid)
            continue
        if field not in allowed_where:
            raise ValueError(f"WHERE 列不在白名单: table={table}, col={field}")
        if op == "IN" and isinstance(value, list):
            placeholders = ", ".join(["%s"] * len(value))
            clauses.append(f"`{field}` IN ({placeholders})")
            params.extend(value)
        else:
            clauses.append(f"`{field}` {op} %s")
            params.append(value)

    sql = f"SELECT {col_sql} FROM `{table}`"
    if table in ("project", "proposal", "material") and "deleted_at" not in " ".join(clauses):
        clauses.append("`deleted_at` IS NULL")
    if clauses:
        sql += " WHERE " + " AND ".join(clauses)

    order_by = args.get("order_by") or []
    if order_by:
        if not isinstance(order_by, list):
            order_by = [order_by]
        order_clauses = []
        allowed_cols = _ORDER_COLUMNS_WHITELIST.get(table, set())
        for ob in order_by:
            col = ob.get("column")
            direction = (ob.get("direction") or "ASC").upper()
            if col not in allowed_cols:
                raise ValueError(f"ORDER BY 列不在白名单: table={table}, col={col}")
            if direction not in ALLOWED_ORDER_DIRS:
                raise ValueError(f"ORDER BY 方向不合法: {direction}")
            order_clauses.append(f"`{col}` {direction}")
        sql += " ORDER BY " + ", ".join(order_clauses)
    else:
        sql += " ORDER BY `id` DESC"

    sql += f" LIMIT {limit}"

    with db_cursor() as cur:
        cur.execute(sql, params)
        return list(cur.fetchall() or [])
