"""schedule_restart spawn tests."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

from app.services import self_update as su


def test_schedule_restart_returns_error_when_script_missing(tmp_path, monkeypatch):
    monkeypatch.setattr(su, "QA_AGENT_ROOT", tmp_path)
    monkeypatch.setattr(su, "REPO_ROOT", tmp_path.parent)
    result = su.schedule_restart(log_dir=str(tmp_path / "logs"))
    assert result["ok"] is False
    assert "missing script" in result["error"]


def test_schedule_restart_spawns_powershell_on_windows(tmp_path, monkeypatch):
    qa = tmp_path / "qa-agent"
    scripts = qa / "scripts"
    scripts.mkdir(parents=True)
    (scripts / "apply_update_and_restart.ps1").write_text("# stub", encoding="utf-8")
    (scripts / "run_apply_restart.cmd").write_text("@echo off", encoding="utf-8")
    monkeypatch.setattr(su, "QA_AGENT_ROOT", qa)
    monkeypatch.setattr(su, "REPO_ROOT", tmp_path)
    monkeypatch.setattr(su.sys, "platform", "win32")

    proc = MagicMock()
    proc.pid = 4242
    log_dir = tmp_path / "logs"

    with patch.object(su.subprocess, "Popen", return_value=proc) as popen:
        result = su.schedule_restart(
            config_json="D:/archive/config/config.json",
            log_dir=str(log_dir),
        )

    assert result["ok"] is True
    assert result["spawn_pid"] == 4242
    popen.assert_called_once()
    args = popen.call_args[0][0]
    assert args[0] == "cmd.exe"
    assert "run_apply_restart.cmd" in args[2]
