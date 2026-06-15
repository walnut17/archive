from typing import Any

import re

from app.db.connection import db_cursor

_SNIPPET_WINDOW = 400


def _snippet_around_keywords(text: str, keywords: list[str], width: int = _SNIPPET_WINDOW) -> str:
    if not text:
        return ""
    for kw in keywords:
        if not kw:
            continue
        pos = text.find(kw)
        if pos >= 0:
            start = max(0, pos - 80)
            return text[start : start + width]
    return text[:width]


def run(args: dict[str, Any], ctx: dict[str, Any]) -> list[dict[str, Any]]:
    query = (args.get("query") or "").strip()
    top_n = int(args.get("topN") or 5)
    project_code = args.get("projectCode") or ctx.get("project_code")
    if not query:
        return []

    keywords = [w for w in re.split(r"\s+", query) if w]

    sql = """
        SELECT mv.id AS versionId, mv.material_id AS materialId, m.title AS materialTitle,
               mv.version_no AS versionNo, mv.original_filename AS originalFilename,
               p.code AS projectCode, p.name AS projectName,
               pr.code AS proposalCode, pr.title AS proposalTitle,
               SUBSTRING(mv.parsed_text, 1, 8000) AS parsedExcerpt,
               MATCH(mv.parsed_text) AGAINST (%s IN NATURAL LANGUAGE MODE) AS score
        FROM material_version mv
        JOIN material m ON m.id = mv.material_id
        JOIN proposal pr ON pr.id = m.proposal_id
        JOIN project p ON p.id = pr.project_id
        WHERE mv.parsed_text IS NOT NULL
          AND MATCH(mv.parsed_text) AGAINST (%s IN NATURAL LANGUAGE MODE)
    """
    params: list[Any] = [query, query]
    if project_code:
        sql += " AND p.code = %s"
        params.append(project_code)
    sql += " ORDER BY score DESC LIMIT %s"
    params.append(top_n)

    with db_cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall() or []

    if not rows and project_code:
        like_sql = """
            SELECT mv.id AS versionId, mv.material_id AS materialId, m.title AS materialTitle,
                   mv.version_no AS versionNo, mv.original_filename AS originalFilename,
                   p.code AS projectCode, p.name AS projectName,
                   pr.code AS proposalCode, pr.title AS proposalTitle,
                   SUBSTRING(mv.parsed_text, 1, 8000) AS parsedExcerpt,
                   1.0 AS score
            FROM material_version mv
            JOIN material m ON m.id = mv.material_id
            JOIN proposal pr ON pr.id = m.proposal_id
            JOIN project p ON p.id = pr.project_id
            WHERE mv.parsed_text IS NOT NULL
              AND p.code = %s
              AND (
        """
        like_parts = []
        like_params: list[Any] = [project_code]
        for kw in keywords[:3]:
            like_parts.append("mv.parsed_text LIKE %s")
            like_params.append(f"%{kw}%")
        like_sql += " OR ".join(like_parts) + ") ORDER BY mv.id DESC LIMIT %s"
        like_params.append(top_n)
        cur.execute(like_sql, like_params)
        rows = cur.fetchall() or []

    out: list[dict[str, Any]] = []
    for r in rows:
        item = dict(r)
        excerpt = item.get("parsedExcerpt") or ""
        item["snippet"] = _snippet_around_keywords(excerpt, keywords)
        out.append(item)
    return out
