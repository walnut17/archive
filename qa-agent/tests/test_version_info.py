"""version_info 与 deploy version API 测试."""

from __future__ import annotations

from app.services.version_info import compare_git_sha, get_runtime_version


def test_get_runtime_version_shape():
    v = get_runtime_version()
    assert v["service"] == "qa-agent"
    assert "git_sha" in v
    assert isinstance(v["features"], dict)
    assert "evidence_routing" in v["features"]
    assert "process_started_at" in v


def test_get_runtime_version_detailed():
    v = get_runtime_version(detailed=True)
    assert "qa_agent_root" in v
    assert "version_file_exists" in v
    assert "disk_git_sha" in v
    assert "pending_restart" in v
    assert v["pending_restart"] is False


def test_compare_git_sha():
    assert compare_git_sha("abc123", "abc123") == "match"
    assert compare_git_sha("abc123", "def456") == "diff"
    assert compare_git_sha("dev", "abc123") == "unknown"


def test_deploy_version_endpoint(api_client):
    resp = api_client.get("/v1/deploy/version")
    assert resp.status_code == 200
    data = resp.json()
    assert data["service"] == "qa-agent"
    assert "git_sha" in data
    assert "features" in data
