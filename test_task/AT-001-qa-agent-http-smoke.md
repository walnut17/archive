# AT-001 — qa-agent 直连 HTTP 冒烟

> **TASKS.md**：`AT-001`  
> **案例状态**：`未执行`  
> **关联**：[`08-qa-agent-python-service.md`](../docs/architecture/08-qa-agent-python-service.md) · plan `plan-2026-06-12-qa-python-upload-first` · T-0612-04/06

---

## 1. 用例说明

| 字段 | 内容 |
|---|---|
| **目的** | 部署 qa-agent 后，不经 Java BFF，直连 `:8001` 验证 HTTP 契约与关键回归 |
| **前置** | `qa-agent/.env` 已配 `GLM_API_KEY`、`MYSQL_*`；服务已启动 |
| **类型** | 集成 / HTTP 冒烟 |

### 1.1 覆盖端点

| # | 方法 | 路径 | 断言要点 |
|---|---|---|---|
| 1 | GET | `/health` | `status=ok`, `service=qa-agent` |
| 2 | POST | `/v1/ask` | 空问题 → 422 |
| 3 | POST | `/v1/turn/{session_id}` | 空 session → 400 |
| 4 | POST | `/v1/extract/project-fields` | 缺字段 → 422；不存在 ID → `success=false` |
| 5 | POST | `/v1/ask` | 离题问 → 200；`agent_mode=true`；无旧版 parse fallback 文案 |
| 6 | POST | `/v1/turn/{session_id}` ×2 | 同 session 连续 2 问均 200（T-0612-04） |

### 1.2 步骤

1. 启动 qa-agent：`uvicorn app.main:app --host 127.0.0.1 --port 8001`
2. 运行离线契约：`pytest tests/ -q -m "not live"`
3. 运行直连 live：`pytest tests/test_api_http_live.py -q`
4. 运行 smoke 脚本：`python scripts/smoke_http.py`

### 1.3 预期结果

- 三步均 exit code 0
- live 用例 7/7 通过（见 `tests/http_helpers.run_smoke_suite`）
- 离题 / 多轮用例中 `steps` 不含「无法解析 LLM 输出,直接返回原文」

### 1.4 命令

```powershell
cd qa-agent
.\.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8001
# 新终端
.\scripts\run_http_tests.ps1
```

---

## 2. 占用与完工（同步 TASKS.md）

| 字段 | 内容 |
|---|---|
| **TASKS 条目** | `AT-001` |
| **状态** | 与 TASKS.md 一致 |

---

## 3. 执行历史

| 时间 | Agent | 环境 | 代码基线 | 结果 | 备注 / 链接 |
|---|---|---|---|---|---|
| | | | | | |

---

*见 [test_task/README.md](./README.md)*
