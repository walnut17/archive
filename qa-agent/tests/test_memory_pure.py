"""v1.2 项目锁单测 (纯函数, 不依赖 DB)."""
import sys
import os
import re
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

# 单独复制 resolve / detect 逻辑测, 不 import db_cursor
import importlib.util
spec = importlib.util.spec_from_file_location(
    "memory_pure",
    os.path.join(os.path.dirname(__file__), "..", "app", "services", "memory.py")
)

# 临时 monkey-patch 把 db_cursor 替换成 dummy
import app.db.connection as _db
_db.db_cursor = lambda: (_ for _ in ()).throw(NotImplementedError("db not patched"))

# 实际我们只要 _detect_reference / resolve_project_reference, 不需要真 db
# 改用 namespace 拿
import types
fake_db = types.ModuleType("app.db.connection")
fake_db.db_cursor = lambda *a, **kw: None
sys.modules["app.db.connection"] = fake_db

from app.services.memory import resolve_project_reference, _detect_reference


def test_detect_reference_basic():
    """指代词检测: 含「它/那个项目/剩余金额/材料」应识别."""
    assert _detect_reference("它的剩余金额多少?")
    assert _detect_reference("那个项目有材料吗?")
    assert _detect_reference("最新进展?")
    assert _detect_reference("它")


def test_resolve_injects_project_code():
    q = "剩余金额多少?"
    resolved = resolve_project_reference(q, "PRJ-2026-001")
    assert "PRJ-2026-001" in resolved
    assert q in resolved  # 原问题保留


def test_resolve_no_op_when_already_has_code():
    q2 = "PRJ-2026-001 剩余金额多少?"
    assert resolve_project_reference(q2, "PRJ-2026-001") == q2


def test_resolve_no_op_when_no_code():
    assert resolve_project_reference("它的金额", None) == "它的金额"
    assert resolve_project_reference("它的金额", "") == "它的金额"


if __name__ == "__main__":
    test_detect_reference_basic()
    print("✓ test_detect_reference_basic")
    test_resolve_injects_project_code()
    print("✓ test_resolve_injects_project_code")
    test_resolve_no_op_when_already_has_code()
    print("✓ test_resolve_no_op_when_already_has_code")
    test_resolve_no_op_when_no_code()
    print("✓ test_resolve_no_op_when_no_code")
    print("\nAll pure memory tests passed")
