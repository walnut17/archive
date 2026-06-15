# API 与端点经验

## 1. 路由总览

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/health` | 无 | 健康检查 + **运行版本** + config 路径 |
| POST | `/v1/ask` | 无 | 单轮问答（非流式） |
| POST | `/v1/ask/stream` | 无 | 单轮 SSE 流式 |
| POST | `/v1/turn/{session_id}` | 无 | 多轮 |
| POST | `/v1/turn/{session_id}/stream` | 无 | 多轮流式 |
| POST | `/v1/extract/project-fields` | 无 | 立项字段抽取 |
| GET | `/v1/deploy/status` | 无 | 部署能力 + 详细版本 |
| GET | `/v1/deploy/version` | 无 | **运行版本对账**（开发机/TUI 用） |
| POST | `/v1/deploy/update` | `X-Deploy-Token` | 上传热更新 zip |
| POST | `/v1/deploy/restart` | token | 仅重启 |

代码：`app/api/routes.py`（问答/健康）、`app/api/deploy.py`（部署）

---

## 2. `/health` 响应（2026-06-15 后）

```json
{
  "status": "ok",
  "service": "qa-agent",
  "git_sha": "474c577",
  "features": {
    "evidence_routing": "2026-06-15",
    "hot_deploy": "1",
    "material_count_routing": "1"
  },
  "process_started_at": "2026-06-15T02:43:00Z",
  "config_json": "D:\\archive\\config\\config.json",
  "deploy_enabled": true
}
```

**破坏性变更**（旧客户端注意）：

- 原顶层 `version` → `git_sha`
- 原顶层 `evidence_routing` → `features.evidence_routing`

`check_version.py` 已兼容旧格式。

---

## 3. `/v1/deploy/version`（详细版）

在 health 基础上增加：

```json
{
  "git_sha": "474c577",
  "disk_git_sha": "474c577",
  "pending_restart": false,
  "qa_agent_root": "D:\\projects-online\\qa-agent",
  "version_file": "...\\VERSION",
  "version_file_exists": true
}
```

**关键语义**：

- `git_sha`：**进程启动时冻结**，表示内存里实际跑的代码世代
- `disk_git_sha`：磁盘 `VERSION` 文件（热更新会改）
- `pending_restart: true` → 磁盘已新、进程仍旧（**最常见坑**）

实现：`app/services/version_info.py`

---

## 4. 热更新 API

### POST `/v1/deploy/update`

- Body: multipart `file` = zip
- Query: `restart=true|false`，`token=`（Header 可 `X-Deploy-Token`）
- Zip 允许路径：`app/`、`tools/`、`scripts/`、根目录 `VERSION`
- 成功后调度 `apply_update_and_restart.ps1`（stop → start -Force）

### Token 配置

`config.json` → `qaAgent.deployToken`（125 与开发机 push 脚本一致）

---

## 5. 流式 SSE 事件（TUI 验收用）

路径：`/v1/ask/stream`、`/v1/turn/{sid}/stream`

TUI（`tools/tui_repl.py`）消费事件类型包括 step、answer、done 等。验收时关注：

- 步骤是否**逐步**打印（非一次性 dump）
- 最终答案是否进 **answer box**（绿色框）
- 流中**无**原始 LLM JSON 泄漏到用户可见区

---

## 6. Java 侧集成

`application.yml`：

```yaml
app:
  qa-agent:
    enabled: true
    base-url: http://${host}:${qaAgent.port:8001}
```

Java 调 `/v1/ask` 或流式端点；健康检查可对 `/health` 看 `git_sha` 确认版本。

---

## 7. 版本对账工具链

| 工具 | 用法 |
|---|---|
| `scripts/check_version.py` | CLI 对比本地 git vs 远端 |
| `scripts/push_update.py` | 打包推送 + 重启后自动对账 |
| TUI `/version` | 交互式查看 + `pending_restart` |

```powershell
.\.venv\Scripts\python scripts\check_version.py --url http://182.168.1.125:8001
```

---

## 8. 改 API 时的约定

1. 契约测试：`tests/test_api_contract.py`、`tests/http_helpers.py`
2. 健康字段变更 → 更新 `assert_health_payload`
3. 新端点加在 `deploy.py` 或 `routes.py`，并在本目录补说明
4. 破坏性字段改名 → CLI/TUI 做向后兼容至少一个版本
