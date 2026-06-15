"""后台深度分析框架单元测试."""

from __future__ import annotations

import json
from unittest.mock import MagicMock, patch

from app.analysis.extractor import run_template_extract, summarize_result
from app.analysis.models import AnalysisTemplate, ProjectContext
from app.analysis.templates_builtin import builtin_templates, get_builtin_template


def _ctx() -> ProjectContext:
    return ProjectContext(
        project_id=1,
        project_code="shtx26007",
        project_name="lmz授信",
        materials_text="目标债权为南安市岭兜建材二厂债权。固定收益15%。",
        material_fingerprint="abc123",
        material_version_ids=[10],
    )


def test_builtin_templates_include_core_codes():
    codes = {t.code for t in builtin_templates()}
    assert "project.investment_structure" in codes
    assert "project.interest_rate_schedule" in codes
    assert "asset.credit_profile" in codes
    assert "project.credit_inventory" in codes


def test_summarize_interest_rate():
    tpl = get_builtin_template("project.interest_rate_schedule")
    assert tpl is not None
    summary = summarize_result(
        tpl,
        {"currentRate": "15%", "rateUnit": "年化", "rateSchedule": []},
    )
    assert "15%" in summary


def test_run_template_extract_success():
    tpl = get_builtin_template("project.credit_inventory")
    assert tpl is not None
    with patch("app.analysis.extractor.glm_client") as mock_glm:
        mock_glm.chat.return_value = json.dumps(
            {"credits": [{"name": "南安市岭兜建材二厂债权"}], "notes": ""},
            ensure_ascii=False,
        )
        result = run_template_extract(tpl, _ctx())
    assert result.success
    assert result.data is not None
    assert result.data["credits"][0]["name"] == "南安市岭兜建材二厂债权"


def test_analysis_status_endpoint(api_client):
    resp = api_client.get("/v1/analysis/status")
    assert resp.status_code == 200
    body = resp.json()
    assert "enabled" in body
    assert "queue" in body
    assert isinstance(body.get("templates"), list)


def test_analysis_run_once_endpoint(api_client):
    with patch("app.analysis.worker.discover_and_enqueue", return_value=[]), patch(
        "app.analysis.worker.AnalysisWorker._process_one_job",
        return_value=False,
    ):
        resp = api_client.post("/v1/analysis/run-once")
    assert resp.status_code == 200
    body = resp.json()
    assert body["discovered"] == 0
    assert body["processed"] is False
