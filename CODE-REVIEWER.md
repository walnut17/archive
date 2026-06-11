# 代码审查员 — 接手 SOP

> **角色**：**代码审查员**（Code Reviewer）— 审 `test-to-settle/` 的 DEBUG 修复与 `upgrade_to_settle/` 的 UPGRADE 实现。  
> **不是** [`docs/reviews/`](docs/reviews/README.md) 里的 Review Agent（那审 MOD/架构交付）；**也不是**仅改 round §4 一行的轻量 Reviewer 别名 — 本角色**统一从两目录索引入手**，对线 + 关单 + 归档。

**Coder 入口**：[`TASKS.md`](TASKS.md) · **审查员入口**：本文 + 下方两索引。

---

## 1. 接手第一件事（5 分钟）

```text
1. 读本文
2. 打开 test-to-settle/STATUS.md      → 看「待审查」
3. 打开 upgrade_to_settle/STATUS.md   → 看「待审查」
4. 选一行 审查状态 = 待审 / 审阅中 的任务
5. 打开对应 round-*.md 或 plan-*.md，进入 §4（DEBUG）或 §6（UPGRADE）
```

| 目录 | 索引 | 审什么 | 任务文件内章节 |
|---|---|---|---|
| [`test-to-settle/`](test-to-settle/README.md) | [`STATUS.md`](test-to-settle/STATUS.md) | DEBUG 修复 | round **§3** 改动 → **§4** 评审 |
| [`upgrade_to_settle/`](upgrade_to_settle/README.md) | [`STATUS.md`](upgrade_to_settle/STATUS.md) | UPGRADE 实现 | plan **§5** 留痕 → **§6** 评审 |

**可选占坑**：在索引表把该任务 `审查状态` 改为 `审阅中`，`审查员` 填你的名字（10 秒内 commit push，与 TASKS 占坑同规则）。

---

## 2. 什么情况进审查队列

| 来源 | 索引里何时出现「待审」 |
|---|---|
| **DEBUG** | round §3 有 `FIXED` / `VERIFY`；或 TASKS 对应 T-* 为 `VERIFY` |
| **UPGRADE** | plan §5 有 commit 留痕；plan 元信息 `IN_PROGRESS` / `VERIFY` |
| **还不审** | plan 仍 `DRAFT`；round 仅 `RECORDED` 无 §3；complexity 中转项 |

---

## 3. 审查流程（单任务）

### 3.1 读材料

1. 任务文件全文（现象 / 根因 / 改法）
2. `git log` / `git diff` 对照 §3 或 §5 的 commit
3. 相关单测、`docs/reviews/LESSONS-LEARNED.md` 避坑

### 3.2 写意见（审查员）

| 类型 | 写哪里 |
|---|---|
| DEBUG | round **§4.2** 评审表 + 必要时 **§4.3 审查对线** |
| UPGRADE | plan **§6** 结论表 + **§6.2 审查对线** |

**结论取值**：

| 结论 | 含义 | 程序员下一步 |
|---|---|---|
| `APPROVED` | 通过 | 等整单归档条件（见 §5） |
| `REQUEST_CHANGES` | 有问题，需改 | 在 **§3.3 / §5.2** 回复并改代码 |
| `REOPEN` | DEBUG 打回 Fix | 回 round §3 重提 |

### 3.3 看程序员回复

- DEBUG：round **§3.3 审查反馈回复**（Fix Agent 逐条回应）
- UPGRADE：plan **§5.2 审查反馈回复**（Implement Agent）

你确认问题解决后，把 §4.2 / §6.1 该行改为 `APPROVED`。

**留痕必填**：Agent 名 · 时间 · 摘要。

---

## 4. 关单与归档

### 4.1 DEBUG — 单条 bug 关闭

1. round **§1.3** 该行 → `CLOSED`
2. round **§4.2** → `APPROVED` + `CLOSED`
3. 若该 bug 在 [`TASKS.md`](TASKS.md) 有路由行 → 改 `已完成`
4. 同步 [`test-to-settle/STATUS.md`](test-to-settle/STATUS.md)

### 4.2 DEBUG — 整轮 round 关闭并移 `done/`

**条件**（全部满足）：

- §1 每条 bug 为 `CLOSED` / `ESCALATED` / `WONTFIX`
- §4 已审项均为 `CLOSED`
- §5 轮次结论已写

**操作**：

1. round **§0** / **§5** → 轮次状态 **`CLOSED`**
2. `git mv test-to-settle/round-YYYY-MM-DD-*.md test-to-settle/done/`
3. [`test-to-settle/STATUS.md`](test-to-settle/STATUS.md) — 从「活跃 round」删除，写入 [`done/README.md`](test-to-settle/done/README.md)
4. TASKS 中该轮已无 OPEN/VERIFY 的 DEBUG 行

### 4.3 UPGRADE — plan 关闭并移 `done/`

**条件**：

- §6 Reviewer **`APPROVED`**
- §7 验收勾选完成
- §0 Plan 状态 → **`CLOSED`**

**操作**：

1. plan **§7.2** 填写归档路径与结论
2. `git mv upgrade_to_settle/plan-*.md upgrade_to_settle/done/`
3. [`upgrade_to_settle/STATUS.md`](upgrade_to_settle/STATUS.md) 删活跃行 → [`done/README.md`](upgrade_to_settle/done/README.md) 追加
4. [`TASKS.md`](TASKS.md) 对应 UP-* → `已完成`

---

## 5. 你不能做什么

- ❌ 自己改业务代码代替 Fix / Implement（应 `REQUEST_CHANGES` / `REOPEN`）
- ❌ 未读 diff 就 `APPROVED`
- ❌ 程序员未在 §3.3 / §5.2 回应就关单
- ❌ 归档后仍留活跃索引行
- ❌ 把 complexity 或 `docs/reviews/` 的项与本流程混为一谈

---

## 6. 快速索引

| 文档 | 用途 |
|---|---|
| [`test-to-settle/STATUS.md`](test-to-settle/STATUS.md) | DEBUG 待审队列 |
| [`upgrade_to_settle/STATUS.md`](upgrade_to_settle/STATUS.md) | UPGRADE 待审队列 |
| [`test-to-settle/round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md) | §3.3 / §4.3 对线格式 |
| [`upgrade_to_settle/plan-TEMPLATE.md`](upgrade_to_settle/plan-TEMPLATE.md) | §5.2 / §6.2 对线格式 |
| [`README.md` §1](README.md#-1-角色导航-核心) | 角色总表 |

---

*审查员不看 TASKS 抢开发任务；只看两目录 STATUS + 任务文件 §4/§6。*
