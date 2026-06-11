# Plan UP-0611-02 — Agent 意图分类 + 离题拒答规则

> **状态**：`DRAFT`（待 Implement 认领）
> **活跃目录**：`upgrade_to_settle/` · 完工后 → `done/`

---

## 0. Plan 元信息

| 字段 | 内容 |
|---|---|
| **Plan ID** | **UP-0611-02** |
| **标题** | Agent 意图分类：离题问题拒答 + 业务域意图路由 |
| **状态** | `DRAFT` |
| **优先级** | **P1** |
| **目标版本** | v1.1.x |
| **代码基线** | `main` ≥ `e592ce5` |
| **触发** | C-0611-01（T-0611-15 离题问题「今天天气怎么样？」） |
| **架构师** | Sisyphus · 2026-06-11 |

### 完成条件

- [ ] §4 全部实现项完成
- [ ] §5 commit 留痕
- [ ] §6 Review PASS
- [ ] §7 125 联测：问"今天天气"→拒答；问"PRJ-2026-001风险"→正常回答

---

## 1. 需求追溯

| 字段 | 内容 |
|---|---|
| **Agent** | Sisyphus（需求分析） |
| **时间** | 2026-06-11 |
| **摘要** | 业务域为投委会档案管理，Agent 应拒答非档案类问题；需 PM 定文案 |

### 1.1 业务背景

当前 Agent 对任意问题都尝试回答（包括"今天天气怎么样"）。业务方期望：
- Agent **只回答**与投委会档案/项目/材料/议案/待办/规则相关的问题
- 非档案类问题**礼貌拒答**，引导用户回到正题
- 模糊问题（如"那个项目怎么样"）走已有 `ask_clarification` 工具

### 1.2 验收场景

| # | 用户问题 | 期望行为 |
|---|---------|---------|
| 1 | "今天天气怎么样" | 拒答："我是投委会档案助手，只回答项目档案相关问题。" |
| 2 | "用python写个冒泡排序" | 拒答 |
| 3 | "新能源项目今年盈利怎么样" | 正常走 Agent 流程 |
| 4 | "PRJ-2026-001 剩余金额" | 正常走 Agent 流程 |
| 5 | "你好" | 允许（问候，可回答"你好，有什么可以帮助您的？"） |

---

## 2. 架构追溯

| 字段 | 内容 |
|---|---|
| **Agent** | Sisyphus（架构） |
| **时间** | 2026-06-11 |

### 2.1 改动范围

- **AgentSystemPrompt.java**: system prompt 首段加"你的回答范围限于投委会档案管理业务"
- **AgentEngine.java**: 首步 LLM 调用后可加意图分类逻辑（或直接在 prompt 中约束）
- **AgentResponse.java**: 不变（拒答也走正常 answer 返回）

### 2.2 方案选择

**方案 A（推荐）— Prompt 级约束**

在 `AgentSystemPrompt.render()` 首段加行为约束：

```
你的回答范围严格限于投委会档案管理系统中的以下内容：
- 项目（project）档案、议案（proposal）、材料（material）及其版本
- 待办事项（todo）、通知（notification）
- 业务术语（business term）、字典（dict）
- 关键事实（project_fact）、事实事件（project_fact_event）

如果用户问题与上述范围无关，请礼貌拒绝回答，
并引导用户提出档案相关问题。
拒绝语气：友好、直接，不要道歉过长。
```

**优点**：改动最小，无需新增工具或安全机制
**缺点**：依赖 LLM 遵从 prompt，无硬性保障

**方案 B（增强）— IntentGuard 工具**

新增 `intent_guard` 工具，在第一步调用 LLM 前先做意图二分类：
- 调用 LLM（极简 prompt："用户的问题是档案管理相关吗？只回答 YES/NO"）
- YES → 走正常 Agent 流程
- NO → 直接返回拒答文案，不走 5 步循环

**优点**：有硬性过滤，不浪费 LLM 配额
**缺点**：每次多 1 次 LLM 调用（GLM 60req/min 限速需评估）

**本次推荐方案 A**，如果实际效果不佳再升级到方案 B。

---

## 3. PM 范围

| 字段 | 内容 |
|---|---|
| **Agent** | （待 PM 拍板） |
| **时间** | |
| **决策** | |

### 3.1 待 PM 拍板

- **拒答文案**：最终文案由 PM 确认
- **问候类是否允许**："你好""谢谢"等是否正常响应
- **方案 A vs B**：先 A，PM 确认是否接受 prompt 级约束的风险

---

## 4. 开发说明

### 4.1 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `agent/prompt/AgentSystemPrompt.java` | 改 | render() 首段加域约束 + 拒答规则 |
| `agent/AgentContext.java` | 不改 | 无需新增字段 |
| `agent/AgentEngine.java` | 不改 | 如果选方案 A 无需改；方案 B 需加 |
| `agent/prompt/AgentFewShots.java` | 可改 | 第 4 个示例展示拒答场景 |

### 4.2 验收

```bash
# 启动后测试
curl -X POST http://localhost:8080/api/qa/ask \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question":"今天天气怎么样"}'
# 期望: 拒答
```

---

## 5. Implement

（Implementer 填写）

---

## 6. Review

| Agent | 时间 | 结论 |
|---|---|---|
| | | |

---

## 7. 验收

| Agent/Operator | 时间 | 结论 |
|---|---|---|
| | | |
