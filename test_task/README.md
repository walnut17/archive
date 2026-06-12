# 自动化测试案例 — `test_task/`

> **本目录放自动化/半自动化测试案例**（步骤、预期）。**有真实案例时再建文件**；不要预先编示例任务。
>
> **任务占用**：有案例后，在根 [`TASKS.md`](../TASKS.md) **「自动化测试任务 AT-*」** 节追加对应 **AT-XXX**（无任务前该节保持空白）。
>
> **PASS** → 案例 **§3 执行历史** 追加 · **FAIL** → [`test-to-settle/test_bug-*.md`](../test-to-settle/README.md)

**导航**：根 [`README.md` §9`](../README.md#-9-自动化测试任务-test_task) · [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 1. 三分工

| 目录 / 文件 | 放什么 |
|---|---|
| **`test_task/*.md`** | 测试案例 + PASS 执行历史（**有案例才有文件**） |
| **`TASKS.md` AT-*** | 仅在有案例时追加占用条目 |
| **`test-to-settle/test_bug-*.md`** | 案例 FAIL 入口 → `round-*.md` |

```text
（有案例后）TASKS 追加 AT-XXX → 执行 test_task/<案例>.md
        ├─ PASS → §3 历史 + TASKS 已完成
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
- **验收**: 案例 §3 PASS + 本节 `已完成`；FAIL → test_bug + round
- **commit 模板**: `test(at-xxx): <简述> PASS by <Agent>`
```

---

## 3. 执行与结果

| 结果 | 动作 |
|---|---|
| **PASS** | 案例 §3 追加（Agent、时间、基线、已成功）+ TASKS 标 `已完成` + push |
| **FAIL** | `cp test-to-settle/test_bug-TEMPLATE.md test-to-settle/test_bug-…` → 案例 §3 记 FAIL → 不擅自改业务代码 |

---

## 4. 当前案例

| AT ID | 案例文件 | 说明 |
|---|---|---|
| AT-001 | [`AT-001-qa-agent-http-smoke.md`](AT-001-qa-agent-http-smoke.md) | 125 部署 qa-agent · **开发机**直连 `182.168.1.125:8001` |
| — | [`case-TEMPLATE.md`](case-TEMPLATE.md) | 复制模板新建案例 |

---

*无任务不编例；TASKS AT 节与案例文件一一对应，同步创建。*
