#!/usr/bin/env python3
"""
测试智能问答 - "lmz项目有几份材料啊？"
看后端是真的在跑还是死锁了.
"""
import json
import time
import urllib.request
import urllib.error

BASE = "http://127.0.0.1:8080"

# 1. login
login_body = json.dumps({"username": "admin", "password": "admin123"}).encode()
r = urllib.request.Request(f"{BASE}/api/auth/login", data=login_body,
                            headers={"Content-Type": "application/json"})
with urllib.request.urlopen(r, timeout=10) as resp:
    token = json.loads(resp.read())["data"]["token"]
print(f"[OK] login, token len={len(token)}")

# 2. qa-ask with the exact question
qa_body = json.dumps({
    "question": "lmz项目有几份材料啊？",
    "topN": 5,
    "rerank": False
}).encode()
hdr = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
r = urllib.request.Request(f"{BASE}/api/qa/ask", data=qa_body, method="POST",
                            headers=hdr)

print(f"[INFO] 发起 qa-ask, 问题='lmz项目有几份材料啊？'")
print(f"[INFO] timeout=60s, 等... (Agent 模式可能跑 5 步 ReAct, 慢是正常的)")
t0 = time.time()
try:
    with urllib.request.urlopen(r, timeout=60) as resp:
        elapsed = time.time() - t0
        body = json.loads(resp.read())
        print(f"\n[OK] qa-ask {elapsed:.1f}s 后返回:")
        print(json.dumps(body, ensure_ascii=False, indent=2)[:2000])
except urllib.error.HTTPError as e:
    elapsed = time.time() - t0
    body = e.read().decode("utf-8", errors="replace")
    print(f"\n[FAIL] qa-ask {elapsed:.1f}s 后 HTTP {e.code}:")
    try:
        print(json.dumps(json.loads(body), ensure_ascii=False, indent=2)[:1000])
    except:
        print(body[:1000])
except Exception as e:
    elapsed = time.time() - t0
    print(f"\n[FAIL] qa-ask {elapsed:.1f}s 后 {type(e).__name__}: {e}")
