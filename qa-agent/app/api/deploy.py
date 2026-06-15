"""部署端点：热更新 zip + 自重启（需 deploy token）."""

from __future__ import annotations

import logging
import zipfile

from fastapi import APIRouter, BackgroundTasks, File, Header, HTTPException, UploadFile

from app.config import settings
from app.services.self_update import (
    apply_zip_update,
    deploy_enabled,
    read_version,
    schedule_restart,
    verify_deploy_token,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/deploy", tags=["deploy"])


def _require_token(x_deploy_token: str | None) -> None:
    if not deploy_enabled(settings.qa_agent_deploy_token):
        raise HTTPException(
            status_code=503,
            detail="自更新未启用：请在 config.json qaAgent.deployToken 或 QA_AGENT_DEPLOY_TOKEN 配置密钥",
        )
    if not x_deploy_token or not verify_deploy_token(x_deploy_token, settings.qa_agent_deploy_token):
        raise HTTPException(status_code=401, detail="无效的 X-Deploy-Token")


@router.get("/status")
def deploy_status() -> dict:
    return {
        "service": "qa-agent",
        "version": read_version(),
        "deploy_enabled": deploy_enabled(settings.qa_agent_deploy_token),
        "qa_agent_root": str(settings.qa_agent_root),
    }


@router.post("/update")
async def deploy_update(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    x_deploy_token: str | None = Header(default=None, alias="X-Deploy-Token"),
    restart: bool = True,
) -> dict:
    """上传 qa-agent 更新 zip（含 app/、tools/、scripts/），可选自动重启."""
    _require_token(x_deploy_token)

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
        background_tasks.add_task(
            schedule_restart,
            settings.config_json_path or None,
            settings.qa_agent_log_dir or None,
        )

    logger.info("deploy update applied: %s files", result.get("files_updated"))
    return {
        "status": "accepted",
        "message": "更新已写入，服务即将重启" if restart else "更新已写入，未请求重启",
        "restart_scheduled": restart,
        **result,
    }


@router.post("/restart")
def deploy_restart(
    background_tasks: BackgroundTasks,
    x_deploy_token: str | None = Header(default=None, alias="X-Deploy-Token"),
) -> dict:
    """仅重启 qa-agent（不更新文件）."""
    _require_token(x_deploy_token)
    background_tasks.add_task(
        schedule_restart,
        settings.config_json_path or None,
        settings.qa_agent_log_dir or None,
    )
    return {"status": "accepted", "message": "重启已调度"}
