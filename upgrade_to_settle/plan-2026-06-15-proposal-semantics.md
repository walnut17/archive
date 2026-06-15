# plan-2026-06-15-proposal-semantics — 议案 vs 维护业务语义对齐

> **状态**：`OPEN` — 可与 scaffold 并行；不阻塞 cutover  
> **来源**：complexity **C-0615-01** · round `round-2026-06-12-qa-agent-react-iteration` T-0615-proposal-semantics · **P2**

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-15-proposal-semantics` |
| **类型** | `UPGRADE` |
| **Case 状态** | `OPEN` |
| **标题** | 投委会议案计数语义：正式议案 vs 维护性材料 |
| **需求锚点** | 本文 §1.2 业务规则 |
| **架构锚点** | [`09-analysis-ownership-python.md`](../docs/architecture/09-analysis-ownership-python.md) §6 |

---

## 1. 任务描述（PM / 架构）

### 1.1 问题

用户问「项目下几次投委会议案」时，qa-agent `get_project_business_data` 对 `proposal` 表 **全量 COUNT**，把「仅挂材料的维护性 proposal」与「正式审议议案」混在一起，导致 **多计** 或列表误导。

Java `GetProjectBusinessDataTool` 同样只返回 `materialCount`，**不含**议案语义；Python 侧已扩展 `proposalCount` + `proposals[]`，但 **无过滤规则**。

### 1.2 业务规则（PM 拍板 · Coder 实现）

| 概念 | 判定 | 计入 `committeeProposalCount` |
|---|---|---|
| **正式议案** | `proposal.type` ∈ 字典 `proposal_type` 的 **`申请` / `立项` / `临时` / `补充`**，且 `status` ≠ `草稿` | ✅ |
| **维护性容器** | `type` 为空 **`维护`** / **`材料维护`**，或仅有材料、从未进入审议流 | ❌（计入 `maintenanceBundleCount`） |
| **草稿议案** | `status = 草稿` | ❌（可选单独字段 `draftProposalCount`） |

**用户可见话术**（prompt 示例）:

> 项目 X 下共有 **N** 次投委会议案（正式审议），另有 **M** 组维护性材料归档。

若 `committeeProposalCount=0` 但有材料：明确说明「无正式议案记录，材料挂在维护性归档下」。

### 1.3 做

- Python `get_project_business_data` 返回拆分计数 + 过滤后 `proposals[]`
- Java `GetProjectBusinessDataTool` **对齐字段**（Agent 降级路径一致）
- `react_helpers` / `prompts.py` 议案类路由改用 `committeeProposalCount`
- 单元测试：lmz/shtx 样例 + 合成数据（1 正式 + 1 维护）

### 不做

- 改 `proposal` 表结构（除非发现 type 字典缺失 — 则仅补 `dict_item` 种子）
- 前台看板大改（可选后续：展示拆分计数）

### 验收

| # | When | Then |
|---|---|---|
| 1 | TUI 问「lmz 项目几次投委会议案」 | 数字 = 正式议案数；答案区分维护材料 |
| 2 | `get_project_business_data` JSON | 含 `committeeProposalCount` · `maintenanceBundleCount` · `proposals`（仅正式） |
| 3 | Java 工具单测 | 字段与 Python 一致 |
| 4 | pytest `test_get_project_business_data.py` | 更新通过 |

---

## 2. 开发说明（架构师 · Coder 只读）

### 2.1 过滤 SQL（Python 参考）

```sql
-- 正式议案
SELECT COUNT(*) FROM proposal
 WHERE project_id = ? AND deleted_at IS NULL
   AND status <> '草稿'
   AND (type IS NULL OR type NOT IN ('维护', '材料维护'));

-- 维护性
SELECT COUNT(*) FROM proposal
 WHERE project_id = ? AND deleted_at IS NULL
   AND type IN ('维护', '材料维护');
```

**注意**: 上线前用 125 真实数据核对 `type` 分布；若历史数据 type 为空且实为正式议案，在 Coder 块记录 **数据补丁 SQL**（UPDATE type）而非改规则。

### 2.2 文件清单

| 文件 | 改动 |
|---|---|
| `qa-agent/app/agent/tools/get_project_business_data.py` | 拆分计数 + 过滤列表 |
| `qa-agent/app/agent/react_helpers.py` | `proposal_count_answer_from_biz` 读新字段 |
| `qa-agent/app/agent/prompts.py` | 示例与路由表 |
| `backend/.../GetProjectBusinessDataTool.java` | 对齐 DTO 字段 |
| `qa-agent/tests/test_get_project_business_data.py` | 新断言 |
| `backend/.../GetProjectBusinessDataToolTest.java` | 新断言 |

### 2.3 兼容

保留 `proposalCount` = `committeeProposalCount` **deprecated 别名** 一版，避免前端/旧 prompt 立刻断裂；日志 warn 一次。

---

## 3. Agent Blocks

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-15
ref: plan-2026-06-15-proposal-semantics
ref_commit: b42fb8b
verdict: REQUEST_CHANGES
summary: 计数 SQL/字段已加；别名、话术、单测未对齐 plan

**已通过 ✅**

- Python/Java `committeeProposalCount` + `maintenanceBundleCount` + 过滤 `proposals[]`
- `ProposalRepository.countCommitteeByProjectId` / `countMaintenanceByProjectId`

**阻塞**

1. **`proposalCount` 语义错**：现为 `committee+maintenance`；plan §2.3 要求 **deprecated 别名 = committeeProposalCount**
2. **`proposal_count_answer_from_biz`** 仍读 `proposalCount`，未区分正式 vs 维护话术（§1.2 示例）
3. **`test_get_project_business_data.py`** mock 仍 `proposal_count`，缺 `committee_count`/`maintenance_count` → **KeyError 挂 pytest**
4. **`GetProjectBusinessDataToolTest`** 未 mock `ProposalRepository`，Java 单测会 NPE
5. 无 **Coder agent-block**

----- agent-block end -----

---

## 4. 关单检查

- [ ] §1 验收 1～4
- [ ] round T-0615-proposal-semantics 标「已转本 plan」
- [ ] Reviewer **CLOSED**
