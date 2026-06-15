"""路径沙箱 — 确保文件操作限定在 qa-agent 根目录内."""

from __future__ import annotations

from pathlib import Path


class PathSandboxError(ValueError):
    """路径越界或非法."""


def resolve_under_root(root: Path, relative: str) -> Path:
    """将相对路径解析为 root 下的绝对路径；越界则抛错."""
    rel = (relative or "").replace("\\", "/").strip()
    while rel.startswith("./"):
        rel = rel[2:]
    parts = [p for p in rel.split("/") if p and p != "."]
    if ".." in parts:
        raise PathSandboxError(f"不允许的路径片段 .. : {relative}")
    if rel.startswith("/") or (len(rel) > 1 and rel[1] == ":"):
        raise PathSandboxError(f"不允许绝对路径: {relative}")

    base = root.resolve()
    target = (base.joinpath(*parts) if parts else base).resolve()
    try:
        target.relative_to(base)
    except ValueError as e:
        raise PathSandboxError(f"路径越界 qa-agent 根目录: {relative} -> {target}") from e
    return target


def assert_under_root(path: Path, root: Path) -> Path:
    """确认已解析路径在 root 内."""
    base = root.resolve()
    resolved = path.resolve()
    try:
        resolved.relative_to(base)
    except ValueError as e:
        raise PathSandboxError(f"路径不在允许目录内: {resolved}") from e
    return resolved
