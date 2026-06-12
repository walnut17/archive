import re
from typing import Any

from app.db.connection import db_cursor

# v1.2: 4 级链升级 — 简称 token 化 (与 Java FindProjectTool.buildSearchVariants 对齐)
LATIN_OR_DIGIT_TOKEN = re.compile(r"[a-zA-Z0-9]{2,}")


def build_search_variants(raw: str) -> list[str]:
    """从用户口头语生成多组 MySQL 检索词 (单次 tool 调用内全部尝试).

    例:
        "lmz项目" → ["lmz项目", "lmz"]
        "PRJ-2026-001 剩余金额" → ["PRJ-2026-001 剩余金额"]  (含编号不变体)
        "林谋志项目" → ["林谋志项目", "林谋志"]
    """
    if not raw or not raw.strip():
        return []
    variants: list[str] = []
    q = raw.strip()
    variants.append(q)

    # 去尾词 (项目/工程/案件/业务/计划/那个/这笔)
    no_suffix = re.sub(r"(项目|工程|案件|业务|计划|那个|这笔)$", "", q).strip()
    if no_suffix and no_suffix != q:
        variants.append(no_suffix)

    # 含编号 (PRJ-2026-XXX) 不拆 token
    if not re.match(r"(?i)PRJ[-_\s]?\d.*", q):
        for m in LATIN_OR_DIGIT_TOKEN.finditer(q):
            token = m.group()
            # 数字 token 不拆 (PRJ-001 里的 001)
            if not token.isdigit():
                if token not in variants:
                    variants.append(token)

    return variants


def run(args: dict[str, Any], ctx: dict[str, Any]) -> list[dict[str, Any]]:
    query = (args.get("query") or "").strip()
    top_n = int(args.get("topN") or 3)
    if not query:
        return []

    # v1.2: 多变体一次工具内全试, 避免 Agent 死循环
    variants = build_search_variants(query)
    seen_codes: set[str] = set()
    all_results: list[dict] = []

    with db_cursor() as cur:
        for variant in variants:
            # 1) 精确编号 (单变体)
            if variant.upper().startswith("PRJ-"):
                cur.execute(
                    """
                    SELECT id, code, name, status, amount_wan AS amountWan, customer_name, 1.0 AS score
                    FROM project WHERE code = %s AND deleted_at IS NULL LIMIT 1
                    """,
                    (variant.upper(),),
                )
                row = cur.fetchone()
                if row and row["code"] not in seen_codes:
                    seen_codes.add(row["code"])
                    all_results.append(_fmt(row, 1.0, ctx))
                    if len(all_results) >= top_n:
                        break

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
                (variant, variant, f"%{variant}%", f"%{variant}%", top_n),
            )
            rows = cur.fetchall() or []
            for r in rows:
                if r["code"] not in seen_codes:
                    seen_codes.add(r["code"])
                    all_results.append(_fmt(r, float(r.get("score") or 0.5), ctx))
            if len(all_results) >= top_n:
                break

    return all_results[:top_n]


def _fmt(row: dict, conf: float, ctx: dict[str, Any] | None = None) -> dict:
    """格式化项目行，包含 Java FindProjectTool 兼容字段与 5 级隐式切换."""
    code = row["code"]
    name = row["name"]

    # 5 级隐式切换判定 (对齐 Java applyImplicitSwitchRule + SwitchDecision)
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
        # 无锁定 (对齐 Java applyImplicitSwitchRule)
        if conf >= 0.95:
            switch_decision = "SAME_CONFIRMED"
            if ctx is not None:
                ctx["project_code"] = code
        elif conf >= 0.7:
            switch_decision = "SAME_PROBABLY"
        else:
            switch_decision = "UNCLEAR"

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
