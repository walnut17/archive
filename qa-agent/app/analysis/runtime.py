"""Analysis worker 进程内单例."""

from __future__ import annotations

from app.analysis.worker import AnalysisWorker

_worker = AnalysisWorker()


def get_analysis_worker() -> AnalysisWorker:
    return _worker
