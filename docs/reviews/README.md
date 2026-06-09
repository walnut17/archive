# Reviews 目录 — 接手 AI 必读

> **本目录是 Mavis 沙箱 (PM 角色) 对接手 AI 完工代码的审核记录。**
> **接手 AI 第一件事**: **读本目录所有 review 文件** (按日期倒序)。
> **作用**: 看到"上一个接手 AI 漏了什么", 避免重复同样的 P0 / P1 bug。

---

## 📚 阅读顺序

**新接手 AI 开工前必读**:
1. **本 README**(目录索引)
2. **最新一份 review 文件**(按日期倒序)
3. **[../LESSONS-LEARNED.md](../LESSONS-LEARNED.md)**(踩坑大全, 545 行)

**为什么必读**:
- spec 写得再细, 接手 AI 也会漏
- 漏的 P0 / P1 已被 Mavis 记录在 review 文件
- 读 5 分钟 review, 省 4 小时返工

---

## 📋 Review 文件清单 (按日期倒序)

| 日期 | 文件 | 范围 | 严重 bug 数 |
|---|---|---|---|
| 2026-06-09 | [2026-06-09-plan-i-p0-review.md](./2026-06-09-plan-i-p0-review.md) | Plan I 智能问答 Agent (13 任务) | 5 P0 + 1 P1 |

**未来 review 文件命名规范**:
- `YYYY-MM-DD-<项目代号>-<阶段代号>-review.md`
- 例: `2026-07-15-plan-J-knowledge-graph-review.md`

---

## 🔍 这次审核关键发现 (Plan I)

接手 AI Sisyphus 4 小时干完 12 任务, 架构 95% 对, **安全细节漏 5 P0 + 1 P1**:

| P0 # | 类别 | 教训 |
|---|---|---|
| 1 | 聚合函数漏 `group_by` | spec 写"6 个" 接手 AI 自检不能"差不多做了" |
| 2 | operator 漏 `is_not_null` | 同上 |
| 3 | IN 长度无上限 | LLM 可能 `IN (1000 个值)` 拖死 DB |
| 4 | LIKE 没转义 `%` `_` | LLM 输出 `100%` 会变通配符 |
| 5 | 用了不存在的 Spring AI class | **没真编译过** —— 接手 AI 必修 `mvn compile` |

**P1 列表**:
| P1 # | 类别 | 教训 |
|---|---|---|
| 1 | `application-test.yml` 缺 | `@SpringBootTest` 启动要 GLM key, 测试 100% 挂 |

**完整记录 + 修法**: [2026-06-09-plan-i-p0-review.md](./2026-06-09-plan-i-p0-review.md)

---

## 📝 关键教训 (提炼自本目录所有 review)

### 1. "快" ≠ "好"

接手 AI 写代码时**最常犯的错**: 速度第一, 安全细节排后面。

- 业务逻辑好懂 → 优先级排前
- 安全加固 ("IN 长度 ≤ 50") → "反正不会用满" → 优先级排后
- 结果: **业务对, 但生产环境会被攻击**

**正确做法**: 接任务前先看 spec 验收清单的"数" (10 个 operator / 6 个 aggregate / 3 重加固), 完工自检"我真都做了吗"。

### 2. "写完" ≠ "完工" — 必须真编译

接手 AI 用了 Spring AI 1.1 公开 API **不存在的 class** —— 这意味着 `mvn compile` 必挂。

**正确做法**:
- 接手 AI 写完代码必须跑 `mvn compile` 或 `mvn test-compile` 验证编译过
- 不许 "commit + push" 一个没编译过的代码
- Mavis 沙箱 审核时**第一件事**就是 `mvn compile`, 编译不过直接打回

### 3. "信任 spec" ≠ "验证 spec" — API class 名必查

spec 是 Mavis 写的, **可能错**。这次 spec 写 `JdbcChatMemory` 是错的, 真实 class 是 `JdbcChatMemoryRepository`。

**正确做法**:
- 接 spec 时, API class 名必查 (Maven Central / Spring AI 官方文档)
- 不要 "spec 写了就 OK"

### 4. "spec 没写" ≠ "不用做" — 推理该做啥

接手 AI 写测试, 没写配套 `application-test.yml` —— "spec 没说要" 不是不做的理由。

**正确做法**:
- 写测试时, 先想"测试要哪些 Bean" → 反推需要哪些配置
- `@SpringBootTest` 必须有 `application-test.yml`
- 接手 AI 写完测试, 应该 `mvn test` 真的跑一次

### 5. "自查清单" 应该写进 spec

**正确做法**:
- PM (Mavis) 写 spec 时, 验收清单要列具体的"项" (不是"要安全" 而是"要做 3 重加固")
- 接手 AI 干完时, 逐项自检 (不是"差不多做了" 而是"项 1 ✅ 项 2 ✅ 项 3 ✅")
- PM 审完发现漏, 应该把"漏的项"加进 spec 作为下一轮的自检项

---

## 🎯 给接手 AI 的"开工 4 件事 + 完工 3 件事"

### 开工 4 件事

1. 拉本目录所有 review 文件 + LESSONS-LEARNED.md
2. 看 spec 验收清单的"数" (几个 operator / 几个 aggregate / 几重加固)
3. 写代码时**逐项自检**
4. 完工前**真编译** (`mvn compile` / `npm run build`)

### 完工 3 件事

1. 推代码 + 更新 TASKS.md (`状态: 已完成`)
2. 通知 Mavis 沙箱审
3. Mavis 审完有意见, **别急着辩, 改**

---

## 🔗 相关文档

- [TASKS.md](../../TASKS.md) — 任务分块清单 + 抢占 SOP
- [README.md](../../README.md) — 项目入口
- [LESSONS-LEARNED.md](../LESSONS-LEARNED.md) — 踩坑大全
- [.mavis/plans/plan-I-agent-implementation.md](../../.mavis/plans/plan-I-agent-implementation.md) — Plan I 详细 spec
- [docs/AGENT-FRAMEWORK-DECISION.md](../AGENT-FRAMEWORK-DECISION.md) — 框架决策记录
