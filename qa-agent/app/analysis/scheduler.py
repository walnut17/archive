"""发现待分析项目并入队."""

from __future__ import annotations

import logging

from app.analysis.models import JobType
from app.analysis.repository import enqueue_job, list_stale_projects, table_available

logger = logging.getLogger(__name__)


def discover_and_enqueue(*, limit: int = 5) -> list[int]:
    """扫描材料已 parse 且指纹变化的项目，创建 project_deep 任务."""
    if not table_available("analysis_job"):
        logger.debug("analysis_job 表不存在，跳过 discover")
        return []

    job_ids: list[int] = []
    for row in list_stale_projects(limit=limit):
        project_id = int(row["project_id"])
        reason = "new_material" if row.get("last_fingerprint") else "bootstrap"
        if row.get("last_status") == "failed":
            reason = "stale"
        job_id = enqueue_job(
            project_id,
            job_type=JobType.PROJECT_DEEP,
            trigger_reason=reason,
            material_fingerprint=row.get("material_fingerprint"),
        )
        if job_id:
            job_ids.append(job_id)
            logger.info(
                "分析入队 project_id=%s code=%s job_id=%s reason=%s",
                project_id,
                row.get("project_code"),
                job_id,
                reason,
            )
    return job_ids


def enqueue_project_manual(project_id: int, *, reason: str = "manual") -> int | None:
    from app.analysis.context import load_project_context

    ctx = load_project_context(project_id)
    if not ctx:
        return None
    return enqueue_job(
        project_id,
        job_type=JobType.PROJECT_DEEP,
        trigger_reason=reason,
        material_fingerprint=ctx.material_fingerprint,
        priority=50,
    )
