"""v1.1: find_project._fmt 5 级隐式切换决策表单测."""

import pytest
from app.agent.tools.find_project import _fmt


def _row(code="PRJ-001", name="测试项目", customer_name="客户A"):
    return {"id": 1, "code": code, "name": name, "customer_name": customer_name,
            "status": "贷后", "amount_wan": 1000}


class TestFindProjectV11:
    """_fmt 各 switchDecision 分支."""

    # ── 有锁定 ──────────────────────────────────────────

    @pytest.mark.parametrize("locked,conf,exp_sd", [
        ("PRJ-001", 0.96, "SAME_CONFIRMED"),   # conf>=0.95
        ("PRJ-001", 0.95, "SAME_CONFIRMED"),
        ("PRJ-001", 0.80, "SAME_PROBABLY"),    # 0.7~0.95
        ("PRJ-001", 0.70, "SAME_PROBABLY"),
        ("PRJ-001", 0.60, "UNCLEAR"),           # <0.7
        ("PRJ-001", 0.00, "UNCLEAR"),
    ])
    def test_locked_same(self, locked, conf, exp_sd):
        ctx = {"project_code": locked}
        r = _fmt(_row(), conf, ctx)
        assert r["switchDecision"] == exp_sd, f"locked same {conf} → {exp_sd}"

    @pytest.mark.parametrize("locked,conf,exp_sd", [
        ("PRJ-002", 0.85, "DIFFERENT_PROBABLY"),  # conf>=0.7
        ("PRJ-002", 0.70, "DIFFERENT_PROBABLY"),
        ("PRJ-002", 0.50, "UNCLEAR"),              # <0.7
    ])
    def test_locked_different(self, locked, conf, exp_sd):
        ctx = {"project_code": locked}
        r = _fmt(_row(), conf, ctx)
        assert r["switchDecision"] == exp_sd, f"locked diff {conf} → {exp_sd}"

    # ── 无锁定 ──────────────────────────────────────────

    @pytest.mark.parametrize("conf,exp_sd,exp_lock", [
        (0.96, "SAME_CONFIRMED", True),   # conf>=0.95 → 自动锁定
        (0.95, "SAME_CONFIRMED", True),
        (0.80, "SAME_PROBABLY", False),   # 0.7~0.95 → 不锁定
        (0.70, "SAME_PROBABLY", False),
        (0.60, "UNCLEAR", False),          # <0.7
        (0.00, "UNCLEAR", False),
    ])
    def test_no_lock(self, conf, exp_sd, exp_lock):
        ctx: dict = {"project_code": None}
        r = _fmt(_row(), conf, ctx)
        assert r["switchDecision"] == exp_sd, f"no-lock {conf} → {exp_sd}"
        if exp_lock:
            assert ctx.get("project_code") == "PRJ-001"
        else:
            assert ctx.get("project_code") is None

    # ── v1.1 字段 ───────────────────────────────────────

    def test_v11_fields_present(self):
        ctx: dict = {"project_code": None}
        r = _fmt(_row(), 0.95, ctx)
        assert r.get("projectCode") == "PRJ-001"
        assert r.get("projectName") == "测试项目"
        assert r.get("switchDecision") is not None
        assert r.get("confidence") == 0.95

    def test_agent_source_fields(self):
        ctx: dict = {"project_code": None}
        r = _fmt(_row(), 0.95, ctx)
        assert r.get("projectCode")  # 用于 agent_sources[].id
        assert r.get("projectName")  # 用于 agent_sources[].title
