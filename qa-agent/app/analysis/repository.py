"""分析任务与快照持久化."""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Any

from app.analysis.models import ANALYZER_VERSION, JobStatus, JobType
from app.db.connection import db_cursor

logger = logging.getLogger(__name__)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def table_available(table: str) -> bool:
    try:
        with db_cursor() as cur:
            cur.execute(f"SELECT 1 FROM {table} LIMIT 1")
        return True
    except Exception:
        return False


def get_analysis_state(project_id: int) -> dict[str, Any] | None:
    try:
        with db_cursor() as cur:
            cur.execute("SELECT * FROM project_analysis_state WHERE project_id = %s", (project_id,))
            return cur.fetchone()
    except Exception:
        return None


def upsert_analysis_state(
    project_id: int,
    *,
    material_fingerprint: str | None = None,
    last_job_id: int | None = None,
    last_status: str = "never",
    last_error: str | None = None,
    snapshot_count: int | None = None,
    asset_count: int | None = None,
    mark_started: bool = False,
    mark_completed: bool = False,
) -> None:
    try:
        with db_cursor() as cur:
            cur.execute(
                "SELECT project_id FROM project_analysis_state WHERE project_id = %s",
                (project_id,),
            )
            exists = cur.fetchone()
            now = _utc_now()
            if not exists:
                cur.execute(
                    """
                    INSERT INTO project_analysis_state
                        (project_id, material_fingerprint, last_job_id, last_status,
                         last_started_at, last_completed_at, last_error,
                         snapshot_count, asset_count)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        project_id,
                        material_fingerprint,
                        last_job_id,
                        last_status,
                        now if mark_started else None,
                        now if mark_completed else None,
                        last_error,
                        snapshot_count or 0,
                        asset_count or 0,
                    ),
                )
                return

            sets = ["last_status = %s", "updated_at = CURRENT_TIMESTAMP"]
            params: list[Any] = [last_status]
            if material_fingerprint is not None:
                sets.append("material_fingerprint = %s")
                params.append(material_fingerprint)
            if last_job_id is not None:
                sets.append("last_job_id = %s")
                params.append(last_job_id)
            if last_error is not None:
                sets.append("last_error = %s")
                params.append(last_error)
            if snapshot_count is not None:
                sets.append("snapshot_count = %s")
                params.append(snapshot_count)
            if asset_count is not None:
                sets.append("asset_count = %s")
                params.append(asset_count)
            if mark_started:
                sets.append("last_started_at = %s")
                params.append(now)
            if mark_completed:
                sets.append("last_completed_at = %s")
                params.append(now)
            params.append(project_id)
            cur.execute(
                f"UPDATE project_analysis_state SET {', '.join(sets)} WHERE project_id = %s",
                params,
            )
    except Exception as e:
        logger.debug("upsert_analysis_state 失败: %s", e)


def has_active_job(project_id: int) -> bool:
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT id FROM analysis_job
                WHERE project_id = %s AND status IN ('pending', 'running')
                LIMIT 1
                """,
                (project_id,),
            )
            return cur.fetchone() is not None
    except Exception:
        return False


def enqueue_job(
    project_id: int,
    *,
    job_type: str = JobType.PROJECT_DEEP,
    template_code: str | None = None,
    asset_id: int | None = None,
    trigger_reason: str = "bootstrap",
    material_fingerprint: str | None = None,
    priority: int = 100,
) -> int | None:
    if has_active_job(project_id) and job_type == JobType.PROJECT_DEEP:
        return None
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                INSERT INTO analysis_job
                    (job_type, project_id, asset_id, template_code, status, priority,
                     trigger_reason, material_fingerprint)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    job_type,
                    project_id,
                    asset_id,
                    template_code,
                    JobStatus.PENDING,
                    priority,
                    trigger_reason,
                    material_fingerprint,
                ),
            )
            job_id = int(cur.lastrowid)
        upsert_analysis_state(
            project_id,
            material_fingerprint=material_fingerprint,
            last_job_id=job_id,
            last_status=JobStatus.PENDING,
        )
        return job_id
    except Exception as e:
        logger.warning("enqueue_job 失败: %s", e)
        return None


def claim_next_job() -> dict[str, Any] | None:
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT id FROM analysis_job
                WHERE status = %s
                ORDER BY priority ASC, scheduled_at ASC, id ASC
                LIMIT 1
                """,
                (JobStatus.PENDING,),
            )
            pick = cur.fetchone()
            if not pick:
                return None
            job_id = int(pick["id"])
            cur.execute(
                """
                UPDATE analysis_job
                SET status = %s, started_at = %s, attempts = attempts + 1
                WHERE id = %s AND status = %s
                """,
                (JobStatus.RUNNING, _utc_now(), job_id, JobStatus.PENDING),
            )
            if cur.rowcount != 1:
                return None
            cur.execute("SELECT * FROM analysis_job WHERE id = %s", (job_id,))
            return cur.fetchone()
    except Exception as e:
        logger.debug("claim_next_job 失败 (表可能不存在): %s", e)
        return None


def finish_job(
    job_id: int,
    *,
    success: bool,
    error: str | None = None,
) -> None:
    status = JobStatus.SUCCESS if success else JobStatus.FAILED
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                UPDATE analysis_job
                SET status = %s, finished_at = %s, last_error = %s
                WHERE id = %s
                """,
                (status, _utc_now(), error, job_id),
            )
    except Exception as e:
        logger.debug("finish_job 失败: %s", e)


def requeue_failed_job(job_id: int, error: str) -> bool:
    try:
        with db_cursor() as cur:
            cur.execute("SELECT attempts, max_attempts FROM analysis_job WHERE id = %s", (job_id,))
            row = cur.fetchone()
            if not row:
                return False
            if int(row["attempts"]) >= int(row["max_attempts"]):
                finish_job(job_id, success=False, error=error)
                return False
            cur.execute(
                """
                UPDATE analysis_job
                SET status = %s, last_error = %s, finished_at = NULL
                WHERE id = %s
                """,
                (JobStatus.PENDING, error, job_id),
            )
            return True
    except Exception:
        return False


def save_snapshot(
    *,
    project_id: int,
    template_code: str,
    scope: str,
    result_json: dict[str, Any],
    summary_text: str | None = None,
    asset_id: int | None = None,
    confidence: float | None = None,
    confidence_level: str = "AI_INFERRED",
    evidence_material_version_id: int | None = None,
    evidence_snippet: str | None = None,
    material_fingerprint: str | None = None,
) -> None:
    payload = json.dumps(result_json, ensure_ascii=False)
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT id FROM analysis_snapshot
                WHERE project_id = %s AND template_code = %s
                  AND asset_key = IFNULL(%s, 0)
                LIMIT 1
                """,
                (project_id, template_code, asset_id),
            )
            row = cur.fetchone()
            if row:
                cur.execute(
                    """
                    UPDATE analysis_snapshot
                    SET scope = %s, result_json = %s, summary_text = %s,
                        confidence = %s, confidence_level = %s,
                        evidence_material_version_id = %s, evidence_snippet = %s,
                        material_fingerprint = %s, analyzer_version = %s,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = %s
                    """,
                    (
                        scope,
                        payload,
                        summary_text,
                        confidence,
                        confidence_level,
                        evidence_material_version_id,
                        evidence_snippet,
                        material_fingerprint,
                        ANALYZER_VERSION,
                        row["id"],
                    ),
                )
            else:
                cur.execute(
                    """
                    INSERT INTO analysis_snapshot
                        (project_id, asset_id, template_code, scope, result_json,
                         summary_text, confidence, confidence_level,
                         evidence_material_version_id, evidence_snippet,
                         material_fingerprint, analyzer_version)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        project_id,
                        asset_id,
                        template_code,
                        scope,
                        payload,
                        summary_text,
                        confidence,
                        confidence_level,
                        evidence_material_version_id,
                        evidence_snippet,
                        material_fingerprint,
                        ANALYZER_VERSION,
                    ),
                )
    except Exception as e:
        logger.warning("save_snapshot 失败: %s", e)


def count_snapshots(project_id: int) -> int:
    try:
        with db_cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) AS c FROM analysis_snapshot WHERE project_id = %s",
                (project_id,),
            )
            row = cur.fetchone()
            return int((row or {}).get("c") or 0)
    except Exception:
        return 0


def count_assets(project_id: int) -> int:
    try:
        with db_cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) AS c FROM project_asset WHERE project_id = %s AND status = 'active'",
                (project_id,),
            )
            row = cur.fetchone()
            return int((row or {}).get("c") or 0)
    except Exception:
        return 0


def list_snapshots(project_id: int, *, template_code: str | None = None) -> list[dict[str, Any]]:
    try:
        with db_cursor() as cur:
            sql = "SELECT * FROM analysis_snapshot WHERE project_id = %s"
            params: list[Any] = [project_id]
            if template_code:
                sql += " AND template_code = %s"
                params.append(template_code)
            sql += " ORDER BY template_code, asset_id"
            cur.execute(sql, params)
            rows = cur.fetchall() or []
        for row in rows:
            payload = row.get("result_json")
            if isinstance(payload, str):
                try:
                    row["result_json"] = json.loads(payload)
                except json.JSONDecodeError:
                    pass
        return rows
    except Exception:
        return []


def list_stale_projects(limit: int = 20) -> list[dict[str, Any]]:
    """有 parsed 材料但指纹与上次分析不一致（或从未分析）的项目."""
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT p.id AS project_id, p.code AS project_code, p.name AS project_name,
                       pas.material_fingerprint AS last_fingerprint,
                       pas.last_status,
                       GROUP_CONCAT(mv.id ORDER BY mv.id SEPARATOR ',') AS version_ids,
                       GROUP_CONCAT(IFNULL(mv.updated_at, mv.id) ORDER BY mv.id SEPARATOR ',') AS updated_tokens
                FROM project p
                JOIN proposal pr ON pr.project_id = p.id
                JOIN material m ON m.proposal_id = pr.id
                JOIN material_version mv ON mv.material_id = m.id
                LEFT JOIN project_analysis_state pas ON pas.project_id = p.id
                WHERE p.deleted_at IS NULL
                  AND mv.parse_status = 'success'
                  AND mv.parsed_text IS NOT NULL
                  AND TRIM(mv.parsed_text) <> ''
                GROUP BY p.id, p.code, p.name, pas.material_fingerprint, pas.last_status
                HAVING last_status IS NULL
                    OR last_status IN ('never', 'failed', 'success')
                ORDER BY COALESCE(pas.last_completed_at, '1970-01-01') ASC, p.id ASC
                LIMIT %s
                """,
                (limit * 3,),
            )
            rows = cur.fetchall() or []
    except Exception as e:
        logger.debug("list_stale_projects 失败: %s", e)
        return []

    import hashlib

    out: list[dict[str, Any]] = []
    for row in rows:
        ids = (row.get("version_ids") or "").split(",")
        tokens = (row.get("updated_tokens") or "").split(",")
        fp_raw = "|".join(sorted(ids)) + "#" + "|".join(tokens)
        fingerprint = hashlib.sha256(fp_raw.encode("utf-8")).hexdigest()[:32]
        if fingerprint == (row.get("last_fingerprint") or ""):
            if row.get("last_status") == "success":
                continue
        if has_active_job(int(row["project_id"])):
            continue
        row["material_fingerprint"] = fingerprint
        out.append(row)
        if len(out) >= limit:
            break
    return out


def worker_stats() -> dict[str, Any]:
    stats = {"pending": 0, "running": 0, "success": 0, "failed": 0}
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT status, COUNT(*) AS c FROM analysis_job
                GROUP BY status
                """
            )
            for row in cur.fetchall() or []:
                stats[str(row["status"])] = int(row["c"])
    except Exception:
        pass
    return stats
