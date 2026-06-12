"""archive_fs: 只读访问 D:/archive 材料目录 (list/grep/read)."""

import logging
import os
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

# 白名单扩展名
TEXT_EXTENSIONS = {".txt", ".md", ".csv", ".json", ".xml", ".html"}
MAX_LIST = 100
MAX_GREP_LINES = 200
MAX_READ_BYTES = 512 * 1024  # 512KB


def _get_roots(ctx: dict[str, Any]) -> tuple[Path, Path]:
    file_root = Path(os.environ.get("FILE_ROOT", "D:/archive/files"))
    parsed_root = Path(os.environ.get("PARSED_ROOT", "D:/archive/parsed"))
    return file_root.resolve(), parsed_root.resolve()


def _resolve(zone: str, relative: str, ctx: dict[str, Any]) -> Path | None:
    file_root, parsed_root = _get_roots(ctx)
    root = file_root if zone == "files" else parsed_root
    resolved = (root / relative).resolve()
    # 检查是否在 root 下
    try:
        resolved.relative_to(root)
    except ValueError:
        logger.warning("路径越界: zone=%s, path=%s", zone, resolved)
        return None
    return resolved


def run(args: dict[str, Any], ctx: dict[str, Any]) -> dict[str, Any]:
    action = args.get("action")
    zone = args.get("zone", "parsed")
    relative_path = args.get("relativePath")
    pattern = args.get("pattern", "")
    max_lines = min(args.get("maxLines", 100), MAX_GREP_LINES)

    if not relative_path:
        return {"error": "缺少 relativePath 参数"}

    resolved = _resolve(zone, relative_path, ctx)
    if resolved is None:
        return {"error": "路径越界或不存在"}

    if action == "list":
        return _do_list(resolved)
    elif action == "grep":
        return _do_grep(resolved, pattern, max_lines)
    elif action == "read":
        return _do_read(resolved)
    else:
        return {"error": f"未知 action: {action}，支持: list/grep/read"}


def _do_list(path: Path) -> dict[str, Any]:
    if not path.is_dir():
        return {"error": f"不是目录: {path}"}

    entries = []
    try:
        for p in sorted(path.iterdir())[:MAX_LIST]:
            entries.append({
                "name": p.name,
                "isDir": p.is_dir(),
                "sizeBytes": p.stat().st_size if p.is_file() else 0,
                "lastModified": int(p.stat().st_mtime * 1000),
            })
    except PermissionError:
        return {"error": "无权限访问目录"}

    return {
        "entries": entries,
        "total": len(entries),
        "truncated": len(entries) >= MAX_LIST,
    }


def _do_grep(path: Path, pattern: str, max_lines: int) -> dict[str, Any]:
    if not path.is_file():
        return {"error": f"不是文件: {path}"}
    if path.suffix.lower() not in TEXT_EXTENSIONS:
        return {"error": f"不支持的文件类型: {path.suffix}"}

    matches = []
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            for lineno, line in enumerate(f, 1):
                if pattern in line:
                    matches.append({"lineNo": lineno, "text": line.strip()})
                    if len(matches) >= max_lines:
                        break
    except Exception as e:
        return {"error": f"读取失败: {e}"}

    return {
        "matches": matches,
        "totalMatches": len(matches),
        "truncated": len(matches) >= max_lines,
    }


def _do_read(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {"error": f"不是文件: {path}"}
    if path.suffix.lower() not in TEXT_EXTENSIONS:
        return {"error": f"不支持的文件类型: {path.suffix}"}

    try:
        size = path.stat().st_size
        with open(path, "rb") as f:
            data = f.read(MAX_READ_BYTES)
        content = data.decode("utf-8", errors="replace")
        truncated = size > MAX_READ_BYTES
        if truncated:
            content += "\n\n... (截断)"
        return {"content": content, "fileSizeBytes": size, "truncated": truncated}
    except Exception as e:
        return {"error": f"读取失败: {e}"}
