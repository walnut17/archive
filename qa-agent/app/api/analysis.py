"""后台深度分析 API."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.analysis.context import load_project_context_by_code
from app.analysis.repository import get_analysis_state, list_snapshots, worker_stats
from app.analysis.scheduler import enqueue_project_manual
from app.analysis.templates import list_templates
from app.api.schemas import (
    AnalysisEnqueueRequest,
    AnalysisEnqueueResponse,
    AnalysisRunOnceResponse,
    AnalysisStatusResponse,
    ProjectAnalysisResponse,
)
from app.analysis.runtime import get_analysis_worker

router = APIRouter(prefix="/v1/analysis", tags=["analysis"])


@router.get("/status", response_model=AnalysisStatusResponse)
def analysis_status() -> AnalysisStatusResponse:
    worker = get_analysis_worker()
    payload = worker.status()
    payload["templates"] = [
        {"code": t.code, "name": t.name, "scope": t.scope, "enabled": t.enabled}
        for t in list_templates(enabled_only=True)
    ]
    return AnalysisStatusResponse(**payload)


@router.post("/run-once", response_model=AnalysisRunOnceResponse)
def analysis_run_once() -> AnalysisRunOnceResponse:
    worker = get_analysis_worker()
    result = worker.run_once()
    return AnalysisRunOnceResponse(**result)


@router.post("/enqueue", response_model=AnalysisEnqueueResponse)
def analysis_enqueue(req: AnalysisEnqueueRequest) -> AnalysisEnqueueResponse:
    project_id = req.project_id
    if not project_id and req.project_code:
        ctx = load_project_context_by_code(req.project_code)
        if not ctx:
            raise HTTPException(status_code=404, detail="项目不存在或无 parsed 材料")
        project_id = ctx.project_id
    if not project_id:
        raise HTTPException(status_code=400, detail="project_id 或 project_code 必填")

    job_id = enqueue_project_manual(project_id, reason=req.reason or "manual")
    if not job_id:
        raise HTTPException(status_code=409, detail="项目已有 pending/running 分析任务")
    return AnalysisEnqueueResponse(job_id=job_id, project_id=project_id)


@router.get("/projects/{project_code}", response_model=ProjectAnalysisResponse)
def project_analysis(project_code: str) -> ProjectAnalysisResponse:
    ctx = load_project_context_by_code(project_code)
    if not ctx:
        raise HTTPException(status_code=404, detail="项目不存在或无 parsed 材料")

    state = get_analysis_state(ctx.project_id) or {}
    snapshots = list_snapshots(ctx.project_id)
    return ProjectAnalysisResponse(
        project_id=ctx.project_id,
        project_code=ctx.project_code,
        project_name=ctx.project_name,
        material_fingerprint=ctx.material_fingerprint,
        analysis_state=state,
        snapshots=snapshots,
        queue=worker_stats(),
    )
