# Review — <主题简述>

> **状态**：`OPEN`  
> **规则**：仅 **Review Agent** 可将状态改为 **`CLOSED`** 并宣布「评审结束」；Subject Agent **不得**自行结案。

---

## 0. 元信息

| 字段 | 内容 |
|---|---|
| **Review ID** | `REV-YYYY-MM-DD-<简述>` |
| **评审对象** | 例：MOD-05 前端 / commit `abc1234` / PR #N |
| **Review Agent** | *填写你的名字* |
| **Subject Agent** | *被评审方名字* |
| **关联** | 例：`test-to-settle/round-*.md` T-0611-XX · `TASKS.md` RI-N |
| **Opened** | YYYY-MM-DD HH:mm |

---

## Round 1 — 评审意见（Review Agent）

**Agent**：  
**时间**：  
**摘要**：

### 意见清单

| # | 严重度 | 意见 | 依据/位置 |
|---|---|---|---|
| R1-1 | P0 / P1 / P2 | | 文件:行 / spec 条款 |

### 总要求

- （可选）必须满足的前置条件、回归项

---

## Round 1 — 回复（Subject Agent）

> **Subject Agent 在本节下方填写**，不要改 Review Agent 上文。

**Agent**：  
**时间**：  
**摘要**：

### 总体态度

- [ ] **全部接受**
- [ ] **部分接受**（见下表）
- [ ] **不接受**（须说明理由与替代方案）

### 逐项回复

| # | 对应意见 | 是否接受 | 处理措施 | 结果 / Commit / 说明 |
|---|---|---|---|---|
| R1-1 | | ✅ / 🟡 / ❌ | | |

### 待澄清问题（可选）

- Q1：…

---

## Round 2 — Review Agent 跟进

> Review Agent 阅读回复后：**续提要求** 或 **宣布评审结束**。

**Agent**：  
**时间**：  
**结论**：`CONTINUE` / **`CLOSED`**

### 跟进意见（若 CONTINUE）

| # | 意见 | 对应 Subject 回复 |
|---|---|---|
| R2-1 | | R1-1 |

### 评审结束声明（若 CLOSED）

- [ ] 所有必改项已验证（commit / 回归 / 文档）
- [ ] 可选项已记录，不阻塞合并
- **结案说明**：（一句话）

---

<!-- 若需 Round 3+，复制「Round N — 评审意见 / 回复」结构 -->

---

*模板版本：2026-06-11 · 见 [README.md](./README.md)*
