#!/usr/bin/env python3
"""Dev machine: pack + POST qa-agent zip to remote /v1/deploy/update."""
from __future__ import annotations

import argparse
import subprocess
import sys
import time
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "scripts" / "pack_update.py"


def local_git_sha() -> str:
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=ROOT.parent,
            stderr=subprocess.DEVNULL,
            text=True,
        )
        return out.strip() or "unknown"
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unknown"


def main() -> int:
    parser = argparse.ArgumentParser(description="推送 qa-agent 热更新到远端")
    parser.add_argument("--url", default="http://182.168.1.125:8001", help="qa-agent 基址")
    parser.add_argument("--token", required=True, help="X-Deploy-Token（与 125 config qaAgent.deployToken 一致）")
    parser.add_argument("--no-restart", action="store_true", help="只更新文件，不自动重启")
    parser.add_argument("--wait", type=int, default=15, help="重启后等待 health 秒数")
    args = parser.parse_args()

    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    zip_path = dist / "_push_update.zip"

    rc = subprocess.call([sys.executable, str(PACK), "-o", str(zip_path)], cwd=str(ROOT))
    if rc != 0:
        return rc

    base = args.url.rstrip("/")
    headers = {"X-Deploy-Token": args.token.strip()}
    params = {"restart": "false" if args.no_restart else "true", "token": args.token.strip()}
    local_sha = local_git_sha()
    print(f"[INFO] 本地 git: {local_sha}")

    print(f"[INFO] POST {base}/v1/deploy/update")
    with httpx.Client(timeout=300.0) as client:
        with open(zip_path, "rb") as f:
            resp = client.post(
                f"{base}/v1/deploy/update",
                params=params,
                headers=headers,
                files={"file": ("qa-agent-update.zip", f, "application/zip")},
            )
        print(resp.status_code, resp.text)
        if resp.status_code not in (200, 202):
            return 1

        if args.no_restart:
            try:
                v = client.get(f"{base}/v1/deploy/version", timeout=5.0).json()
                remote_sha = v.get("git_sha", "unknown")
                print(f"[INFO] 远端版本(未重启): {remote_sha}")
                pending = v.get("pending_restart", False)
                if pending or local_sha != remote_sha:
                    print("[WARN] 已写盘但未重启，进程仍跑旧代码 — 请 /v1/deploy/restart 或手工 restart")
            except httpx.HTTPError:
                pass
            return 0

        print(f"[INFO] 等待重启 ({args.wait}s) ...")
        deadline = time.time() + args.wait
        while time.time() < deadline:
            try:
                h = client.get(f"{base}/health", timeout=5.0)
                if h.status_code == 200:
                    payload = h.json()
                    remote_sha = payload.get("git_sha") or payload.get("version") or "unknown"
                    print("[OK] health:", payload)
                    if local_sha == remote_sha:
                        print(f"[OK] 版本一致: {local_sha}")
                    else:
                        print(f"[WARN] 版本不一致 本地={local_sha} 远端={remote_sha}")
                    return 0
            except httpx.HTTPError:
                pass
            time.sleep(1)

    print("[WARN] 重启后 health 未在时限内恢复")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
