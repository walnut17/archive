"""Tests for ReAct iteration helpers (T-0612-07)."""

from __future__ import annotations

import json

from app.agent.react_helpers import (
    append_step_hints,
    compact_search_hits_for_obs,
    evidence_search_query,
    maybe_upgrade_step,
    proposal_count_answer_from_biz,
    question_needs_material_evidence,
    question_needs_material_stats,
    question_needs_proposal_stats,
    try_finalize_evidence_from_search,
    try_recover_material_count_loop,
)


def test_question_needs_material_stats():
    assert question_needs_material_stats("lmz项目下有多少份材料？")
    assert not question_needs_material_stats("今天天气怎么样？")


def test_question_needs_material_evidence():
    assert question_needs_material_evidence("lmz项目的利率是多少？")
    assert not question_needs_material_evidence("今天天气怎么样？")


def test_question_needs_debt_target_evidence():
    from app.agent.react_helpers import question_needs_debt_target_evidence

    assert question_needs_debt_target_evidence("lmz项目远期回购的债权标的是什么？")
    assert question_needs_material_evidence("lmz项目远期回购的债权标的是什么？")


def test_extract_debt_target_from_title():
    from app.agent.react_helpers import extract_debt_target_from_hits

    hits = [
        {
            "materialTitle": "债权转让暨远期回购协议书（岭兜建材二厂）20260601",
            "parsedExcerpt": "甲方：泉州市正德信息咨询有限公司",
        }
    ]
    assert extract_debt_target_from_hits(hits) == "南安市岭兜建材二厂债权"


def test_synthesize_debt_target_answer():
    from app.agent.react_helpers import synthesize_evidence_answer

    hits = [
        {
            "materialTitle": "债权转让暨远期回购协议书（岭兜建材二厂）20260601",
            "parsedExcerpt": "目标债权为南安市岭兜建材二厂债权",
        }
    ]
    ans = synthesize_evidence_answer(
        "lmz项目远期回购的债权标的是什么？",
        "lmz授信",
        "shtx26007",
        hits,
    )
    assert ans is not None
    assert "南安市岭兜建材二厂债权" in ans
    assert "shtx26007" in ans


def test_question_needs_collateral_evidence():
    from app.agent.react_helpers import question_needs_collateral_evidence

    assert question_needs_collateral_evidence("这个债权的抵押物是什么？")
    assert question_needs_material_evidence("这个债权的抵押物是什么？")


def test_evidence_search_query_collateral_with_debt():
    from app.agent.react_helpers import evidence_search_query

    q = evidence_search_query(
        "岭兜建材二厂 这个债权的抵押物是什么？",
        debt_target="南安市岭兜建材二厂债权",
    )
    assert "抵押物" in q
    assert "岭兜" in q
    assert "建材二厂" in q


def test_extract_collateral_items_with_valuation():
    from app.agent.react_helpers import extract_collateral_items_from_texts

    text = (
        "一、债权抵押物基本情况。\n"
        "第一笔贷款本金145万元，以债务人名下南安市11.8亩工业土地抵押，"
        "土地上有上盖无证厂房8617平米；他项权证记载抵押金额为354.18万元；"
        "债务人已偿还了土地抵押贷款本金145万元。\n"
        "第二笔贷款本金400万元，以设备抵押。"
    )
    items = extract_collateral_items_from_texts([text])
    assert len(items) == 2
    assert "11.8亩" in items[0]["name"]
    assert "8617" in items[0]["name"]
    assert "354.18" in items[0]["initialValue"]
    assert "145" in items[0]["statusNote"]
    assert items[1]["name"] == "设备抵押"
    assert "400" in items[1]["initialValue"]


def test_question_needs_collateral_detail():
    from app.agent.react_helpers import question_needs_collateral_detail

    assert question_needs_collateral_detail(
        "岭兜建材二厂债权下的抵押物还剩哪些，初始估值分别是多少？"
    )
    assert not question_needs_collateral_detail("这个债权的抵押物是什么？")


def test_extract_debt_anchor_from_question():
    from app.agent.react_helpers import extract_debt_anchor_from_question

    assert (
        extract_debt_anchor_from_question("岭兜建材二厂债权下的抵押物还剩哪些？")
        == "南安市岭兜建材二厂债权"
    )
    assert extract_debt_anchor_from_question("lmz项目利率多少？") is None
    assert extract_debt_anchor_from_question("这个债权的抵押物是什么？") is None
    assert extract_debt_anchor_from_question("这个债权的抵押物是什么？") is None


def test_balance_question_not_material_evidence():
    assert not question_needs_material_evidence("剩余金额多少?")
    assert not question_needs_material_evidence("它的剩余金额多少?")


def test_evidence_search_query_collateral_detail():
    from app.agent.react_helpers import evidence_search_query

    q = evidence_search_query(
        "岭兜建材二厂债权下的抵押物还剩哪些，初始估值分别是多少？",
        debt_target="南安市岭兜建材二厂债权",
    )
    assert "抵押物" in q
    assert "估值" in q
    assert "抵押金额" in q


def test_synthesize_collateral_detail_answer():
    from app.agent.react_helpers import synthesize_evidence_answer

    hits = [
        {
            "materialTitle": "投资申请报告",
            "parsedExcerpt": (
                "一、债权抵押物基本情况。\n"
                "第一笔贷款本金145万元，以债务人名下南安市11.8亩工业土地抵押，"
                "土地上有上盖无证厂房8617平米；他项权证记载抵押金额为354.18万元；"
                "债务人已偿还了土地抵押贷款本金145万元。\n"
                "第二笔贷款本金400万元，以设备抵押。"
            ),
        }
    ]
    ans = synthesize_evidence_answer(
        "岭兜建材二厂债权下的抵押物还剩哪些，初始估值分别是多少？",
        "lmz授信",
        "shtx26007",
        hits,
        debt_target="南安市岭兜建材二厂债权",
    )
    assert ans is not None
    assert "剩余抵押物及初始估值" in ans
    assert "354.18" in ans
    assert "400" in ans
    assert "设备抵押" in ans


def test_extract_collateral_from_texts():
    from app.agent.react_helpers import extract_collateral_from_texts

    text = (
        "一、债权抵押物基本情况。\n"
        "债务人是福建省南安市岭兜建材二厂。第一笔贷款本金145万元，"
        "以债务人名下南安市11.8亩工业土地抵押，土地上有上盖无证厂房8617平米；"
        "第二笔贷款本金400万元，以设备抵押。"
    )
    collateral = extract_collateral_from_texts([text])
    assert collateral is not None
    assert "11.8亩" in collateral
    assert "8617" in collateral
    assert "设备抵押" in collateral


def test_synthesize_collateral_answer():
    from app.agent.react_helpers import synthesize_evidence_answer

    hits = [
        {
            "materialTitle": "投资申请报告",
            "parsedExcerpt": (
                "一、债权抵押物基本情况。\n"
                "第一笔贷款以债务人名下南安市11.8亩工业土地抵押，"
                "土地上有上盖无证厂房8617平米；第二笔贷款以设备抵押。"
            ),
        }
    ]
    ans = synthesize_evidence_answer(
        "岭兜建材二厂 这个债权的抵押物是什么？",
        "lmz授信",
        "shtx26007",
        hits,
        debt_target="南安市岭兜建材二厂债权",
    )
    assert ans is not None
    assert "抵押物包括" in ans
    assert "南安市岭兜建材二厂债权" in ans
    assert "11.8亩" in ans


def test_try_finalize_collateral_from_search():
    from app.agent.react_helpers import try_finalize_evidence_from_search

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
    hits = [
        {
            "materialTitle": "投资申请报告",
            "parsedExcerpt": (
                "债权抵押物基本情况。11.8亩工业土地抵押，无证厂房8617平米；设备抵押。"
            ),
            "snippet": "债权抵押物基本情况。11.8亩工业土地抵押",
        }
    ]
    answer = try_finalize_evidence_from_search(
        "岭兜建材二厂 抵押物是什么？",
        steps,
        hits,
        {
            "project_code": "shtx26007",
            "last_debt_target": "南安市岭兜建材二厂债权",
        },
    )
    assert answer is not None
    assert "抵押物包括" in answer


def test_question_needs_proposal_stats():
    assert question_needs_proposal_stats("lmz项目下面对应几次投委会议案？")
    assert not question_needs_proposal_stats("lmz项目下有多少份材料？")


def test_proposal_count_answer_from_biz():
    ans = proposal_count_answer_from_biz(
        {
            "projectCode": "shtx26007",
            "projectName": "lmz授信",
            "proposalCount": 1,
            "committeeProposalCount": 1,
            "maintenanceBundleCount": 0,
            "proposals": [{"code": "shtx26007", "title": "投资申请", "type": "申请", "status": "通过"}],
        }
    )
    assert "1 次投委会议案" in ans
    assert "正式审议" in ans
    assert "shtx26007" in ans


def test_maybe_upgrade_step_query_mysql_to_get_for_proposals():
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
        "thought": "查议案",
        "tool": "query_mysql",
        "toolArgs": json.dumps(
            {
                "table": "proposal",
                "where": [{"column": "project_code", "operator": "=", "value": "shtx26007"}],
            }
        ),
    }
    upgraded = maybe_upgrade_step(next_step, steps, "lmz项目下面对应几次投委会议案？")
    assert upgraded["tool"] == "get_project_business_data"
    assert "shtx26007" in upgraded["toolArgs"]


def test_extract_rate_from_texts():
    from app.agent.react_helpers import extract_rate_from_texts

    assert extract_rate_from_texts(["本项目为固定收益15%"]) == "15%"
    assert extract_rate_from_texts(["利率为 12.5%"]) == "12.5%"
    assert extract_rate_from_texts(["回购利率为 18%"]) == "18%"


def test_try_finalize_evidence_from_search():
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
    hits = [
        {
            "materialTitle": "投资申请报告",
            "parsedExcerpt": "本项目采用固定收益15%的方式",
            "snippet": "本项目采用固定收益15%的方式",
        }
    ]
    answer = try_finalize_evidence_from_search(
        "lmz项目的利率是多少？", steps, hits, {"project_code": "shtx26007"}
    )
    assert answer is not None
    assert "15%" in answer
    assert "shtx26007" in answer


def test_compact_search_hits_for_obs_keeps_snippet():
    hits = [
        {
            "materialTitle": "合同",
            "parsedExcerpt": "固定收益15%" + ("x" * 5000),
            "snippet": "固定收益15%",
            "projectCode": "shtx26007",
            "versionId": 99,
            "proposalTitle": "很长的议案标题" * 20,
        }
    ]
    compact = compact_search_hits_for_obs(hits)
    assert len(compact) == 1
    assert "15%" in compact[0]["snippet"]
    assert len(json.dumps(compact, ensure_ascii=False)) < 500


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
