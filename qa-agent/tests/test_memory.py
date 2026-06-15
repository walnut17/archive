"""v1.2 项目锁单测."""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from app.services.memory import (
    detect_debt_reference,
    resolve_debt_reference,
    resolve_project_reference,
    resolve_session_references,
    _detect_reference,
)


def test_detect_reference():
    """指代词检测: 含「它/那个项目/剩余金额/材料」应识别."""
    assert _detect_reference("它的剩余金额多少?")
    assert _detect_reference("那个项目有材料吗?")
    assert _detect_reference("最新进展?")
    assert not _detect_reference("PRJ-2026-001 剩余金额多少?")  # 含编号不算
    assert not _detect_reference("今天天气怎么样?")  # 无关问题不算


def test_resolve_project_reference():
    """resolve: 含指代词 + 有 project_code → 注入编号."""
    q = "剩余金额多少?"
    resolved = resolve_project_reference(q, "PRJ-2026-001")
    assert "PRJ-2026-001" in resolved
    assert q in resolved  # 原问题保留

    # 没指代词 → 不动
    q2 = "PRJ-2026-001 剩余金额多少?"
    assert resolve_project_reference(q2, "PRJ-2026-001") == q2

    # 无 project_code → 不动
    assert resolve_project_reference("它的金额", None) == "它的金额"

    # project_code 已含 → 不重复
    q3 = "它的金额"
    resolved3 = resolve_project_reference(q3, "PRJ-2026-001")
    assert "PRJ-2026-001 它的金额" == resolved3


def test_detect_debt_reference():
    assert detect_debt_reference("这个债权的抵押物是什么？")
    assert not detect_debt_reference("lmz项目的利率是多少？")


def test_resolve_debt_reference():
    q = "这个债权的抵押物是什么？"
    resolved = resolve_debt_reference(q, "南安市岭兜建材二厂债权")
    assert "岭兜建材二厂" in resolved
    assert q in resolved


def test_resolve_session_references_combined():
    q = "这个债权的抵押物是什么？"
    resolved = resolve_session_references(
        q, "shtx26007", "南安市岭兜建材二厂债权"
    )
    assert "shtx26007" in resolved
    assert "岭兜建材二厂" in resolved


if __name__ == "__main__":
    test_detect_reference()
    print("✓ test_detect_reference")
    test_resolve_project_reference()
    print("✓ test_resolve_project_reference")
    test_detect_debt_reference()
    print("✓ test_detect_debt_reference")
    test_resolve_debt_reference()
    print("✓ test_resolve_debt_reference")
    test_resolve_session_references_combined()
    print("✓ test_resolve_session_references_combined")
    print("\nAll memory tests passed")
