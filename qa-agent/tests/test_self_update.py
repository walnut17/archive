"""Tests for qa-agent self-update (zip apply + deploy API)."""

from __future__ import annotations

import io
import zipfile
from pathlib import Path
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import self_update as su


@pytest.fixture
def qa_root(tmp_path: Path, monkeypatch) -> Path:
    (tmp_path / "app").mkdir()
    (tmp_path / "tools").mkdir()
    (tmp_path / "scripts").mkdir()
    monkeypatch.setattr(su, "QA_AGENT_ROOT", tmp_path)
    monkeypatch.setattr(su, "REPO_ROOT", tmp_path.parent)
    return tmp_path


def _make_zip(files: dict[str, bytes]) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        for name, data in files.items():
            zf.writestr(name, data)
    return buf.getvalue()


def test_normalize_rejects_traversal():
    assert su._normalize_zip_member("../app/x.py") is None
    assert su._normalize_zip_member("app/../secret.py") is None
    assert su._normalize_zip_member(".venv/lib.py") is None


def test_normalize_accepts_qa_agent_prefix():
    assert su._normalize_zip_member("qa-agent/app/main.py") == "app/main.py"


def test_normalize_accepts_version_file():
    assert su._normalize_zip_member("VERSION") == "VERSION"


def test_apply_zip_update_writes_files(qa_root: Path):
    payload = _make_zip(
        {
            "app/demo.txt": b"hello",
            "tools/tui.txt": b"tui",
        }
    )
    result = su.apply_zip_update(payload)
    assert result["files_updated"] == 2
    assert (qa_root / "app" / "demo.txt").read_bytes() == b"hello"
    assert (qa_root / "tools" / "tui.txt").read_bytes() == b"tui"


def test_apply_zip_rejects_empty():
    with pytest.raises(ValueError, match="没有可更新"):
        su.apply_zip_update(_make_zip({}))


@pytest.fixture
def deploy_client(monkeypatch) -> TestClient:
    monkeypatch.setattr("app.config.settings.qa_agent_deploy_token", "deploy-test-token")
    return TestClient(app)


def test_deploy_status_shows_enabled(deploy_client: TestClient):
    resp = deploy_client.get("/v1/deploy/status")
    assert resp.status_code == 200
    body = resp.json()
    assert body["deploy_enabled"] is True


def test_deploy_update_requires_token(deploy_client: TestClient, qa_root: Path):
    z = _make_zip({"app/x.py": b"1"})
    resp = deploy_client.post(
        "/v1/deploy/update",
        files={"file": ("u.zip", z, "application/zip")},
        headers={"X-Deploy-Token": "wrong"},
    )
    assert resp.status_code == 401


def test_deploy_update_applies_zip(deploy_client: TestClient, qa_root: Path, monkeypatch):
    z = _make_zip({"app/patched.py": b"ok"})
    monkeypatch.setattr(
        "app.api.deploy.schedule_restart",
        lambda *a, **k: {"ok": True, "spawn_pid": 12345},
    )
    resp = deploy_client.post(
        "/v1/deploy/update",
        files={"file": ("u.zip", z, "application/zip")},
        headers={"X-Deploy-Token": "deploy-test-token"},
        params={"restart": "false"},
    )
    assert resp.status_code == 200
    assert resp.json()["files_updated"] == 1
    assert (qa_root / "app" / "patched.py").read_bytes() == b"ok"


def test_deploy_disabled_without_token(monkeypatch):
    monkeypatch.setattr("app.config.settings.qa_agent_deploy_token", "")
    client = TestClient(app)
    resp = client.get("/v1/deploy/status")
    assert resp.json()["deploy_enabled"] is False
    resp2 = client.post("/v1/deploy/restart", headers={"X-Deploy-Token": "x"})
    assert resp2.status_code == 503
