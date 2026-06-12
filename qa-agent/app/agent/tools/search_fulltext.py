from typing import Any

from app.db.connection import db_cursor


def run(args: dict[str, Any], ctx: dict[str, Any]) -> list[dict[str, Any]]:
    query = (args.get("query") or "").strip()
    top_n = int(args.get("topN") or 5)
    project_code = args.get("projectCode") or ctx.get("project_code")
    if not query:
        return []

    sql = """
        SELECT mv.id AS versionId, mv.material_id AS materialId, m.title AS materialTitle,
               mv.version_no AS versionNo, mv.original_filename AS originalFilename,
               p.code AS projectCode, p.name AS projectName,
               pr.code AS proposalCode, pr.title AS proposalTitle,
               SUBSTRING(mv.parsed_text, 1, 300) AS snippet,
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
    return [dict(r) for r in rows]
