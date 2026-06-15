"""qa-agent 运行版本信息 — health / deploy / CLI 共用."""

from __future__ import annotations

import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.analysis.models import FEATURE_ANALYSIS_WORKER, FEATURE_DEPLOY_SANDBOX, FEATURE_LLM_DESENSITIZE
from app.agent.react_helpers import FEATURE_EVIDENCE_ROUTING, FEATURE_PROPOSAL_COUNT_ROUTING, FEATURE_DEBT_TARGET_ROUTING, FEATURE_COLLATERAL_ROUTING
from app.services.self_update import QA_AGENT_ROOT, read_version

_LOADED_GIT_SHA = read_version()
_PROCESS_STARTED_AT = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

FEATURE_FLAGS: dict[str, str] = {
    "evidence_routing": FEATURE_EVIDENCE_ROUTING,
    "post_search_finalize": "2026-06-15",
    "hot_deploy": "1",
    "material_count_routing": "1",
    "proposal_count_routing": FEATURE_PROPOSAL_COUNT_ROUTING,
    "debt_target_routing": FEATURE_DEBT_TARGET_ROUTING,
    "collateral_routing": FEATURE_COLLATERAL_ROUTING,
    "analysis_worker": FEATURE_ANALYSIS_WORKER,
    "llm_desensitize": FEATURE_LLM_DESENSITIZE,
    "deploy_sandbox": FEATURE_DEPLOY_SANDBOX,
}


def local_git_sha(repo_root: Path | None = None) -> str | None:
    """开发机 git 短 SHA（服务进程内一般不用）."""
    root = repo_root or QA_AGENT_ROOT.parent
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=root,
            stderr=subprocess.DEVNULL,
            text=True,
        )
        return out.strip() or None
    except (subprocess.CalledProcessError, FileNotFoundError, OSError):
        return None


def get_runtime_version(*, detailed: bool = False) -> dict[str, Any]:
    """当前进程实际运行的版本快照（启动时冻结 git_sha + 进程启动时间）."""
    payload: dict[str, Any] = {
        "service": "qa-agent",
        "git_sha": _LOADED_GIT_SHA,
        "features": dict(FEATURE_FLAGS),
        "process_started_at": _PROCESS_STARTED_AT,
    }
    if detailed:
        disk_sha = read_version()
        payload.update(
            {
                "qa_agent_root": str(QA_AGENT_ROOT),
                "version_file": str(QA_AGENT_ROOT / "VERSION"),
                "version_file_exists": (QA_AGENT_ROOT / "VERSION").is_file(),
                "env_override": os.environ.get("QA_AGENT_VERSION"),
                "disk_git_sha": disk_sha,
                "pending_restart": disk_sha != _LOADED_GIT_SHA,
            }
        )
    return payload


def compare_git_sha(local_sha: str, remote_sha: str) -> str:
    """local vs remote：match / behind / ahead / unknown."""
    local = (local_sha or "").strip().lower()
    remote = (remote_sha or "").strip().lower()
    if not local or not remote or local in ("dev", "unknown") or remote in ("dev", "unknown"):
        return "unknown"
    if local == remote:
        return "match"
    return "diff"
