"""Tests for qa-agent path sandbox."""

from __future__ import annotations

import pytest

from app.services.path_sandbox import PathSandboxError, resolve_under_root


def test_resolve_under_root_ok(tmp_path):
    root = tmp_path / "qa-agent"
    root.mkdir()
    p = resolve_under_root(root, "app/main.py")
    assert p == (root / "app" / "main.py").resolve()


def test_resolve_rejects_traversal(tmp_path):
    root = tmp_path / "qa-agent"
    root.mkdir()
    with pytest.raises(PathSandboxError):
        resolve_under_root(root, "../outside/secret.txt")


def test_resolve_rejects_absolute(tmp_path):
    root = tmp_path / "qa-agent"
    root.mkdir()
    with pytest.raises(PathSandboxError):
        resolve_under_root(root, "D:/etc/passwd")
