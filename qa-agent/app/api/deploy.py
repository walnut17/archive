"""部署端点：热更新 zip + 自重启（需 deploy token）."""

from __future__ import annotations

import logging
import zipfile
from pathlib import Path
from typing import Any

from fastapi import APIRouter, File, Form, Header, HTTPException, Query, UploadFile

from app.config import settings
from app.services.self_update import (
    apply_zip_update,
    deploy_enabled,
    schedule_restart,
    verify_deploy_token,
)
from app.services.version_info import get_runtime_version

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/deploy", tags=["deploy"])


def _resolve_token(
    header_token: str | None,
    query_token: str | None = None,
    form_token: str | None = None,
) -> str | None:
    for raw in (header_token, query_token, form_token):
        if raw and raw.strip():
            return raw.strip()
    return None


def _require_token(
    header_token: str | None = None,
    query_token: str | None = None,
    form_token: str | None = None,
) -> None:
    if not deploy_enabled(settings.qa_agent_deploy_token):
        raise HTTPException(
            status_code=503,
            detail="自更新未启用：请在 config.json qaAgent.deployToken 或 QA_AGENT_DEPLOY_TOKEN 配置密钥",
        )
    provided = _resolve_token(header_token, query_token, form_token)
    if not provided or not verify_deploy_token(provided, settings.qa_agent_deploy_token):
        raise HTTPException(status_code=401, detail="无效的 X-Deploy-Token")


@router.get("/status")
def deploy_status() -> dict:
    token = settings.qa_agent_deploy_token.strip()
    runtime = get_runtime_version(detailed=True)
    return {
        **runtime,
        "deploy_enabled": deploy_enabled(token),
        "token_length": len(token) if token else 0,
    }


@router.get("/version")
def deploy_version() -> dict:
    """当前运行进程版本（无需 token，供开发机/TUI 对账）."""
    return get_runtime_version(detailed=True)


@router.post("/update")
async def deploy_update(
    file: UploadFile = File(...),
    x_deploy_token: str | None = Header(default=None, alias="X-Deploy-Token"),
    token: str | None = Query(default=None, description="与 Header 二选一"),
    deploy_token: str | None = Form(default=None, description="multipart 表单 token"),
    restart: bool = True,
) -> dict:
    """上传 qa-agent 更新 zip（含 app/、tools/、scripts/），可选自动重启."""
    _require_token(x_deploy_token, token, deploy_token)

    if not file.filename or not file.filename.lower().endswith(".zip"):
        raise HTTPException(status_code=400, detail="请上传 .zip 文件")

    raw = await file.read()
    try:
        result = apply_zip_update(raw)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    except zipfile.BadZipFile as e:
        raise HTTPException(status_code=400, detail="无效的 zip 文件") from e

    if restart:
        spawn = schedule_restart(
            settings.config_json_path or None,
            settings.qa_agent_log_dir or None,
        )
        result["restart_spawn"] = spawn
    else:
        result["restart_spawn"] = {"ok": False, "skipped": True}

    logger.info("deploy update applied: %s files", result.get("files_updated"))
    return {
        "status": "accepted",
        "message": "更新已写入，服务即将重启" if restart else "更新已写入，未请求重启",
        "restart_scheduled": restart,
        **result,
    }


@router.post("/restart")
def deploy_restart(
    x_deploy_token: str | None = Header(default=None, alias="X-Deploy-Token"),
    token: str | None = Query(default=None),
) -> dict:
    """仅重启 qa-agent（不更新文件）."""
    _require_token(x_deploy_token, token)
    spawn = schedule_restart(
        settings.config_json_path or None,
        settings.qa_agent_log_dir or None,
    )
    return {"status": "accepted", "message": "重启已调度", "restart_spawn": spawn}


@router.get("/restart-log")
def deploy_restart_log(lines: int = Query(default=40, ge=1, le=200)) -> dict:
    """最近重启调度日志（开发机排障用，无需 token）."""
    log_dir = Path(settings.qa_agent_log_dir or "D:/archive/logs/qa-agent")
    files = {
        "spawn": log_dir / "schedule_restart_spawn.log",
        "restart": log_dir / "apply_update_restart.log",
        "stop": log_dir / "stop-qa-agent.log",
    }
    out: dict[str, Any] = {"log_dir": str(log_dir)}
    for key, path in files.items():
        if path.is_file():
            text = path.read_text(encoding="utf-8", errors="replace").splitlines()
            out[key] = text[-lines:]
        else:
            out[key] = []
    return out
