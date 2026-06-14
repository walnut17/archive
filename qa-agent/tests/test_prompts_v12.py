"""v1.2 prompts 优化测例.

验证 SYSTEM_PROMPT 含关键升级点:
1. Few-shot 示例 (5 个)
2. 工具优先级 (8 工具, 排序明确)
3. 答案模板 (结构化 + 200 字内)
4. 项目锁指令 (先 find_project, 已知直接 get)
5. 降级规则 (GLM 不可用 → 后端自动, LLM 不需自己检测)
6. 死循环规则 (不同参数不算重复)
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from app.agent.prompts import (
    DEFAULT_REJECT_ANSWER,
    SYSTEM_PROMPT,
    EXTRACT_SYSTEM,
    EXTRACT_USER_TEMPLATE,
)


def test_prompt_has_8_tools():
    """8 个工具清单齐."""
    expected = [
        "find_project",
        "get_project_business_data",
        "search_fulltext",
        "query_mysql",
        "archive_fs",
        "network_dict_lookup",
        "llm_summarize",
        "ask_clarification",
    ]
    for tool in expected:
        assert tool in SYSTEM_PROMPT, f"缺工具: {tool}"


def test_prompt_has_few_shot():
    """9 个 Few-shot 示例齐."""
    for i in range(1, 10):
        assert f"示例 {i}" in SYSTEM_PROMPT


def test_prompt_priority_order():
    """工具优先级: find_project 第一, ask_clarification 在第 8 (推荐)."""
    # 找 "1. find_project" 在 prompt 中的位置
    idx = SYSTEM_PROMPT.find("1. find_project")
    idx_clarify = SYSTEM_PROMPT.find("8. ask_clarification")
    assert idx < idx_clarify, "find_project 应该在 ask_clarification 之前"


def test_prompt_answer_template():
    """答案模板: 1 句结论 + 结构化要点 + 引用 + 不超 200 字."""
    assert "答案模板" in SYSTEM_PROMPT
    assert "引用来源" in SYSTEM_PROMPT
    assert "200 字" in SYSTEM_PROMPT


def test_prompt_out_of_scope_rejection():
    """范围外礼貌拒答."""
    assert "礼貌拒答" in SYSTEM_PROMPT
    assert "天气" in SYSTEM_PROMPT
    assert DEFAULT_REJECT_ANSWER in SYSTEM_PROMPT


def test_prompt_has_scenario_routing():
    """v1.3: 按场景路由, 避免所有问题都先 find_project."""
    assert "场景路由" in SYSTEM_PROMPT
    assert "不要把所有问题都强行当成单项目问答" in SYSTEM_PROMPT
    assert "跨项目统计" in SYSTEM_PROMPT
    assert "query_mysql" in SYSTEM_PROMPT
    assert "问候 / 感谢" in SYSTEM_PROMPT
    assert "业务术语" in SYSTEM_PROMPT


def test_prompt_cross_project_does_not_force_find_project():
    """跨项目统计/列表应直接 query_mysql, 不先锁项目."""
    assert "跨项目统计 / 待办列表 / 系统能力说明 → **不要** 先 `find_project`" in SYSTEM_PROMPT
    assert "列一下所有待审议项目" in SYSTEM_PROMPT
    assert "不需要先锁定单个项目" in SYSTEM_PROMPT


def test_prompt_material_and_term_routes():
    """材料检索与术语解释有明确首选工具."""
    assert "材料正文检索" in SYSTEM_PROMPT
    assert "search_fulltext" in SYSTEM_PROMPT
    assert "术语解释" in SYSTEM_PROMPT
    assert "network_dict_lookup" in SYSTEM_PROMPT


def test_prompt_degraded_rule():
    """降级规则: LLM 不需自己检测, 后端自动."""
    assert "降级" in SYSTEM_PROMPT
    assert "GLM" in SYSTEM_PROMPT
    assert "不需要" in SYSTEM_PROMPT or "自动" in SYSTEM_PROMPT


def test_prompt_no_loop_rule():
    """死循环规则: 不同参数不算重复."""
    assert "不同参数" in SYSTEM_PROMPT or "多变体" in SYSTEM_PROMPT
    assert "settings.agent_max_iterations" in SYSTEM_PROMPT


def test_extract_template_has_max_chars():
    """EXTRACT_USER_TEMPLATE 含 max_chars 占位."""
    assert "{max_chars}" in EXTRACT_USER_TEMPLATE
    assert "{title}" in EXTRACT_USER_TEMPLATE
    assert "{content}" in EXTRACT_USER_TEMPLATE


def test_extract_system_5_fields():
    """EXTRACT_SYSTEM 列 5 字段."""
    for f in ["projectName", "amount", "customerName", "projectType", "summary"]:
        assert f in EXTRACT_SYSTEM, f"EXTRACT_SYSTEM 缺字段: {f}"


def test_prompt_size_reasonable():
    """prompt 不过长 (避免 token 浪费)."""
    size = len(SYSTEM_PROMPT)
    # 经验值: 4K-12K 字符是合理范围
    assert 4000 < size < 12000, f"SYSTEM_PROMPT 长度异常: {size}"


if __name__ == "__main__":
    test_prompt_has_8_tools()
    print("✓ test_prompt_has_8_tools")
    test_prompt_has_few_shot()
    print("✓ test_prompt_has_few_shot")
    test_prompt_priority_order()
    print("✓ test_prompt_priority_order")
    test_prompt_answer_template()
    print("✓ test_prompt_answer_template")
    test_prompt_out_of_scope_rejection()
    print("✓ test_prompt_out_of_scope_rejection")
    test_prompt_degraded_rule()
    print("✓ test_prompt_degraded_rule")
    test_prompt_no_loop_rule()
    print("✓ test_prompt_no_loop_rule")
    test_extract_template_has_max_chars()
    print("✓ test_extract_template_has_max_chars")
    test_extract_system_5_fields()
    print("✓ test_extract_system_5_fields")
    test_prompt_size_reasonable()
    print("✓ test_prompt_size_reasonable")
    print(f"\n✓ All 10 prompt tests passed  (SYSTEM_PROMPT = {len(SYSTEM_PROMPT)} chars)")
