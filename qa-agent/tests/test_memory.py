"""v1.2 项目锁单测."""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from app.services.memory import resolve_project_reference, _detect_reference


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


if __name__ == "__main__":
    test_detect_reference()
    print("✓ test_detect_reference")
    test_resolve_project_reference()
    print("✓ test_resolve_project_reference")
    print("\nAll memory tests passed")
