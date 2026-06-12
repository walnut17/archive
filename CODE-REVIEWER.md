# 代码审查员 — 接手 SOP

> **唯一入口**：[`TASKS.md`](TASKS.md) **🎯 活跃 Case 路由**（与 Coder 相同，不看别处抢活）。  
> **工作单元**：**Case** = `test-to-settle/round-*.md` 或 `upgrade_to_settle/plan-*.md`（见 [`CASE-FORMAT.md`](CASE-FORMAT.md)）。  
> **不是** [`docs/reviews/`](docs/reviews/) 的 Review Agent。

---

## 1. 接手（3 步）

```text
1. 打开 TASKS.md → 找 状态 = 待审 或 审阅中 的行
2. 打开「Case 路径」列指向的 case 文件
3. 读 Coder 块 + git diff → 在 case 末尾追加 Reviewer 块（格式见 CASE-FORMAT.md）
```

| TASKS 状态 | 含义 | 你要做什么 |
|---|---|---|
| **`待审`** | Coder 已完工 | 占行 → 改 **`审阅中`** → 开始审 |
| **`审阅中`** | 你在审 | 写 Reviewer 块 |
| **`开发中`** | 打回后 Coder 在改 | 等 Coder 再标 **`待审`** |

**占坑**：改 TASKS 该行的 `最后 Agent` / `最后更新` / `状态`，10 秒内 push。

---

## 2. 审查写什么

在 case 文件 **## Agent Blocks** 末尾追加：

```markdown
----- agent-block begin -----
role: Reviewer
agent: <你>
time: <现在>
ref: <TASKS 路由 ID 或 case 内子项>
verdict: APPROVED | REQUEST_CHANGES | REOPEN
summary: <一行>

（正文）
----- agent-block end -----
```

| verdict | 后续 |
|---|---|
| `APPROVED` | 该 ref 通过；整 case 全部通过后走 §3 关单 |
| `REQUEST_CHANGES` / `REOPEN` | TASKS → **`开发中`**；等 Coder 追加块后再 **`待审`** |

**不能**自己改业务代码；不能未读 diff 就 `APPROVED`。

---

## 3. 关单（整 case 完成后）

**条件**：case 内本 TASKS 项相关工作已全部 `APPROVED` 或已 ESCALATED 到 complexity（不进 TASKS）。

1. case 末尾写 **Closer** 块（`case-status: CLOSED`）
2. case 元信息表 `Case 状态` → `CLOSED`
3. `git mv` case 文件 → 对应 **`done/`**
4. **`TASKS.md` 删除该 case 整行**（不再路由）
5. 可选：更新 [`test-to-settle/STATUS.md`](test-to-settle/STATUS.md) / [`upgrade_to_settle/STATUS.md`](upgrade_to_settle/STATUS.md) 辅索引

```bash
# DEBUG
git mv test-to-settle/round-YYYY-MM-DD-*.md test-to-settle/done/
# UPGRADE
git mv upgrade_to_settle/plan-YYYY-MM-DD-*.md upgrade_to_settle/done/
```

---

## 4. 快速链接

| 文档 | 用途 |
|---|---|
| [`TASKS.md`](TASKS.md) | **入口** |
| [`CASE-FORMAT.md`](CASE-FORMAT.md) | 块格式 |
| [`README.md` §1.11](README.md#111-代码审查员) | 角色说明 |
