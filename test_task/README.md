# 自动化测试案例 — `test_task/`

> **本目录放自动化/半自动化测试案例**（步骤、预期）。**有真实案例时再建文件**；不要预先编示例任务。
>
> **任务占用**：有**未完成**案例时，在根 [`TASKS.md`](../TASKS.md) **「自动化测试 AT-*」** 节追加 **AT-XXX**；**PASS 后删 TASKS 行**（无任务前该节保持空白）。
>
> **PASS** → 案例 **§3 执行历史** 追加 · **FAIL** → [`test-to-settle/test_bug-*.md`](../test-to-settle/README.md)

**导航**：根 [`README.md` §9`](../README.md#-9-自动化测试任务-test_task) · [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 1. 三分工

| 目录 / 文件 | 放什么 |
|---|---|
| **`test_task/*.md`** | 测试案例 + PASS 执行历史（**长期保留**） |
| **`TASKS.md` AT-*** | **仅未完成**占坑；PASS 后**删行** |
| **`test-to-settle/test_bug-*.md`** | 案例 FAIL 入口 → `round-*.md` |

```text
（有案例后）TASKS 追加 AT-XXX → 执行 test_task/<案例>.md
        ├─ PASS → §3 历史 + 删 TASKS 行 + push
        └─ FAIL → test-to-settle/test_bug-*.md → round §1
```

---

## 2. 新建第一个案例

```bash
cp test_task/case-TEMPLATE.md test_task/AT-XXX-<简述>.md
```

1. 编辑案例文件（步骤、预期）  
2. 在 [`TASKS.md`](../TASKS.md) **AT-*** 节按下方模板**追加一条**（不要提前占位）  
3. 占坑：`未开发` → `占用-<名字>` → push  

### TASKS.md 条目模板（追加时用）

```markdown
#### AT-XXX: <标题>

- **状态**: 未开发
- **占用者**: —
- **案例文件**: `test_task/AT-XXX-<简述>.md`
- **工作量**: ~Xh
- **依赖**: …
- **可并行**: ✅ / ❌
- **验收**: 案例 §3 PASS + **删本节 TASKS 行**；FAIL → test_bug + round
- **commit 模板**: `test(at-xxx): <简述> PASS by <Agent>`
```

---

## 3. 执行与结果

| 结果 | 动作 |
|---|---|
| **PASS** | 案例 §3 追加（Agent、时间、基线、已成功）→ **删 TASKS AT 行** → push |
| **FAIL** | `cp test-to-settle/test_bug-TEMPLATE.md test-to-settle/test_bug-…` → 案例 §3 记 FAIL → 不擅自改业务代码 |

---

## 4. 案例目录

| AT ID | 案例文件 | 状态 | 说明 |
|---|---|---|---|
| AT-001 | [`AT-001-qa-agent-http-smoke.md`](AT-001-qa-agent-http-smoke.md) | **已通过** | 125 qa-agent · 开发机直连 `182.168.1.125:8001` |
| — | [`case-TEMPLATE.md`](case-TEMPLATE.md) | 模板 | 复制后新建案例 + TASKS 占行 |

---

*无未完成 AT 时不占 TASKS；完工历史只看 `test_task/` §3。*
