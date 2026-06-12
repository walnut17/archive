from app.agent.parser import FINAL_ANSWER, parse_agent_step, final_answer_from_args


def test_parse_valid_json():
    raw = '{"thought": "查项目", "tool": "find_project", "args": {"query": "新能源", "topN": 3}}'
    step = parse_agent_step(raw, 1)
    assert step["tool"] == "find_project"
    assert "新能源" in step["toolArgs"]


def test_parse_final_answer():
    raw = '{"thought": "拒答", "tool": "FINAL_ANSWER", "args": {"answer": "只能答档案问题"}}'
    step = parse_agent_step(raw, 1)
    assert step["tool"] == FINAL_ANSWER
    assert final_answer_from_args(step["toolArgs"]) == "只能答档案问题"


def test_parse_invalid_returns_polite_reject():
    step = parse_agent_step("今天天气不错", 1)
    assert step["tool"] == FINAL_ANSWER
    assert "档案" in final_answer_from_args(step["toolArgs"])
