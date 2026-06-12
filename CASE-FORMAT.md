# Case 文件格式标准

> **Case** = 一份 Markdown 文件 = 开发 + 审查的**唯一工作单元**。  
> **入口**：[`TASKS.md`](TASKS.md)（Coder、审查员共用）→ 打开 **Case 路径** → 按本文读写。  
> **DEBUG case**：`test-to-settle/round-*.md` · **UPGRADE case**：`upgrade_to_settle/plan-*.md`

### 路由 ID（与 TASKS、文件名统一）

| 类型 | 路由 ID = 文件名（无 `.md`） | 示例 |
|---|---|---|
| **DEBUG** | `round-YYYY-MM-DD-<简述>` | `round-2026-06-11-v1.1-deploy` |
| **UPGRADE** | `plan-YYYY-MM-DD-<简述>` | `plan-2026-06-11-archive-local-fs-tools` |

**禁止**另起 `CASE-D-*`、`UP-MMDD-NN` 等与文件名不一致的路由 ID。  
**Bug 子项**仍用 `T-MMDD-NN`（仅 DEBUG case §1 / Agent Block `ref`）。

---

## 生成 case 的 Agent（必读）

> **适用角色**：Recorder · Co-test Guide · Analyst（开新 round 时）· PM · 架构师（开 plan / complexity 升格）· 任何**新建** `round-*.md` / `plan-*.md` 的 Agent。  
> **权威步骤**：下文 + 对应模板 [`round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md) / [`plan-TEMPLATE.md`](upgrade_to_settle/plan-TEMPLATE.md)。

### 命名铁律

```text
文件名 = 路由 ID + ".md"
路由 ID 必须与文件名完全一致（仅差 .md 后缀）
```

| 类型 | 目录 | 文件名 / 路由 ID 格式 | ✅ 示例 | ❌ 禁止 |
|---|---|---|---|---|
| **DEBUG** | `test-to-settle/` | `round-YYYY-MM-DD-<英文简述>` | `round-2026-06-11-v1.1-deploy` | `CASE-D-0611`、`round-0611` |
| **UPGRADE** | `upgrade_to_settle/` | `plan-YYYY-MM-DD-<英文简述>` | `plan-2026-06-11-chat-ui` | `UP-0611-04`、`plan-0611` |

**`<简述>`**：小写英文 + 连字符，见名知意（如 `archive-local-fs-tools`，勿空格、勿中文文件名）。

### 开 DEBUG case（Recorder / Co-test / 需新 round 时）

1. `cp test-to-settle/round-TEMPLATE.md test-to-settle/round-YYYY-MM-DD-<简述>.md`
2. **§0 路由 ID** = 上一步文件名去掉 `.md`（三者必须相同：文件名 · §0 · TASKS 首列）
3. 填 **§1** Bug 表（`T-MMDD-NN`；来源 `DEPLOY` / `AUTO`）
4. [`TASKS.md`](TASKS.md) **🎯 活跃 Case 路由** 加一行：类型 `DEBUG`，路由 ID = 文件名无后缀，状态 `未开发`
5. 可选更新 [`test-to-settle/STATUS.md`](test-to-settle/STATUS.md)
6. push（含 TASKS + case 文件）

**已有活跃 round、只追加 bug**：**不要**新建文件；只改该 round **§1** 表 + 可选 **Recorder** block。

### 开 UPGRADE case（PM / 架构师 / complexity 升格）

1. 需求/架构文档已写清范围（或 complexity 分析完成）
2. `cp upgrade_to_settle/plan-TEMPLATE.md upgrade_to_settle/plan-YYYY-MM-DD-<简述>.md`
3. **§0 路由 ID** = 文件名无 `.md`
4. 填 **§1** 范围验收、**§2** 开发说明（架构师）
5. [`TASKS.md`](TASKS.md) 加一行：类型 `UPGRADE`，**路由 ID = plan 文件名无后缀**，状态 `未开发`
6. round 源 bug 备注「已转 `plan-…`」；[`complexity.md`](test-to-settle/complexity.md) **删对应行**
7. 可选更新 [`upgrade_to_settle/STATUS.md`](upgrade_to_settle/STATUS.md) · push

### 自检（生成后必过）

- [ ] 文件名以 `round-` 或 `plan-` 开头，且含完整日期 `YYYY-MM-DD`
- [ ] case **§0 路由 ID** = TASKS **路由 ID** = 文件名（无 `.md`）
- [ ] TASKS **Case 路径** 链接能打开且指向本文件
- [ ] 未使用 `CASE-D-*`、`UP-MMDD-NN` 等第二套 ID

---

## 0. 一眼看懂（给所有 Agent）

```text
Case 文件
  §0  元信息表          ← 索引，少改
  §1  任务描述          ← 只写一次，【不是】agent-block
  §2+ Agent Blocks      ← 之后全部留痕，【必须】agent-block，按时间往下追加

时间线（最短）：
  §1 任务描述 → Coder 块 → Reviewer(APPROVED) → Reviewer(CLOSED) → done/ → TASKS 删行

时间线（有打回）：
  … → Reviewer(REQUEST_CHANGES) → Coder → Reviewer(APPROVED) → … → Reviewer(CLOSED)

铁律：
  · 只追加 block，不改、不删他人 block
  · **关单 = 代码审查员**写最后一轮 Reviewer 块（verdict: CLOSED），不是单独 Agent 角色
  · CLOSED 之前须有 Reviewer 的 APPROVED（可对多个 ref 分块写）
  · CLOSED 后 case 移 done/，TASKS 删除该行
```

---

## 1. Case 文件结构

### 1.1 固定段（不用 agent-block）

| 章节 | 谁写 | 写什么 | 何时写 |
|---|---|---|---|
| **§0 元信息** | 开 case 的人 | **路由 ID**（= 文件名无 `.md`）、类型、状态、基线 | 创建 case 时 |
| **§1 任务描述** | 见下表 | 要做什么、验收/现象、子项清单 | **创建 case 时一次写完** |
| **§2 开发说明**（仅 UPGRADE） | 架构师 | 改哪些文件、接口、禁止事项 | plan 定稿后；Coder **只读** |

**§1 任务描述 — 来源对照**

| 来源 | 典型填写人 | DEBUG §1 内容 | UPGRADE §1 内容 |
|---|---|---|---|
| Co-test | Guide / Recorder | Bug 清单表 `T-*`、现象、复现 | — |
| Auto-test | 测试 Agent / Recorder | 同上，`来源: AUTO` | — |
| PM / 架构 | PM、架构师 | — | 做/不做/验收标准 |

> §1 是**摘要锚点**，方便先读再翻 blocks。细节、根因、改法、评审 **一律进 § Agent Blocks**。

### 1.2 动态段（必须用 agent-block）

**章节标题固定为：`## Agent Blocks`**（DEBUG 在 §2，UPGRADE 在 §3）。

此后**每一次**留痕 = **追加一个** agent-block，**禁止**在 §1 或旧 §3～§7 表格里扩写新内容（历史 case 旧表只读）。

---

## 2. Agent Block 格式（a-b）

**起止标记固定**，便于大模型 `grep "agent-block begin"`：

```markdown
----- agent-block begin -----
role: <见 §2.1>
agent: <Agent 名字>
time: <YYYY-MM-DD HH:mm>
ref: <可选：T-MMDD-NN（DEBUG 子项）或 case 路由 ID（round-… / plan-…）；整 case 级可写 case>
summary: <一行摘要，必填>

（正文：Markdown）

----- agent-block end -----
```

### 2.1 role 枚举（Agent 角色 — 只用这些字）

| role | 谁 | 必填扩展头 | 正文 |
|---|---|---|---|
| **Recorder** | Co-test / 测试 | `source: DEPLOY \| AUTO` | 现象、复现（§1 已写时可简短） |
| **Analyst** | 分析 Agent | `verdict: SMALL_FIX \| ESCALATED` | 根因、建议；大改链 complexity ID |
| **Coder** | 程序员 | `commits: <hash>` 或 `commits: —` | 改了什么、怎么验 |
| **Reviewer** | **代码审查员**（含审代码 + **关单**） | `verdict: APPROVED \| REQUEST_CHANGES \| CLOSED` | 审 diff；**关单时**另填 `archive: <done/路径>` |

> **没有 `Closer` Agent 角色** — 关单是审查员写的 **`Reviewer` + `verdict: CLOSED`** 块。  
> **没有 `Fix` / `Implement` role** — 写代码统一用 **`Coder`**。

**历史 case** 可能仍有 `role: Closer` 旧块，视为审查员关单，新留痕勿再用。

### 2.2 verdict 含义（无歧义）

| verdict | 含义 | TASKS 状态 | 下一步 |
|---|---|---|---|
| `APPROVED` | 本条 ref 代码 OK | 仍 `审阅中`，等其它 ref 或关单 | 全 OK → 审查员写 **CLOSED** 块 |
| `REQUEST_CHANGES` | 必须改代码 | → **`开发中`** | Coder 新块 → 再 **`待审`** |
| `CLOSED` | **审查员宣布 case 结束** | — | `git mv` → `done/` · **TASKS 删行** |
| `ESCALATED`（Analyst） | 不当轮修 | — | 转 complexity |

**`APPROVED` ≠ case 结束**。case 结束 **只看** 有没有审查员的 **`Reviewer` + `verdict: CLOSED`** 块。

### 2.3 一轮 vs 多轮

- **一轮** = 一个 agent 追加 **一个** block。  
- Coder 与 Reviewer **一人一块**交替；同一 Reviewer 可连写多块（不同 `ref`）。  
- **最后一轮**必须是 **审查员** 的 **`Reviewer` + `verdict: CLOSED`**（`ref: case`，含 `archive:`）。

---

## 3. 典型时间线（按顺序）

### 3.1 DEBUG（最短 4 块）

```text
§1  Bug 清单（表，非 block）
§2  Agent Blocks:
      [1] Recorder   ← 可选，§1 已够可省略
      [2] Analyst    ← 可选
      [3] Coder
      [4] Reviewer   verdict: APPROVED
      [5] Reviewer   verdict: CLOSED  ← 审查员关单（同一角色，非 Closer Agent）
```

### 3.2 DEBUG（打回一次）

```text
… Coder → Reviewer(REQUEST_CHANGES) → Coder → Reviewer(APPROVED) → Reviewer(CLOSED)
         TASKS: 待审→开发中          TASKS: 开发中→待审
```

### 3.3 UPGRADE

```text
§0  元信息
§1  范围与验收（PM/架构，非 block）
§2  开发说明（架构，非 block）
§3  Agent Blocks:
      [1] Coder      ← 实现
      [2] Reviewer   ← 审 plan 范围 + diff
      … 打回则同 DEBUG …
      [N] Reviewer   verdict: CLOSED
```

---

## 4. 完整示例（DEBUG，一轮过）

```markdown
## 0. Case 元信息
| 路由 ID | round-2026-06-11-v1.1-deploy |
| Case 状态 | OPEN |

## 1. 背景与 Bug 清单
| ID | 来源 | 现象 | 状态 |
| T-0611-20 | DEPLOY | lmz 项目查不到材料数 | OPEN |

## 2. Agent Blocks

----- agent-block begin -----
role: Recorder
agent: Co-test-Guide
time: 2026-06-11 10:00
ref: T-0611-20
source: DEPLOY
summary: 125 联调「lmz项目有几份材料」无结果

复现：知识库问答 → 见 deployment_log §9 step 5

----- agent-block end -----

----- agent-block begin -----
role: Coder
agent: Sisyphus
time: 2026-06-11 14:20
ref: T-0611-20
commits: 35321f1
summary: FindProjectTool 多 variant + materialCount

- `FindProjectTool.java` / `ProjectRepository.java`
- 单测 `FindProjectToolTest`

----- agent-block end -----

----- agent-block begin -----
role: Reviewer
agent: PM-Review
time: 2026-06-11 16:00
ref: T-0611-20
verdict: APPROVED
summary: diff 与单测一致

----- agent-block end -----

----- agent-block begin -----
role: Reviewer
agent: PM-Review
time: 2026-06-11 16:05
ref: case
verdict: CLOSED
archive: test-to-settle/done/round-2026-06-11-v1.1-deploy.md
summary: 全 ref 已过，本 case 关闭

----- agent-block end -----
```

关单后：元信息 `Case 状态` → `CLOSED` · `git mv` → `done/` · **TASKS 删行**。

---

## 5. 与 TASKS.md

| 角色 | 看 TASKS | 写 Case |
|---|---|---|
| **Coder** | `未开发` / `开发中` | **Coder** block；完工 → TASKS **`待审`** |
| **Reviewer**（代码审查员） | `待审` / `审阅中` | **Reviewer** block；全过 → **CLOSED** 关单 → `done/` → **删 TASKS 行** |
| **Recorder/Analyst** | 通常无单独行 | 在 case **Agent Blocks** 追加（开 case 后） |

---

## 6. 历史 case（旧 §1～§7 表格）

仓库里已有的 `round-0611`、旧 `plan-*` 可能含 §3～§7。**新留痕只追加 Agent Blocks**；旧表只读。新 case 用 [`round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md) / [`plan-TEMPLATE.md`](upgrade_to_settle/plan-TEMPLATE.md)。

---

## 7. 歧义 FAQ

| 问题 | 答案 |
|---|---|
| 路由 ID 怎么定？ | **= 文件名无 `.md`**。DEBUG `round-…` · UPGRADE `plan-…` |
| 谁负责遵守？ | **生成 case 的 Agent**（Recorder/PM/架构师）— 见上文 **「生成 case 的 Agent」** |
| §1 要用 agent-block 吗？ | **不要**。§1 是固定任务描述表/列表。 |
| Recorder 还写 block 吗？ | **可选**。§1 已有清单时，block 可只写联调细节。 |
| 审查员能改代码吗？ | **不能**。只写 **Reviewer** block，打回 Coder。 |
| 多个 bug 在一个 case？ | §1 多行 `T-*`；blocks 用 `ref:` 区分；每个 ref 需 Reviewer APPROVED 或 ESCALATED。 |
| 有 Closer Agent 吗？ | **没有**。关单 = 审查员 **`Reviewer` + `verdict: CLOSED`**。旧 `role: Closer` 块只读。 |
| 谁宣布 CLOSED？ | **仅代码审查员**，最后一轮 **Reviewer(CLOSED)** 块。 |
| CLOSED 后 TASKS 还留吗？ | **删除**该行。 |

---

*模板：DEBUG [`round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md) · UPGRADE [`plan-TEMPLATE.md`](upgrade_to_settle/plan-TEMPLATE.md) · 审查 [`CODE-REVIEWER.md`](CODE-REVIEWER.md)*
