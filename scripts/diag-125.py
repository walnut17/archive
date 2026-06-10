#!/usr/bin/env python3
"""
125 部署机端到端诊断脚本.

跑法 (在 125 的 PowerShell 里):
    python D:\projects-online\scripts\diag-125.py

输出: 5 段 (health / login / llm-provider / qa-ask / core-apis)
每段 PASS / FAIL 标记, FAIL 带错误信息.

依赖: 仅用 Python 3.7+ 标准库 (urllib / json), 无需 pip install.
"""
import json
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urlencode

BASE = "http://127.0.0.1:8080"
TIMEOUT = 15

PASS = "[PASS]"
FAIL = "[FAIL]"
INFO = "[INFO]"


def req(method, path, body=None, token=None, timeout=TIMEOUT):
    """统一发 HTTP 请求, 返回 (status_code, body_dict_or_text)."""
    url = f"{BASE}{path}"
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                return resp.status, json.loads(raw)
            except json.JSONDecodeError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw
    except urllib.error.URLError as e:
        return None, f"URLError: {e.reason}"
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


def section(title):
    print()
    print("=" * 60)
    print(f"  {title}")
    print("=" * 60)


# ============================================================
# 1. health
# ============================================================
section("1. /actuator/health")
status, body = req("GET", "/actuator/health")
if status == 200 and isinstance(body, dict) and body.get("status") == "UP":
    print(f"{PASS} health UP")
    print(f"  groups: {body.get('groups')}")
elif status:
    print(f"{FAIL} health status={status}")
    print(f"  body: {body}")
else:
    print(f"{FAIL} health unreachable: {body}")

# ============================================================
# 2. login
# ============================================================
section("2. POST /api/auth/login (admin/admin123)")
status, body = req("POST", "/api/auth/login", {"username": "admin", "password": "admin123"})
if status == 200 and isinstance(body, dict) and body.get("code") == 0:
    data = body.get("data") or {}
    token = data.get("token")
    if not token:
        print(f"{FAIL} login OK but no token in response: {body}")
        sys.exit(1)
    print(f"{PASS} login OK")
    print(f"  userId={data.get('userId')} role={data.get('role')} username={data.get('username')}")
else:
    print(f"{FAIL} login status={status} body={body}")
    sys.exit(1)

# ============================================================
# 3. /api/auth/me (验证 JWT 有效)
# ============================================================
section("3. GET /api/auth/me (JWT validation)")
status, body = req("GET", "/api/auth/me", token=token)
if status == 200 and isinstance(body, dict) and body.get("code") == 0:
    print(f"{PASS} me OK -> {body.get('data')}")
else:
    print(f"{FAIL} me status={status} body={body}")

# ============================================================
# 4. /api/llm/my-usage (核心 — 验证 GLM key 是否被加载)
# ============================================================
section("4. GET /api/llm/my-usage?recentLimit=5 (GLM key loaded?)")
status, body = req("GET", "/api/llm/my-usage?recentLimit=5", token=token)
if status == 200 and isinstance(body, dict) and body.get("code") == 0:
    data = body.get("data")
    print(f"{PASS} llm/my-usage OK")
    print(f"  data: {json.dumps(data, ensure_ascii=False)[:300]}")
elif status == 500:
    print(f"{FAIL} llm/my-usage 500 — GLM key 可能仍未加载")
    print(f"  body: {body}")
    print()
    print("  >>> 看 backend.log 里 '加载 config.json' 这行:")
    print("  >>>   Get-Content D:\\archive\\logs\\backend.log | Select-String config.json")
else:
    print(f"{FAIL} llm/my-usage status={status} body={body}")

# ============================================================
# 5. POST /api/qa/ask (真打智谱)
# ============================================================
section("5. POST /api/qa/ask (real GLM call)")
print(f"{INFO} 发问题: '1+1 等于几?' (小问题, 5 秒内应返回)")
t0 = time.time()
status, body = req("POST", "/api/qa/ask",
                   {"question": "1+1 等于几? 用一句话回答", "topN": 1, "rerank": False},
                   token=token, timeout=30)
elapsed = time.time() - t0
if status == 200 and isinstance(body, dict) and body.get("code") == 0:
    data = body.get("data") or {}
    answer = (data.get("answer") or data.get("response") or "")[:200]
    print(f"{PASS} qa-ask OK ({elapsed:.1f}s)")
    print(f"  answer: {answer}")
elif status == 500:
    print(f"{FAIL} qa-ask 500 — GLM key 未加载 或 智谱 key 无效 ({elapsed:.1f}s)")
    print(f"  body: {body}")
else:
    print(f"{FAIL} qa-ask status={status} ({elapsed:.1f}s)")
    print(f"  body: {body}")

# ============================================================
# 6. 核心业务 API 探活
# ============================================================
section("6. core APIs smoke test")
for path in ["/api/projects", "/api/dict/types", "/api/trigger-rules", "/api/materials", "/api/llm/my-usage?recentLimit=5", "/api/llm/stats?recentLimit=5"]:
    status, body = req("GET", path, token=token)
    if status == 200:
        code = body.get("code") if isinstance(body, dict) else "?"
        print(f"{PASS} GET {path} -> HTTP {status}, code={code}")
    else:
        print(f"{FAIL} GET {path} -> HTTP {status}, body={body}")

# ============================================================
# 7. config.json 加载证据 (从后端日志读)
# ============================================================
section("7. config.json 加载证据 (后端启动日志)")
print(f"{INFO} 看 backend.log 是否有 '加载 config.json' 这行:")
print(f"  Get-Content D:\\archive\\logs\\backend.log | Select-String -Pattern 'config.json|GLM|api-key' | Select-Object -Last 10")

print()
print("=" * 60)
print("  全部完成")
print("=" * 60)
print()
print("下一步:")
print("  1. 复制上面 [INFO] 那一行的命令, 跑一下, 把输出贴回来")
print("  2. 如果 /api/qa/ask PASS, 智能问答功能通, 验收完成")
print("  3. 如果 /api/llm-providers 或 /api/qa/ask FAIL, 把 FAIL 段贴回来")
