#!/usr/bin/env python3
"""Smoke-test qa-agent by calling its HTTP API directly."""

from __future__ import annotations

import argparse
import os
import sys

import httpx

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from tests.conftest import BASE_URL, qa_agent_reachable  # noqa: E402
from tests.http_helpers import run_smoke_suite  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="直连 qa-agent HTTP 冒烟测试")
    parser.add_argument(
        "--base-url",
        default=BASE_URL,
        help="qa-agent 基址，默认 QA_AGENT_BASE_URL 或 http://127.0.0.1:8001",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=float(os.environ.get("QA_AGENT_HTTP_TIMEOUT", "120")),
        help="HTTP 超时秒数",
    )
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")

    if not qa_agent_reachable(base_url):
        print(f"[FAIL] qa-agent 不可达: {base_url}")
        print("请先启动: .\\.venv\\Scripts\\uvicorn app.main:app --host 127.0.0.1 --port 8001")
        return 2

    print(f"[INFO] 目标: {base_url}")
    with httpx.Client(base_url=base_url, timeout=args.timeout) as client:
        results = run_smoke_suite(client)

    passed = 0
    for item in results:
        status = "PASS" if item.passed else "FAIL"
        suffix = f" — {item.detail}" if item.detail else ""
        print(f"[{status}] {item.name}{suffix}")
        if item.passed:
            passed += 1

    print(f"\n合计: {passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
