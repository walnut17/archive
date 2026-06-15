"""qa-agent 自更新：接收 zip 包、覆盖 app/tools/scripts、调度重启."""

from __future__ import annotations

import io
import logging
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

QA_AGENT_ROOT = Path(__file__).resolve().parents[2]
REPO_ROOT = QA_AGENT_ROOT.parent
ALLOWED_TOP_DIRS = frozenset({"app", "tools", "scripts"})
ALLOWED_ROOT_FILES = frozenset({"VERSION"})
MAX_ZIP_BYTES = 50 * 1024 * 1024
MAX_ZIP_FILES = 500
VERSION_FILE = QA_AGENT_ROOT / "VERSION"


def read_version() -> str:
    if VERSION_FILE.is_file():
        return VERSION_FILE.read_text(encoding="utf-8").strip() or "unknown"
    return os.environ.get("QA_AGENT_VERSION", "dev")


def deploy_enabled(deploy_token: str) -> bool:
    return bool(deploy_token.strip())


def verify_deploy_token(provided: str, expected: str) -> bool:
    import hmac

    if not expected.strip():
        return False
    return hmac.compare_digest(provided.strip(), expected.strip())


def _normalize_zip_member(name: str) -> str | None:
    normalized = name.replace("\\", "/").strip()
    while normalized.startswith("./"):
        normalized = normalized[2:]
    parts = [p for p in normalized.split("/") if p and p != "."]
    if not parts or ".." in parts:
        return None
    if parts[0] == "qa-agent":
        parts = parts[1:]
    if not parts:
        return None
    if len(parts) == 1 and parts[0] in ALLOWED_ROOT_FILES:
        return parts[0]
    if parts[0] not in ALLOWED_TOP_DIRS:
        return None
    return "/".join(parts)


def apply_zip_update(zip_bytes: bytes) -> dict[str, Any]:
    """解压 zip 到 qa-agent 目录（仅允许 app/tools/scripts）."""
    if len(zip_bytes) > MAX_ZIP_BYTES:
        raise ValueError(f"zip 超过上限 {MAX_ZIP_BYTES} 字节")

    staging = Path(tempfile.mkdtemp(prefix="qa-agent-update-"))
    backup_root = QA_AGENT_ROOT / ".update_backup" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    written: list[str] = []
    try:
        with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
            if len(zf.infolist()) > MAX_ZIP_FILES:
                raise ValueError(f"zip 文件数超过上限 {MAX_ZIP_FILES}")

            for info in zf.infolist():
                if info.is_dir():
                    continue
                rel = _normalize_zip_member(info.filename)
                if rel is None:
                    raise ValueError(f"不允许的路径: {info.filename}")
                target = staging / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                with zf.open(info) as src, open(target, "wb") as dst:
                    shutil.copyfileobj(src, dst)
                written.append(rel)

        if not written:
            raise ValueError("zip 内没有可更新的文件")

        backup_root.mkdir(parents=True, exist_ok=True)
        for rel in written:
            src_live = QA_AGENT_ROOT / rel
            if src_live.exists():
                backup_dest = backup_root / rel
                backup_dest.parent.mkdir(parents=True, exist_ok=True)
                if src_live.is_dir():
                    shutil.copytree(src_live, backup_dest, dirs_exist_ok=True)
                else:
                    shutil.copy2(src_live, backup_dest)

            staged = staging / rel
            live = QA_AGENT_ROOT / rel
            live.parent.mkdir(parents=True, exist_ok=True)
            if staged.is_dir():
                if live.exists():
                    shutil.rmtree(live)
                shutil.copytree(staged, live)
            else:
                shutil.copy2(staged, live)

        for cache_dir in QA_AGENT_ROOT.rglob("__pycache__"):
            shutil.rmtree(cache_dir, ignore_errors=True)

        return {
            "files_updated": len(written),
            "paths": written[:20],
            "backup_dir": str(backup_root),
            "version": read_version(),
        }
    finally:
        shutil.rmtree(staging, ignore_errors=True)


def schedule_restart(config_json: str | None = None, log_dir: str | None = None) -> None:
    """后台拉起 PowerShell 脚本：stop + start（仅 Windows 生产路径）."""
    script = QA_AGENT_ROOT / "scripts" / "apply_update_and_restart.ps1"
    if not script.is_file():
        logger.error("缺少重启脚本: %s", script)
        return

    if sys.platform != "win32":
        logger.warning("非 Windows 环境，跳过自动重启；请手工重启 qa-agent")
        return

    args = [
        "powershell.exe",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        str(script),
        "-RepoRoot",
        str(REPO_ROOT),
    ]
    if config_json:
        args.extend(["-ConfigJson", config_json])
    if log_dir:
        args.extend(["-LogDir", log_dir])

    creationflags = 0
    if hasattr(subprocess, "CREATE_NEW_PROCESS_GROUP"):
        creationflags |= subprocess.CREATE_NEW_PROCESS_GROUP
    if hasattr(subprocess, "DETACHED_PROCESS"):
        creationflags |= subprocess.DETACHED_PROCESS

    subprocess.Popen(
        args,
        cwd=str(QA_AGENT_ROOT),
        creationflags=creationflags,
        close_fds=True,
    )
    logger.info("已调度重启脚本: %s", script)
