# qa-agent TUI 测试工具

> **目的**：命令行直连 qa-agent 流式端点, 快速测问答功能. 0 依赖 (Python stdlib).

---

## 快速启动 (3 步)

### 1. 启动 qa-agent (一个终端)

```bash
cd D:\projects-online\qa-agent
.\.venv\Scripts\activate   # 或 source .venv/bin/activate
uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

### 2. 启动 TUI (另开终端)

```bash
cd D:\projects-online
python qa-agent\tools\tui_repl.py
# 或 (在 qa-agent/ 目录内)
python -m tools.tui_repl
```

### 3. 开始问

```
❯ 你好
→ 你好
────────────────────────────────────────────────────────────
[320ms] 您好! 我是投委会档案助手。请问您想查询哪个项目?
────────────────────────────────────────────────────────────
⏱ 320ms  🔧 0 步

❯ PRJ-2026-001 剩余金额多少?
→ PRJ-2026-001 剩余金额多少?
────────────────────────────────────────────────────────────
[1200ms] 项目 PRJ-2026-001 (新能源 A) 剩余金额 500 万元。
[1250ms] 
[1280ms] 关键信息:
[1290ms] - 状态: 贷后中
[1310ms] - 待办: 3 项, 最近 2026-07-01
────────────────────────────────────────────────────────────
⏱ 1320ms  🔧 2 步

── 步骤 ──
  步 1 💭 先定位项目
      🔧 find_project
      👁 [{"code":"PRJ-2026-001","name":"新能源 A","switchDecision":"SAME_CONFIRMED"}]
  步 2 💭 已知项目, 拿汇总
      🔧 get_project_business_data
      👁 {"amountWan":500,"todoCount":3,"nextDue":"2026-07-01"}

── 来源 (1) ──
  [1] PROJECT · 新能源 A
```

---

## 常用命令

| 命令 | 说明 |
|---|---|
| 直接输入问题 | 调 qa-agent, 流式渲染 |
| `/help` | 显示所有命令 |
| `/exit` | 退出 TUI |
| `/clear` | 清屏 |
| `/session` | 显示当前 session_id |
| `/new` | 开启新 session |
| `/health` | 调 /health 端点 |
| `/tools` | 列出 8 个 Agent 工具 |
| `/last` | 显示上一次完整响应 (步骤+来源) |
| `/time <q>` | 非流式测延迟 |
| `/bench [N=5]` | 跑 N 次 "你好" 测 P50/P90 |
| `/raw` | 切换 raw 模式 (输出原始 SSE JSON) |
| `/quiet` | 切换 quiet 模式 (只显示答案) |
| `/config` | 显示当前配置 |
| `/set <k> <v>` | 临时改配置 (url / timeout / raw / quiet) |

### 快捷键

| 键 | 说明 |
|---|---|
| `Ctrl+C` | 中断当前回答 |
| `Ctrl+D` | 退出 TUI |
| `↑ / ↓` | 命令历史 (需 readline) |

---

## 命令行参数

```bash
python qa-agent/tools/tui_repl.py \
  --url http://127.0.0.1:8001 \   # qa-agent 地址 (env: QA_AGENT_URL)
  --session-id my-test-id \      # 多轮 session (默认随机)
  --no-color \                   # 禁用颜色 (Windows CMD 用)
  --raw \                        # 启动就 raw 模式
  --quiet \                     # 启动就 quiet 模式
  --timeout 30                  # 请求超时 (秒, 默认 60)
```

---

## 典型测试场景

### 多轮项目锁穿透

```
❯ /new
新 session: abc-123
旧 session: xyz-789

❯ PRJ-2026-001 抵押物处理到哪了?
→ (流式输出, 命中 find_project → get_project_business_data)

❯ 它最新进展?
→ (自动锁定 PRJ-2026-001, 不重新 find_project)
── 步骤 ──
  步 0 🔗 锁定: PRJ-2026-001    ← v1.2 新增: 指代词解析
  步 1 💭 已知项目
      🔧 get_project_business_data
```

### 简称 + 多变体

```
❯ lmz项目下有几份材料?
→ (find_project 一次工具内试 [lmz项目, lmez], 命中 PRJ-2025-088)
```

### 离题拒答

```
❯ 今天天气怎么样?
→ (直接 FINAL_ANSWER, 0 步)
────────────────────────────────────────────────────────────
我是投委会档案助手, 只回答项目档案相关问题。请问您想查询哪个项目?
────────────────────────────────────────────────────────────
⏱ 280ms  🔧 0 步
```

### 降级模式（GLM 不可用时）

```
❯ PRJ-2026-001 剩余金额
→ (GLM 503, 后端走降级)
────────────────────────────────────────────────────────────
⚠️ 当前 LLM 服务暂时不可用, 以下是基于降级搜索的简要信息:

**项目**: 新能源 A (PRJ-2026-001)
**状态**: 贷后中
**金额**: 500 万元
**材料数**: 5 份
**最近待办**: 2026-07-01

(完整分析需要 LLM 服务恢复, 请稍后重试)
────────────────────────────────────────────────────────────
⏱ 50ms  🔧 1 步  🏷 DEGRADED  ⚠️ 降级
```

### 性能压测

```
❯ /bench 10
跑 10 次 benchmark...
  [1/10] 280ms
  [2/10] 250ms
  [3/10] 320ms
  ...
P50: 280ms  P90: 350ms  (n=10)
```

---

## 故障排查

| 问题 | 原因 | 解决 |
|---|---|---|
| `qa-agent 不可达 (127.0.0.1:8001)` | qa-agent 没启动 / 端口错 | `uvicorn app.main:app --port 8001` |
| `HTTP 404` | 端点路径错 | 检查 qa-agent `/v1/ask/stream` 是否存在 |
| `HTTP 500` | qa-agent 内部错 | 看 qa-agent 终端的 traceback |
| 流式不显示逐字 | qa-agent 没装 `sse-starlette` | 升级 qa-agent 到 v1.2 |
| 中文乱码 | Windows CMD 编码 | `chcp 65001` 或用 `--no-color` |
| 历史命令不工作 | readline 未装 (Windows) | `pip install pyreadline3` |

---

## 为什么不用 prompt_toolkit / textual?

- **0 依赖** = 业务方 125 服务器不一定有这些包
- **快速上手** = TUI 30 秒能跑
- **够用** = cmd module 够做 TUI (readline history + 命令编辑 + 颜色)

将来要"花哨 TUI" (光标移动 / 多窗格) 再升级到 `textual` (~ 30MB 依赖).

---

*PM 写于 2026-06-12 (qa-agent v1.2) | 业务方 125 验收 / dev 调试 / CI 联测 都用得上*
