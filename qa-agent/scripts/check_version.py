#!/usr/bin/env python3
"""对比开发机 git 版本与远端 qa-agent 运行版本."""
from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]


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


def fetch_remote(base_url: str) -> dict:
    base = base_url.rstrip("/")
    with httpx.Client(timeout=10.0) as client:
        hres = client.get(f"{base}/health")
        hres.raise_for_status()
        health = hres.json()
        try:
            vres = client.get(f"{base}/v1/deploy/version")
            version = vres.json() if vres.status_code == 200 else health
        except httpx.HTTPError:
            version = health
    return {"health": health, "version": version}


def extract_remote_sha(payload: dict) -> str:
    return (
        payload.get("git_sha")
        or payload.get("version")
        or "unknown"
    )


def extract_features(health: dict, version: dict) -> dict | None:
    feats = version.get("features")
    if isinstance(feats, dict):
        return feats
    if health.get("evidence_routing"):
        return {"evidence_routing": health["evidence_routing"]}
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="检查远端 qa-agent 运行版本")
    parser.add_argument("--url", default="http://182.168.1.125:8001")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    args = parser.parse_args()

    local = local_git_sha()
    remote_payload = fetch_remote(args.url)
    remote = remote_payload["version"]
    health = remote_payload["health"]
    remote_sha = extract_remote_sha(remote)
    if remote_sha == "unknown":
        remote_sha = extract_remote_sha(health)
    features = extract_features(health, remote)

    if local == remote_sha:
        state = "match"
        message = "✅ 本地与远端版本一致"
    elif local in ("unknown",) or remote_sha in ("dev", "unknown"):
        state = "unknown"
        message = "⚠️ 无法可靠对比（本地或远端无 git SHA）"
    else:
        state = "diff"
        message = "❌ 版本不一致 — 需 push_update 或 125 git pull + 重启"

    result = {
        "state": state,
        "local_git_sha": local,
        "remote_git_sha": remote_sha,
        "remote_process_started_at": remote.get("process_started_at"),
        "remote_features": features,
        "remote_pending_restart": remote.get("pending_restart"),
        "message": message,
    }

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(f"目标: {args.url.rstrip('/')}")
        print(f"本地 git:  {local}")
        print(f"远端运行:  {remote_sha}")
        if remote.get("process_started_at"):
            print(f"远端启动:  {remote['process_started_at']}")
        if remote.get("pending_restart"):
            print("待重启:    磁盘已更新，进程未加载新代码")
        if features:
            print(f"特性:      {features}")
        print(message)

    return 0 if state == "match" else (2 if state == "diff" else 1)


if __name__ == "__main__":
    raise SystemExit(main())
