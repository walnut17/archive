"""Tests for ReAct iteration helpers (T-0612-07)."""

from __future__ import annotations

import json

from app.agent.react_helpers import (
    append_step_hints,
    evidence_search_query,
    maybe_upgrade_step,
    question_needs_material_evidence,
    question_needs_material_stats,
    try_recover_material_count_loop,
)


def test_question_needs_material_stats():
    assert question_needs_material_stats("lmz项目下有多少份材料？")
    assert not question_needs_material_stats("今天天气怎么样？")


def test_question_needs_material_evidence():
    assert question_needs_material_evidence("lmz项目的利率是多少？")
    assert not question_needs_material_evidence("今天天气怎么样？")


def test_extract_rate_from_texts():
    from app.agent.react_helpers import extract_rate_from_texts

    assert extract_rate_from_texts(["本项目为固定收益15%"]) == "15%"
    assert extract_rate_from_texts(["利率为 12.5%"]) == "12.5%"


def test_maybe_upgrade_step_evidence_to_search():
    steps = [
        {
            "iteration": 1,
            "tool": "find_project",
            "toolArgs": "{}",
            "observation": json.dumps(
                [{"projectCode": "shtx26007", "projectName": "lmz授信", "code": "shtx26007"}]
            ),
        }
    ]
    next_step = {
        "iteration": 2,
        "thought": "拿汇总",
        "tool": "get_project_business_data",
        "toolArgs": json.dumps({"projectCode": "shtx26007"}),
    }
    upgraded = maybe_upgrade_step(next_step, steps, "lmz项目的利率是多少？")
    assert upgraded["tool"] == "search_fulltext"
    assert "shtx26007" in upgraded["toolArgs"]
    assert "利率" in evidence_search_query("lmz项目的利率是多少？")


def test_maybe_upgrade_step_after_find_hit():
    steps = [
        {
            "iteration": 1,
            "tool": "find_project",
            "toolArgs": json.dumps({"query": "lmz项目", "topN": 3}),
            "observation": json.dumps(
                [{"projectCode": "PRJ-2025-088", "projectName": "lmz授信", "code": "PRJ-2025-088"}]
            ),
        }
    ]
    next_step = {
        "iteration": 2,
        "thought": "再 find 一次",
        "tool": "find_project",
        "toolArgs": json.dumps({"query": "lmz项目", "topN": 3}),
    }
    upgraded = maybe_upgrade_step(next_step, steps, "lmz项目下有多少份材料？")
    assert upgraded["tool"] == "get_project_business_data"
    assert "PRJ-2025-088" in upgraded["toolArgs"]


def test_append_step_hints_includes_material_guidance():
    steps = [
        {
            "iteration": 1,
            "tool": "find_project",
            "toolArgs": "{}",
            "observation": json.dumps([{"projectCode": "PRJ-1", "projectName": "demo"}]),
        }
    ]
    parts: list[str] = []
    append_step_hints(parts, "lmz项目下有多少份材料？", steps)
    text = "\n".join(parts)
    assert "get_project_business_data" in text
    assert "禁止再次 find_project" in text


def test_maybe_upgrade_step_after_biz_data():
    steps = [
        {
            "iteration": 1,
            "tool": "get_project_business_data",
            "toolArgs": json.dumps({"projectCode": "PRJ-1"}),
            "observation": json.dumps(
                {"projectCode": "PRJ-1", "projectName": "lmz授信", "materialCount": 2}
            ),
        }
    ]
    next_step = {
        "iteration": 2,
        "thought": "再查一次",
        "tool": "get_project_business_data",
        "toolArgs": json.dumps({"projectCode": "PRJ-1"}),
    }
    upgraded = maybe_upgrade_step(next_step, steps, "lmz项目下有多少份材料？")
    assert upgraded["tool"] == "FINAL_ANSWER"
    assert "2 份材料" in upgraded["toolArgs"]


def test_try_recover_duplicate_business_data():
    steps = [
        {
            "iteration": 2,
            "tool": "get_project_business_data",
            "toolArgs": json.dumps({"projectCode": "PRJ-1"}),
            "observation": json.dumps(
                {"projectCode": "PRJ-1", "projectName": "lmz授信", "materialCount": 2}
            ),
        },
        {
            "iteration": 3,
            "tool": "get_project_business_data",
            "toolArgs": json.dumps({"projectCode": "PRJ-1"}),
            "observation": json.dumps(
                {"projectCode": "PRJ-1", "projectName": "lmz授信", "materialCount": 2}
            ),
        },
    ]

    answer, extra = try_recover_material_count_loop(
        steps, "lmz项目下有多少份材料？", 3, {}, lambda *a: None, lambda s, n=2000: s
    )
    assert answer is not None
    assert "2 份材料" in answer
    assert extra[-1]["tool"] == "FINAL_ANSWER"


def test_try_recover_material_count_loop():
    steps = [
        {
            "iteration": 1,
            "tool": "find_project",
            "toolArgs": json.dumps({"query": "lmz项目"}),
            "observation": json.dumps([{"projectCode": "PRJ-1", "projectName": "lmz授信"}]),
        },
        {
            "iteration": 2,
            "tool": "find_project",
            "toolArgs": json.dumps({"query": "lmz项目"}),
            "observation": json.dumps([{"projectCode": "PRJ-1", "projectName": "lmz授信"}]),
        },
    ]

    def fake_dispatch(tool, args, ctx):
        assert tool == "get_project_business_data"
        return {"materialCount": 7, "projectCode": "PRJ-1", "projectName": "lmz授信"}

    answer, extra = try_recover_material_count_loop(
        steps, "lmz项目下有多少份材料？", 2, {}, fake_dispatch, lambda s, n=2000: s
    )
    assert answer is not None
    assert "7 份材料" in answer
    assert len(extra) == 2
    assert extra[-1]["tool"] == "FINAL_ANSWER"
