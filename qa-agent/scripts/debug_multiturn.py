#!/usr/bin/env python3
"""Multi-turn session test."""
from __future__ import annotations

import uuid

import httpx

BASE = "http://182.168.1.125:8001"
SID = str(uuid.uuid4())


def turn(q: str) -> None:
    with httpx.Client(timeout=120) as c:
        r = c.post(f"{BASE}/v1/turn/{SID}", json={"question": q})
        d = r.json()
    print("Q:", q)
    print("A:", (d.get("answer") or "")[:500])
    for s in d.get("steps", []):
        print(" ", s.get("tool"), str(s.get("toolArgs", ""))[:90])
        print("   obs:", str(s.get("observation", ""))[:180])
    print()


turn("lmz项目远期回购的债权标的是什么？")
turn("这个债权的抵押物是什么？")
