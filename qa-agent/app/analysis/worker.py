"""后台分析 Worker — 单线程轮询执行 analysis_job."""

from __future__ import annotations

import logging
import threading
import time
from typing import Any

from app.analysis.context import load_project_context
from app.analysis.extractor import run_template_extract, summarize_result
from app.analysis.mapper import sync_facts_from_snapshots, upsert_project_assets_from_inventory, write_timepoints
from app.analysis.models import AnalysisScope, JobStatus, JobType
from app.analysis.repository import (
    claim_next_job,
    count_assets,
    count_snapshots,
    finish_job,
    requeue_failed_job,
    save_snapshot,
    table_available,
    upsert_analysis_state,
    worker_stats,
)
from app.analysis.scheduler import discover_and_enqueue
from app.analysis.templates import get_template, list_templates
from app.config import settings

logger = logging.getLogger(__name__)


class AnalysisWorker:
    def __init__(self) -> None:
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._lock = threading.Lock()
        self._running_job_id: int | None = None
        self._last_tick_at: float | None = None
        self._last_error: str | None = None
        self._jobs_processed = 0

    @property
    def enabled(self) -> bool:
        return settings.analysis_worker_enabled

    def start(self) -> None:
        if not self.enabled:
            logger.info("analysis worker 未启用 (qaAgent.analysisWorker.enabled=false)")
            return
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(
            target=self._loop,
            name="qa-agent-analysis-worker",
            daemon=True,
        )
        self._thread.start()
        logger.info(
            "analysis worker 已启动 poll=%ss",
            settings.analysis_worker_poll_seconds,
        )

    def stop(self, timeout: float = 5.0) -> None:
        self._stop.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=timeout)

    def status(self) -> dict[str, Any]:
        return {
            "enabled": self.enabled,
            "alive": bool(self._thread and self._thread.is_alive()),
            "running_job_id": self._running_job_id,
            "jobs_processed": self._jobs_processed,
            "last_tick_at": self._last_tick_at,
            "last_error": self._last_error,
            "queue": worker_stats(),
            "tables_ready": table_available("analysis_job"),
        }

    def run_once(self) -> dict[str, Any]:
        """手动执行一轮：discover + 处理一个 job（供 API/脚本调用）."""
        discovered = discover_and_enqueue(limit=settings.analysis_worker_discover_batch)
        processed = self._process_one_job()
        return {
            "discovered": len(discovered),
            "processed": processed,
            "queue": worker_stats(),
        }

    def _loop(self) -> None:
        while not self._stop.is_set():
            self._last_tick_at = time.time()
            try:
                if table_available("analysis_job"):
                    discover_and_enqueue(limit=settings.analysis_worker_discover_batch)
                    while self._process_one_job():
                        if self._stop.is_set():
                            break
                else:
                    self._last_error = "analysis_job 表不存在，请先执行迁移 SQL"
            except Exception as e:
                self._last_error = str(e)
                logger.exception("analysis worker tick 失败")
            self._stop.wait(settings.analysis_worker_poll_seconds)

    def _process_one_job(self) -> bool:
        with self._lock:
            job = claim_next_job()
            if not job:
                return False
            self._running_job_id = int(job["id"])
        try:
            ok, err = self._execute_job(job)
            if ok:
                finish_job(int(job["id"]), success=True)
                self._jobs_processed += 1
                self._last_error = None
            elif err and requeue_failed_job(int(job["id"]), err):
                upsert_analysis_state(
                    int(job["project_id"]),
                    last_status=JobStatus.PENDING,
                    last_error=err,
                )
            else:
                finish_job(int(job["id"]), success=False, error=err)
                upsert_analysis_state(
                    int(job["project_id"]),
                    last_status=JobStatus.FAILED,
                    last_error=err,
                )
                self._last_error = err
        finally:
            with self._lock:
                self._running_job_id = None
        return True

    def _execute_job(self, job: dict[str, Any]) -> tuple[bool, str | None]:
        project_id = int(job["project_id"])
        ctx = load_project_context(project_id)
        if not ctx:
            return False, "项目无可用 parsed 材料"

        upsert_analysis_state(
            project_id,
            material_fingerprint=ctx.material_fingerprint,
            last_job_id=int(job["id"]),
            last_status=JobStatus.RUNNING,
            mark_started=True,
        )

        job_type = job.get("job_type") or JobType.PROJECT_DEEP
        if job_type == JobType.ASSET_CREDIT:
            return self._run_asset_job(job, ctx)

        return self._run_project_deep_job(job, ctx)

    def _run_project_deep_job(self, job: dict[str, Any], ctx) -> tuple[bool, str | None]:
        project_id = int(job["project_id"])
        template_code = job.get("template_code")
        errors: list[str] = []

        project_templates = list_templates(scope=AnalysisScope.PROJECT)
        if template_code:
            t = get_template(template_code)
            project_templates = [t] if t else []

        inventory_data: dict | None = None
        for template in project_templates:
            if not template or not template.enabled:
                continue
            result = run_template_extract(template, ctx)
            if not result.success or not result.data:
                errors.append(f"{template.code}: {result.message or 'extract failed'}")
                continue
            save_snapshot(
                project_id=project_id,
                template_code=template.code,
                scope=template.scope,
                result_json=result.data,
                summary_text=summarize_result(template, result.data),
                confidence=result.confidence,
                confidence_level=result.confidence_level,
                material_fingerprint=ctx.material_fingerprint,
            )
            if template.code == "project.credit_inventory":
                inventory_data = result.data

        asset_ids: list[int] = []
        if inventory_data:
            asset_ids = upsert_project_assets_from_inventory(project_id, inventory_data)

        asset_template = get_template("asset.credit_profile")
        if asset_template and asset_template.enabled:
            for asset_id, asset_name in _list_credit_assets(project_id, asset_ids):
                result = run_template_extract(
                    asset_template, ctx, asset_name=asset_name
                )
                if not result.success or not result.data:
                    errors.append(
                        f"asset.{asset_id}: {result.message or 'extract failed'}"
                    )
                    continue
                save_snapshot(
                    project_id=project_id,
                    asset_id=asset_id,
                    template_code=asset_template.code,
                    scope=asset_template.scope,
                    result_json=result.data,
                    summary_text=summarize_result(asset_template, result.data),
                    confidence=result.confidence,
                    confidence_level=result.confidence_level,
                    material_fingerprint=ctx.material_fingerprint,
                )

        sync_facts_from_snapshots(project_id)
        write_timepoints(project_id)
        upsert_analysis_state(
            project_id,
            material_fingerprint=ctx.material_fingerprint,
            last_job_id=int(job["id"]),
            last_status=JobStatus.SUCCESS if not errors else JobStatus.FAILED,
            last_error="; ".join(errors)[:2000] if errors else None,
            snapshot_count=count_snapshots(project_id),
            asset_count=count_assets(project_id),
            mark_completed=True,
        )
        if errors and count_snapshots(project_id) == 0:
            return False, "; ".join(errors)
        return True, "; ".join(errors) if errors else None

    def _run_asset_job(self, job: dict[str, Any], ctx) -> tuple[bool, str | None]:
        asset_id = job.get("asset_id")
        template = get_template(job.get("template_code") or "asset.credit_profile")
        if not template:
            return False, "asset 模板不存在"
        asset_name = _asset_name(int(asset_id)) if asset_id else ""
        result = run_template_extract(template, ctx, asset_name=asset_name)
        if not result.success or not result.data:
            return False, result.message or "asset extract failed"
        save_snapshot(
            project_id=int(job["project_id"]),
            asset_id=int(asset_id) if asset_id else None,
            template_code=template.code,
            scope=template.scope,
            result_json=result.data,
            summary_text=summarize_result(template, result.data),
            confidence=result.confidence,
            material_fingerprint=ctx.material_fingerprint,
        )
        return True, None


def _asset_name(asset_id: int) -> str:
    from app.db.connection import db_cursor

    with db_cursor() as cur:
        cur.execute("SELECT name FROM project_asset WHERE id = %s", (asset_id,))
        row = cur.fetchone()
    return str((row or {}).get("name") or "")


def _list_credit_assets(project_id: int, asset_ids: list[int]) -> list[tuple[int, str]]:
    from app.db.connection import db_cursor

    pairs: list[tuple[int, str]] = []
    with db_cursor() as cur:
        if asset_ids:
            for aid in asset_ids:
                cur.execute("SELECT id, name FROM project_asset WHERE id = %s", (aid,))
                row = cur.fetchone()
                if row:
                    pairs.append((int(row["id"]), str(row["name"])))
        else:
            cur.execute(
                """
                SELECT id, name FROM project_asset
                WHERE project_id = %s AND asset_type = 'credit' AND status = 'active'
                """,
                (project_id,),
            )
            for row in cur.fetchall() or []:
                pairs.append((int(row["id"]), str(row["name"])))
    return pairs
