#!/usr/bin/env python3
"""Debug remote /v1/ask steps for a question."""
from __future__ import annotations

import json
import sys

import httpx

URL = sys.argv[1] if len(sys.argv) > 1 else "http://182.168.1.125:8001"
QUESTION = sys.argv[2] if len(sys.argv) > 2 else "lmz项目的利率是多少？"


def main() -> None:
    with httpx.Client(timeout=120.0) as c:
        r = c.post(f"{URL.rstrip('/')}/v1/ask", json={"question": QUESTION})
        print("status", r.status_code)
        d = r.json()
        print("answer:", d.get("answer", ""))
        print("tool_calls:", d.get("tool_calls"))
        for s in d.get("steps", []):
            obs = str(s.get("observation", ""))[:300]
            thought = str(s.get("thought", ""))[:80]
            print(f"  step {s.get('iteration')}: tool={s.get('tool')}")
            print(f"    thought: {thought}")
            print(f"    args: {str(s.get('toolArgs', ''))[:120]}")
            print(f"    obs: {obs}")


if __name__ == "__main__":
    main()
