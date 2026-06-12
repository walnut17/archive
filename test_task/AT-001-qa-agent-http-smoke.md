# AT-001 — qa-agent 直连 HTTP 冒烟（125 部署 · 本机发起）

> **TASKS.md**：`AT-001`  
> **案例状态**：`已通过`  
> **关联**：[`08-qa-agent-python-service.md`](../docs/architecture/08-qa-agent-python-service.md) · 父 plan [`done/plan-2026-06-12-qa-python-upload-first.md`](../upgrade_to_settle/done/plan-2026-06-12-qa-python-upload-first.md)（CLOSED）· Coder 遗留 → [`plan-2026-06-12-qa-agent-followups`](../upgrade_to_settle/plan-2026-06-12-qa-agent-followups.md)

---

## 1. 用例说明

| 字段 | 内容 |
|---|---|
| **目的** | qa-agent 部署在 **125**，Auto-test **从开发机** HTTP 直连，不经 Java BFF，验证契约与 T-0612-04/06 |
| **服务位置** | `182.168.1.125:8001`（qa-agent + MySQL `archive_db` 同在 125） |
| **测试发起** | **开发机**（本仓库 `qa-agent/` 下 pytest / smoke 脚本） |
| **默认目标** | `http://182.168.1.125:8001`（`QA_AGENT_BASE_URL` 可覆盖） |
| **类型** | 集成 / HTTP 冒烟 |

> **部署侧（Coder）**：125 上起 qa-agent，`.env` 与 `D:\archive\config\config.json` 一致；**8001 须对本机网络可达**（内网监听 `0.0.0.0` 或等价防火墙放行）。  
> **测试侧（Auto-test）**：部署完成前只跑离线 mock；**不叫开始就不跑 live**。

### 1.0 125 部署检查（Coder）

| # | 检查项 |
|---|---|
| 1 | `git pull` · `pip install -r requirements.txt` |
| 2 | `qa-agent/.env` ← `config.json`（GLM + MYSQL） |
| 3 | MySQL `archive_db` 正常 |
| 4 | qa-agent 启动，125 上 `curl http://127.0.0.1:8001/health` → 200 |
| 5 | **开发机** `curl http://182.168.1.125:8001/health` → 200（网络连通） |

### 1.1 覆盖端点

| # | 方法 | 路径 | 断言要点 |
|---|---|---|---|
| 1 | GET | `/health` | `status=ok`, `service=qa-agent` |
| 2 | POST | `/v1/ask` | 空问题 → 422 |
| 3 | POST | `/v1/turn/{session_id}` | 空 session → 400 |
| 4 | POST | `/v1/extract/project-fields` | 缺字段 → 422；不存在 ID → `success=false` |
| 5 | POST | `/v1/ask` | 离题问 → 200；拒答；无 parse fallback 丑文案 |
| 6 | POST | `/v1/turn/{session_id}` ×2 | 同 session 连续 2 问均 200（T-0612-04） |

### 1.2 步骤（开发机 · 部署完成后）

1. （可选）开发期自检：`pytest tests/ -q -m "not live"`
2. 确认 125 health 可达：`.\scripts\wait_for_qa_agent.ps1`
3. **一键 live + smoke**：`.\scripts\run_remote_smoke.ps1`
4. 或分步：
   - `pytest tests/test_api_http_live.py -q -m live`
   - `python scripts/smoke_http.py`

### 1.3 预期结果

- live + smoke exit code 0
- smoke 7/7 通过
- FAIL → `test-to-settle/test_bug-*.md`，来源 `AUTO`

### 1.4 命令（开发机 PowerShell）

```powershell
cd D:\projects_new\projects-online\qa-agent

# 部署完成后（用户叫「开始实测」时执行）
.\scripts\run_remote_smoke.ps1

# 若服务刚重启，可等待就绪（最多 5 分钟）
.\scripts\run_remote_smoke.ps1 -WaitSeconds 300
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
| 2026-06-12 | Auto-test | 开发机 → `182.168.1.125:8001` | workspace | **BLOCKED** | 125 Ping/5173/8080 通，8001 超时；qa-agent 仅 `--host 127.0.0.1` 监听。需 125 改为 `0.0.0.0` 重启后再测 |
| 2026-06-12 | Auto-test | 开发机 → `182.168.1.125:8001` | `3364c90` | **PASS** | pytest live 7/7 + smoke 7/7；T-0612-04/06 场景通过 |

---

*见 [test_task/README.md](./README.md)*
