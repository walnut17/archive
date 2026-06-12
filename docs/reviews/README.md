# 评审与对线 — `docs/reviews/`

> **本目录是各 Agent 评审、回复、跟进的正式场所**（可以「吵架」，但必须落在文件里）。
>
> - **Review Agent**：**新开**一个 review 文件，写评审意见  
> - **Subject Agent**（被评审方）：在文件**下方**写回复（是否接受、措施、结果）  
> - **Review Agent**：再看回复 → 续提要求 **或** **宣布评审结束**  
> - **只有 Review Agent 宣布结束，该 review 才算结束**（状态 `CLOSED`）

**踩坑汇总**（只读参考）：[`LESSONS-LEARNED.md`](./LESSONS-LEARNED.md)  
**DEBUG Case**（另一套流程）：[`../../test-to-settle/README.md`](../../test-to-settle/README.md) · 格式 [`CASE-FORMAT.md`](../../CASE-FORMAT.md)  
**自动化测试**（PASS/FAIL 分流）：[`../../test_task/README.md`](../../test_task/README.md)  
**协作架构总览**：[`../../MULTI-AGENT-REPO-ARCHITECTURE.md`](../../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 1. 和 `test-to-settle/` 的分工

| 目录 | 场景 | 谁结案 |
|---|---|---|
| **`test-to-settle/round-*.md`** | 部署/验收测出的 **bug**：§1 → Agent Blocks → **Reviewer(CLOSED)** → `done/` | 审查员关单 + TASKS 删行 |
| **`docs/reviews/`** | **代码/架构/交付物**评审、MOD 完工审、跨 agent 对线 | **仅 Review Agent** 在该文件宣布 `CLOSED` |

一条问题可以**同时**出现在 `test-to-settle/round`（验收 bug）和 `docs/reviews`（代码审），互相用链接关联即可。

---

## 2. 工作流

```text
Review Agent 新建 review-*.md（状态 OPEN）
        ↓
    写 Round 1 评审意见
        ↓
Subject Agent 在下方写 Round 1 回复
  （是否接受 / 处理措施 / commit / 结果）
        ↓
Review Agent 写 Round 2 跟进
        ├─ CONTINUE → 再一轮回复 …
        └─ CLOSED   → 宣布「评审结束」（唯一结案方式）
```

### 2.1 角色

| 角色 | 做什么 | 不能做什么 |
|---|---|---|
| **Review Agent** | 新建文件；写/追加评审意见；验证回复；**宣布 CLOSED** | 替 Subject 写回复 |
| **Subject Agent** | 在文件下方写回复；改代码；填 commit/结果 | **自行**把状态改 CLOSED |
| **其他 Agent** | 只读；若要插话经 PM 协调 | 擅自改他人 Round 正文 |

### 2.2 留痕（必填）

每个 Round 块必须含：

| 字段 | 说明 |
|---|---|
| **Agent** | 名字或代号 |
| **时间** | `YYYY-MM-DD HH:mm` |
| **摘要** | 本段做了什么（一两句） |

### 2.3 回复必须包含

Subject Agent 的回复里，对**每条**评审意见说明：

| 项 | 说明 |
|---|---|
| **是否接受** | 全部 / 部分 / 不接受 |
| **处理措施** | 具体改什么、改哪 |
| **结果** | commit hash、回归结论、或为何不做 |

### 2.4 何时算结束

**必须同时满足**：

1. 文件顶部 **状态 = `CLOSED`**
2. Review Agent 在最后一轮写了 **「评审结束声明」**（见模板）
3. 所有 P0/P1 必改项在回复或跟进中已有 **结果** 或 **明确 WONTFIX**（Review Agent 认可）

Subject Agent 写「我改完了」**不算**结束；Review Agent 未宣布 **不算**结束。

---

## 3. 新建 review 文件

```bash
cp docs/reviews/review-TEMPLATE.md docs/reviews/2026-06-12-MOD-05-knowledge-review.md
```

**命名建议**：

```text
YYYY-MM-DD-<范围>-review.md
sisyphus-review-MOD-NN-YYYY-MM-DD.md   # 已有惯例可沿用
REV-YYYY-MM-DD-<简述>                  # 写在文件元信息里
```

**开头状态**：一律 `OPEN`，直到 Review Agent 改 `CLOSED`。

---

## 4. 文件清单

### 4.1 对话式 review（本规范）

| 日期 | 文件 | 状态 | 说明 |
|---|---|---|---|
| — | [`review-TEMPLATE.md`](./review-TEMPLATE.md) | 模板 | 新开 review 复制 |

> 2026-06-11 之前的 `sisyphus-review-*.md` 等为**历史静态审阅**，未按 Round 对线格式；新评审请用模板。

### 4.2 历史 / 静态记录（只读）

| 日期 | 文件 | 类型 |
|---|---|---|
| 2026-06-11 | [archive/tasks-history-routing.md](./archive/tasks-history-routing.md) | 原 TASKS Plan I / v1.1 MOD / UPGRADE 占表归档 |
| 2026-06-09 | [2026-06-09-plan-i-p0-review.md](./2026-06-09-plan-i-p0-review.md) | Plan I P0 静态 review |
| 2026-06-10 | [sisyphus-code-review-2026-06-10.md](./sisyphus-code-review-2026-06-10.md) | 代码 review |
| 2026-06-11 | [sisyphus-review-MOD-*.md](./sisyphus-review-MOD-01-2026-06-11.md) | v1.1 MOD 静态 review |
| 2026-06-11 | [2026-06-11-v1.1-review.md](./2026-06-11-v1.1-review.md) | v1.1 总 review |
| — | [2026-06-10-prod-deploy-handoff.md](./2026-06-10-prod-deploy-handoff.md) | 部署 handoff |
| — | [LESSONS-LEARNED.md](./LESSONS-LEARNED.md) | 踩坑大全 |

---

## 5. 给 Agent 的一句话

| 你是… | 做什么 |
|---|---|
| **Review Agent** | `cp review-TEMPLATE.md` → 写 Round 1 → 等回复 → Round 2 **CLOSED 或 CONTINUE** |
| **Subject Agent** | **只在文件末尾**追加回复 Round；改代码；**不要**改 OPEN→CLOSED |
| **接手新人** | 先扫 **OPEN** 的 review + [LESSONS-LEARNED.md](./LESSONS-LEARNED.md) |

---

## 6. 关键教训（历史提炼）

<details>
<summary>Plan I 等历史 review 要点（点击展开）</summary>

- **快 ≠ 好**：安全细节不能排后（IN 上限、LIKE 转义、operator 齐全）
- **写完 ≠ 完工**：必须 `mvn compile` / `npm run build`
- **spec 可能错**：API class 名要查官方文档
- **spec 没写 ≠ 不用做**：测试要有 `application-test.yml` 等配套

完整：[2026-06-09-plan-i-p0-review.md](./2026-06-09-plan-i-p0-review.md)

</details>

---

## 7. 相关链接

- [`../../MULTI-AGENT-REPO-ARCHITECTURE.md`](../../MULTI-AGENT-REPO-ARCHITECTURE.md) — 多 Agent 架构（可套用）
- [根 README §1.6](../../README.md#16-评审对线-docsreviews) — Review / Subject Agent
- [TASKS.md](../../TASKS.md) — 开发 + AT 任务占用
- [test-to-settle/complexity.md](../../test-to-settle/complexity.md) — 验收 bug 升级 PM 拍板
- [architecture/](../architecture/README.md) — 架构基线

---

*2026-06-11 起：review 文件 = 对线线程；仅 Review Agent 可 CLOSED。*
