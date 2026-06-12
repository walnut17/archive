# Case 文件格式标准

> **Case** = 一份独立 Markdown 文件，开发与审查的**基本单元**。  
> **DEBUG case**：[`test-to-settle/round-*.md`](test-to-settle/round-TEMPLATE.md)  
> **UPGRADE case**：[`upgrade_to_settle/plan-*.md`](upgrade_to_settle/plan-TEMPLATE.md)  
> **路由入口**：[`TASKS.md`](TASKS.md)（Coder 与 Reviewer **共用**）

---

## 1. Case 文件结构

```text
┌─ 元信息表（Case ID、类型、状态、路径） ─ 只读摘要，少改
├─ ## 背景 / 清单（可选，Recorder、PM 定稿）
└─ ## Agent Blocks（主日志）— 所有 agent 只在这里追加块
```

**规则**：
- 每个 agent **只追加**块，不改他人块（除 Closer 写 CLOSED 块）。
- 块与块之间空一行；**按时间顺序**追加（最新在文件末尾）。
- 大模型解析：搜 `----- agent-block begin -----` 取块；读 `role` / `agent` / `time` 行。

---

## 2. Agent Block 标准格式

每个块**必须**用起止标记包裹，头部键值行 + 正文：

```markdown
----- agent-block begin -----
role: <Recorder | Analyst | Coder | Reviewer | Closer>
agent: <名字>
time: <YYYY-MM-DD HH:mm 或 ISO8601>
ref: <T-MMDD-NN 或 UP-MMDD-NN；整 case 级可省略>
summary: <一行摘要>

（正文：Markdown，可多段、列表、表格）

----- agent-block end -----
```

### 2.1 各 role 必填扩展字段

| role | 额外头部字段 | 正文写什么 |
|---|---|---|
| **Recorder** | `source: DEPLOY \| AUTO` | 现象、复现、严重度 |
| **Analyst** | `verdict: SMALL_FIX \| ESCALATED` | 根因、建议改法；ESCALATED 链 complexity ID |
| **Coder** | `commits: <hash>` 或 `commits: —` | 改了什么文件、如何验证 |
| **Reviewer** | `verdict: APPROVED \| REQUEST_CHANGES \| REOPEN` | 审 diff 结论、问题列表 |
| **Closer** | `case-status: CLOSED` · `archive: <done/ 路径>` | 关单结论；**仅 Reviewer 在整 case 通过后可写** |

### 2.2 示例 — Coder

```markdown
----- agent-block begin -----
role: Coder
agent: Sisyphus
time: 2026-06-11 14:20
ref: T-0611-20
commits: 35321f1
summary: FindProjectTool 多 variant 检索 + materialCount

- `FindProjectTool.buildSearchVariants()` 去「项目」后缀
- `ProjectRepository` remark LIKE
- 单测 `FindProjectToolTest` 新增 lmz 简称用例

----- agent-block end -----
```

### 2.3 示例 — Reviewer

```markdown
----- agent-block begin -----
role: Reviewer
agent: ReviewBot
time: 2026-06-11 16:00
ref: T-0611-20
verdict: APPROVED
summary: diff 与单测一致，125 待 rebuild 验证

核对 commit 35321f1；无越界改动。建议 125 pull 后回归「lmz项目有几份材料」。

----- agent-block end -----
```

### 2.4 示例 — Closer（整 case 归档）

```markdown
----- agent-block begin -----
role: Closer
agent: ReviewBot
time: 2026-06-11 18:00
case-status: CLOSED
archive: test-to-settle/done/round-2026-06-11-v1.1-deploy.md
summary: 本 case 全部待审项已通过或已 ESCALATED

----- agent-block end -----
```

---

## 3. 与 TASKS.md 的联动

| 谁 | TASKS 操作 | Case 操作 |
|---|---|---|
| **Coder** | 占 `未开发`/`开发中` 行 → 完工改 **`待审`** | 追加 **Coder** 块 |
| **Reviewer** | 占 **`待审`** → **`审阅中`** | 追加 **Reviewer** 块；打回则 Coder 再写块，TASKS 回 **`开发中`** |
| **Reviewer** | 整 case 完毕 → **删除 TASKS 行** | 写 **Closer** 块 → `git mv` → **`done/`** |

**TASKS 不再保留已 CLOSED 的 case** — 追溯只看 `done/` 内 case 文件。

---

## 4. 旧 §1～§7 章节

历史 case 可能仍有 §1～§7 表格。**新留痕优先用 Agent Blocks**；旧表只读不扩写。新 case 从 [`round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md) / [`plan-TEMPLATE.md`](upgrade_to_settle/plan-TEMPLATE.md) 复制。

---

*2026-06-11：Coder + Reviewer 统一 case 块格式。*
