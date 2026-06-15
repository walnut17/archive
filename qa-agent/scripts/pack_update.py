#!/usr/bin/env python3
"""Pack qa-agent app/tools/scripts into a deploy zip (for POST /v1/deploy/update)."""
from __future__ import annotations

import argparse
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INCLUDE_DIRS = ("app", "tools", "scripts")
SKIP_PARTS = {".pyc", "__pycache__", ".update_backup"}


def git_short_sha() -> str:
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=ROOT.parent,
            stderr=subprocess.DEVNULL,
            text=True,
        )
        return out.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unknown"


def pack(out_path: Path) -> int:
    version = git_short_sha()
    (ROOT / "VERSION").write_text(version + "\n", encoding="utf-8")

    count = 0
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for top in INCLUDE_DIRS:
            base = ROOT / top
            if not base.is_dir():
                continue
            for path in base.rglob("*"):
                if not path.is_file():
                    continue
                if any(part in SKIP_PARTS for part in path.parts):
                    continue
                rel = path.relative_to(ROOT).as_posix()
                zf.write(path, rel)
                count += 1
    print(f"[OK] {count} files -> {out_path} (version={version})")
    return count


def main() -> int:
    parser = argparse.ArgumentParser(description="打包 qa-agent 热更新 zip")
    parser.add_argument(
        "-o",
        "--output",
        default="",
        help="输出路径，默认 qa-agent/dist/qa-agent-update-<timestamp>.zip",
    )
    args = parser.parse_args()
    if args.output:
        out = Path(args.output)
    else:
        dist = ROOT / "dist"
        dist.mkdir(exist_ok=True)
        ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        out = dist / f"qa-agent-update-{ts}.zip"
    out.parent.mkdir(parents=True, exist_ok=True)
    if pack(out) == 0:
        print("[WARN] zip 为空")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
