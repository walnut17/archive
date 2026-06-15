"""为分析任务组装项目材料上下文."""

from __future__ import annotations

import hashlib
import logging

from app.analysis.models import ProjectContext
from app.db.connection import db_cursor

logger = logging.getLogger(__name__)

_MAX_MATERIALS = 12
_CHARS_PER_MATERIAL = 8000


def _fingerprint(version_ids: list[int], updated_tokens: list[str]) -> str:
    raw = "|".join(str(i) for i in sorted(version_ids)) + "#" + "|".join(updated_tokens)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:32]


def load_project_context(project_id: int, max_chars: int = 30000) -> ProjectContext | None:
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT id, code, name FROM project WHERE id = %s AND deleted_at IS NULL
            """,
            (project_id,),
        )
        proj = cur.fetchone()
        if not proj:
            return None

        cur.execute(
            """
            SELECT mv.id, mv.original_filename, mv.parsed_text, mv.updated_at
            FROM material_version mv
            JOIN material m ON m.id = mv.material_id
            JOIN proposal p ON p.id = m.proposal_id
            WHERE p.project_id = %s
              AND mv.parse_status = 'success'
              AND mv.parsed_text IS NOT NULL
              AND TRIM(mv.parsed_text) <> ''
            ORDER BY mv.updated_at DESC, mv.id DESC
            LIMIT %s
            """,
            (project_id, _MAX_MATERIALS),
        )
        rows = cur.fetchall() or []

    if not rows:
        return None

    parts: list[str] = []
    version_ids: list[int] = []
    updated_tokens: list[str] = []
    budget = max_chars

    for row in rows:
        vid = int(row["id"])
        version_ids.append(vid)
        updated_tokens.append(str(row.get("updated_at") or vid))
        title = (row.get("original_filename") or f"material-{vid}").strip()
        text = (row.get("parsed_text") or "").strip()
        if not text:
            continue
        chunk = text[: min(_CHARS_PER_MATERIAL, budget)]
        parts.append(f"### [{vid}] {title}\n{chunk}")
        budget -= len(chunk)
        if budget <= 0:
            break

    materials_text = "\n\n".join(parts)
    return ProjectContext(
        project_id=project_id,
        project_code=str(proj.get("code") or ""),
        project_name=str(proj.get("name") or ""),
        materials_text=materials_text,
        material_fingerprint=_fingerprint(version_ids, updated_tokens),
        material_version_ids=version_ids,
    )


def load_project_context_by_code(project_code: str, max_chars: int = 30000) -> ProjectContext | None:
    with db_cursor() as cur:
        cur.execute(
            "SELECT id FROM project WHERE code = %s AND deleted_at IS NULL",
            (project_code,),
        )
        row = cur.fetchone()
    if not row:
        return None
    return load_project_context(int(row["id"]), max_chars=max_chars)
