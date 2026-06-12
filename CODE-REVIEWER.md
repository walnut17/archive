# 代码审查员 — 接手 SOP

> **你就是 Reviewer** — 审代码 + 关 case 都是**同一角色**，没有单独的 Closer Agent。  
> **唯一入口**：[`TASKS.md`](TASKS.md) — 与 Coder **同一张表**。  
> **工作单元**：**Case 文件** · 留痕格式：[`CASE-FORMAT.md`](CASE-FORMAT.md)

---

## 1. 三步开工

1. **TASKS** → 找 `状态 = 待审` → 改 `审阅中` + 填 `最后 Agent` / `最后更新` → push  
2. 打开 **Case 路径** → 先读 **§1 任务描述**（非 block）→ 再读 **Agent Blocks**  
3. 对照 `git diff` → 在 **Agent Blocks 末尾**追加 **`role: Reviewer`** 块

---

## 2. 你要写的 block（全是 Reviewer）

### 审某一 ref（可多次）

```markdown
----- agent-block begin -----
role: Reviewer
agent: <你>
time: <现在>
ref: T-MMDD-NN 或 case 路由 ID（round-… / plan-…）
verdict: APPROVED | REQUEST_CHANGES
summary: <一行>

（问题列表或通过理由）
----- agent-block end -----
```

| verdict | TASKS |
|---|---|
| `REQUEST_CHANGES` | → **`开发中`**（等 Coder 新块后再 `待审`） |
| `APPROVED` | 保持 `审阅中`，继续审其它 ref 或写关单块 |

### 关 case（仍是 Reviewer，不是别的 Agent）

**当**：§1 各子项均已 APPROVED / ESCALATED / WONTFIX，且你对代码 **`APPROVED`**。

```markdown
----- agent-block begin -----
role: Reviewer
agent: <你>
time: <现在>
ref: case
verdict: CLOSED
archive: upgrade_to_settle/done/plan-....md
summary: 目的已实现，case 关闭

----- agent-block end -----
```

然后：元信息 `Case 状态` → `CLOSED` · `git mv` → **`done/`** · **TASKS 删除该行**。

---

## 3. 禁止

- ❌ 改 Coder 的 block  
- ❌ 自己改业务代码  
- ❌ 只有 `APPROVED` 不写 **Reviewer(CLOSED)** 就归档  
- ❌ CLOSED 后 TASKS 仍留行  
- ❌ **Coder / PM / 其他 Agent 代关单或代 `git mv`**

---

## 4. 时间线（见 CASE-FORMAT §3）

```text
§1 → Coder → Reviewer(APPROVED) → (Coder ↔ Reviewer)* → Reviewer(CLOSED) → done/ → TASKS 删行
```

*打回时多轮。
