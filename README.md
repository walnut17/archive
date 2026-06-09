# 投委会档案管理系统

> 投委会专属档案管理与智能分析 Web 应用
> **当前任务**:实施 **Plan I — 智能问答 Agent**(Spring AI 1.1,基线已就绪,等你开工)
> **基线 commit**:`1473461`(M0~M2 + Plan A~G 全部完工 + Plan I 方案定稿 + v1.1 评审修)
> **接手 AI 必读**: 本 README + plan-I §0 接手 Agent 必读

---

## 🚨 0. 你是谁?你要做什么?(接手 AI 看这里就够)

**你的唯一任务**:**实施 Plan I 智能问答 Agent**,把现有 `QaController` 写死的"search → rerank → generate"三步管道,**升级为 Spring AI 1.1 智能 Agent**,支持自适应选工具、语义定位项目、追问用户、多轮对话。

**3 步开工**:
1. **`cat .mavis/plans/plan-I-agent-implementation.md` §0「接手 Agent 必读」**(~10 分钟)
2. 按 §1 执行顺序表, 从 I-1 开始, **每子项一个 commit**
3. 完工写 `deliverable.md`, 通知 Mavis 沙箱审

**特别预警** —— 反复看 3 遍:
- ⚠️ **Spring AI 1.1 公开 API 没有 `ReactAgent` class**(那是 1.2 路线 / 阿里云 Spring AI Alibaba 概念)。本项目用 `ChatClient` + `@Tool` 注解 + `Advisor` + **手写 5 步 ReAct 循环**。详见决策 doc §1.2.1.1 第 6 点 + plan-I §0.3。
- ⚠️ **`spring.ai.agent.max-iterations` 不存在** —— 5 步上限**硬编码在 `AgentEngine` 的 for 循环里**(`MAX_ITERATIONS = 5`)。
- ⚠️ **业务需求 §4.4「多轮对话」必须实现**(plan-I I-13 子项)—— 不要跳过。

---

## 📊 1. 任务概览(Plan I)

| 项 | 值 |
|---|---|
| **任务 ID** | Plan I(智能问答 Agent) |
| **基线** | `1473461` |
| **目标 commit 数** | **13+**(每子项一个) + 1 聚合 |
| **工期估算** | **~8.3 天** |
| **关键路径** | I-3 → I-9 → I-10 → I-11 |
| **可并行** | I-4 / I-5 / I-6 / I-7 / I-8 / I-12 / I-13(7 个) |
| **零回归** | 现有 M0~M2 + Plan A~G 全部功能不能挂 |
| **降级路径** | Spring AI 挂时 QaController 走老 GlmService |

**13 子项工时表**(详细看 plan-I §1):

| # | 范围 | 估时 | 关键 |
|---|---|---|---|
| I-1 | pom.xml 加 Spring AI BOM + 4 starter | 0.2 天 | 不引 autoconfigure-agent |
| I-2 | application.yml 加 Spring AI + agent 开关 | 0.1 天 | `spring.ai.agent.enabled=false` 即降级 |
| I-3 | agent 包骨架 + 5 DTO + Listener | 0.5 天 | `@Tool` 接口 + ChatClient config |
| I-4 | SearchFulltextTool | 0.5 天 | 复用现有 KnowledgeSearchService |
| I-5 | FindProjectTool + project FULLTEXT 索引 | 0.5 天 | **语义锁项目**关键工具 |
| I-6 | QueryMysqlTool(白名单 + **聚合** + 操作符) | 1.5 天 | **安全重点** |
| I-7 | LlmSummarizeTool + AskClarificationTool | 0.5 天 | 复用 LLMProvider |
| I-8 | GetProjectBusinessDataTool | 0.5 天 | 复用 Plan C-5 |
| I-9 | **AgentEngine + ChatClient + 手写 5 步 ReAct 循环** | **1.5 天** | **最关键** |
| I-10 | QaController 改造 + 降级 | 0.5 天 | `@ConditionalOnProperty` 开关 |
| I-11 | 端到端集成测试(10 用例) | 1 天 | mvn test + 浏览器 |
| I-12 | 前端 Knowledge.vue + AgentStepsPanel | 0.5 天 | Vue 3 + Element Plus |
| I-13 | **多轮对话** + MessageChatMemoryAdvisor + chat_memory 表 | 0.5 天 | **补业务需求 §4.4 漏实现** |

---

## 📚 2. 必读文档(5 份,按顺序)

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `.mavis/plans/plan-I-agent-implementation.md` | 1192 | **你的主 spec**——12 子项详细 + 验收 + commit 规范 + 完工 checklist |
| ② | `docs/AGENT-IMPL-PLAN.md` | 252 | 总览:6 工具 + 工期 + 风险 + 集成点 + 完工验收 |
| ③ | `docs/AGENT-FRAMEWORK-DECISION.md` | 885 | 决策:Spring AI 1.1 + **不引** spring-ai-alibaba,**踩坑预警 §1.2.1.1 第 6 点** |
| ④ | `docs/AGENT-REQUIREMENTS.md` | 257 | 业务:15 真实问题 + 7 场景 + 验收标准(§6 验收场景) |
| ⑤ | `docs/AGENT-RESEARCH.md` | 194 | 调研:7 框架评分 + Top 3 + 资源链接(必看 §5 资源链接) |

**参考文档(按需查)**:
- `docs/ENVIRONMENT-DEPENDENCIES.md` (330) — 硬件/网络/凭证,部署细节
- `docs/DEV-STANDARDS.md` (466) — 开发标准,**§7.2 完工交回清单必读**
- `docs/LESSONS-LEARNED.md` (19KB) — 15+ 踩坑,**避免重蹈覆辙**
- `docs/TEAM-ARCHIVE.md` (12KB) — 沙箱 SSH 重建 + 环境
- `docs/ARCHITECTURE-v2.md` (685) — 现有架构基线
- `docs/DB-SCHEMA-v2.md` (1060) — 现有 schema(你要给 project 表加 FULLTEXT 索引)
- `docs/REQUIREMENTS-v1.md` (872) — 业务全貌

---

## 🎯 3. 第一天:5 步开工

### Step 1: Clone 仓库 + 验证基线(2 分钟)

```bash
git clone -b minimax git@gitee.com:frisker/projects-online.git
cd projects-online

# 验证基线 = 1473461
git rev-parse HEAD
# 期望: 14734613180f51c14cf3920b428adc6a9618d879

# 沙箱编译验证(接手 AI 必跑)
cd /workspace/projects-online-clone  # 或本机
mvn compile -DskipTests -B -o
# 期望: BUILD SUCCESS(零回归,现有代码全过)
```

### Step 2: 读 plan-I §0 接手 Agent 必读(10 分钟)

```bash
cat .mavis/plans/plan-I-agent-implementation.md | head -100
```

**§0 必看 3 处**:
- §0.2 必读文档(7 份)
- §0.3 关键技术点(ReactAgent 幻觉预警 + 资源链接)
- §0.4 基线 commit `1473461` 验证方法

### Step 3: 读决策 doc + 业务 doc(15 分钟)

```bash
# 决策(踩坑预警 3 处)
sed -n '40,90p' docs/AGENT-FRAMEWORK-DECISION.md
sed -n '1,15p' docs/AGENT-FRAMEWORK-DECISION.md   # §1.2.1.1 第 6 点

# 业务 15 真实问题
cat docs/AGENT-REQUIREMENTS.md
```

### Step 4: 沙箱编译验证(1 分钟,关键)

```bash
# 沙箱内
cd /workspace/projects-online-clone
mvn compile -DskipTests -B -o
# 期望: BUILD SUCCESS,无 WARNING(本项目 Spring Boot 3.3 + JDK 17)
```

**编译过不了,先看 `docs/LESSONS-LEARNED.md`**(15+ 条历史坑,可能命中)

### Step 5: 开始 I-1(2 小时)

```bash
# 看 plan-I §2 I-1 详细规范
sed -n '95,200p' .mavis/plans/plan-I-agent-implementation.md

# 编辑 pom.xml
vim backend/pom.xml
# 加 spring-ai-bom 1.1.0 + 4 个 starter
# 详见 plan-I §2 I-1 关键代码段

# 跑编译验证
mvn compile -DskipTests -B
# 期望: BUILD SUCCESS,依赖下载成功

# commit + push
git add backend/pom.xml
git commit -m "chore(deps,I-1): add Spring AI 1.1 BOM + 4 starters"
git push origin minimax
```

---

## 📝 4. 13 子项执行规范(总览)

**每个子项**:
1. 读 `plan-I §2 <I-N>` 详细规范
2. 按规范写代码(配套代码 + SQL + yml 改)
3. 跑验收(`mvn compile` / `mvn test` / `npm run build` / 浏览器)
4. **一个 commit + push**(不囤)
5. 标记 plan-I §3 完工 checklist

**关键路径**(必须按序):
```
I-1 → I-2 → I-3 → [I-4 ~ I-8 并行] → I-9 → I-10 → I-11
                       ↓
            I-12 / I-13(与 I-11 并行)
```

**完工验收清单**(必跑,plan-I §3):
- [ ] `mvn compile -DskipTests -B -o` 0 错
- [ ] `mvn test` 10 个 AgentIntegrationTest 全过
- [ ] `npm run build` 0 错
- [ ] 浏览器 5 个端到端问题都能答(看 plan-I §3)
- [ ] 关 `spring.ai.agent.enabled=false` 重启 → 问问题 → 走老路径(零回归)
- [ ] `llm_call_log` 新增 ≥ 3 条(`scenario=AGENT_*`)

---

## 🛠 5. 完整 13 子项速查(给接手 AI 当 checklist)

> **速查表**。每项**详细 spec 在 plan-I §2 详读**。这里只是"确认你有没有漏"。

| # | 文件 / 改动 | 验收命令 |
|---|---|---|
| **I-1** | `backend/pom.xml` 加 BOM + 4 starter | `mvn dependency:tree \| grep spring-ai` |
| **I-2** | `backend/src/main/resources/application.yml` 加 `spring.ai.*` | 启动日志看到 `OpenAiChatModel configured` |
| **I-3** | `backend/src/main/java/com/archive/agent/` 7 个文件(AgentConfig + 3 DTO + AgentStep + AgentTool + 2 listener) | `mvn compile` 0 错 |
| **I-4** | `agent/tool/SearchFulltextTool.java` | `SearchFulltextToolTest` 3 测例 |
| **I-5** | `agent/tool/FindProjectTool.java` + `ProjectRepository.searchByNameOrCustomerFulltext` + `db/migration/I-find-project-fulltext.sql` | `FindProjectToolTest` 3 测例 + 浏览器端到端 |
| **I-6** | `agent/tool/QueryMysqlTool.java`(白名单 + **聚合** + 操作符 + 注入防护,**重点子项**) | 8 测例(含聚合 + 越权 + 注入 + 边界) |
| **I-7** | `agent/tool/LlmSummarizeTool.java` + `AskClarificationTool.java` | 单元测试 |
| **I-8** | `agent/tool/GetProjectBusinessDataTool.java`(+ `TodoRepository.countByProjectIdAndStatus` 1 个方法) | 端到端 |
| **I-9** | `agent/AgentEngine.java` + `agent/prompt/AgentSystemPrompt.java` + `AgentFewShots.java`(**最关键**) | `AgentEngineTest`(mock ChatClient) |
| **I-10** | `controller/QaController.java` 改造 + `QaResponse` 加 3 字段 | `QaControllerTest` 3 测例 |
| **I-11** | `backend/src/test/java/com/archive/agent/AgentIntegrationTest.java`(10 测例) | `mvn test` 全过 |
| **I-12** | `frontend/src/components/AgentStepsPanel.vue` + `frontend/src/views/Knowledge.vue` 改造 | `npm run build` 0 错 + 浏览器 |
| **I-13** | `db/migration/I-chat-memory.sql` + `agent/ChatMemoryConfig.java` + `agent/MultiTurnController.java` + `agent/MultiTurnService.java` | 浏览器连问 3 轮 + 重启不丢 |

---

## 🧪 6. 端到端测试 5 个问题(完工必跑)

**前提**:Spring Boot 启动,MySQL 跑 v2 schema + G-llm-call-log.sql + I-find-project-fulltext.sql + I-chat-memory.sql

**测试场景**(plan-I §I-11 + IMPL-PLAN §6):

1. **"新能源那个项目今年盈利怎么样?"** → 看到 4-5 步 agent 思考 → 锁定 PRJ-2026-001 → 答案
2. **"PRJ-2026-001 剩余金额?"** → 调 get_project_business_data → 数字准确
3. **"现在总共还没结清的项目有几个?涉及总金额?"** → 调 query_mysql(`aggregate=count` + `operator!=`) + `aggregate=sum` 2 次 → **SQL 层算聚合,LLM 不数行**
4. **"今年否决了哪些项目?"** → 调 query_mysql + search_fulltext 综合
5. **多轮对话**(I-13):第 1 轮"PRJ-2026-001 怎么样" → 第 2 轮"它的剩余金额" → 第 3 轮"谁负责" → 都自动锁定 PRJ-2026-001

**降级测试**:`application.yml` 设 `spring.ai.agent.enabled=false` → 重启 → 问问题 → `agentMode=false`(老路径)

**埋点测试**:
```sql
SELECT scenario, COUNT(*) FROM llm_call_log GROUP BY scenario;
-- 期望: 出现 AGENT_STEP / AGENT_FINAL / AGENT_TOOL / AGENT_FALLBACK
```

**关键修复记录**(2026-06-09): 你原问的"还没结清的项目有几个"暴露 QueryMysqlTool **只返 rows 不做聚合**的 bug。
- 修复: I-6 加 `aggregate` (count/sum/avg/max/min/group_by) + `operator` (= / != / > / >= / in / like / is_null)
- LLM **必须**用 SQL 算聚合,**禁止**自己 rows[].size() / sum()(精度 / 截断问题)
- I-6 估时 1 → 1.5 天, 总工期 7.8 → 8.3 天
- 详见 plan-I §2 I-6 修订段

---

## 🆘 7. 卡住怎么办?

| 问题 | 怎么办 |
|---|---|
| Spring AI 1.1 API 找不到 `ReactAgent` | 看 plan-I §0.3 踩坑预警 + 决策 §1.2.1.1 第 6 点 + AGENT-RESEARCH §3.1 |
| Spring AI BOM 找不到 / 版本对不上 | 改用 `spring-ai-bom` 1.0.6(降级方案) |
| 智谱 GLM 4xx 错 | `application.yml` 加 `spring.ai.openai.chat.options.model=glm-4-flash` 显式指定 |
| `@Tool` 注解不生效 | 用 `MethodToolCallbackProvider.builder().toolObjects(tools).build()` 暴露 |
| 多轮对话 LLM 不带上下文 | 确认 `MessageChatMemoryAdvisor` bean 注入 ChatClient,`conversationId` 从 Controller 传 |
| 编译错 | `mvn clean compile -DskipTests -B`,看 `LESSONS-LEARNED.md` |
| **LLM 框架装不上** | **不要死磕**,在 `deliverable.md` 报告"卡住:XXX",Mavis 会接管 |
| **接手 AI 没遇到但 PM 漏写的** | **不要猜**,在 `deliverable.md` 报告"问题:XXX,需要 PM 决策" |

---

## ✅ 8. 完工产出(`deliverable.md`)

完工后**写** `deliverable.md` 提交给 Mavis 沙箱审。**必含**:

```markdown
# Plan I 完工报告

## 1. 13 commit 链接
- I-1 commit: <hash>
- I-2 commit: <hash>
- ...
- I-13 commit: <hash>
- 聚合 commit: <hash>

## 2. 编译 / 测试 / 构建 截图
- mvn compile: [截图或日志]
- mvn test: [截图或日志,10 个 AgentIntegrationTest 全过]
- npm run build: [截图或日志]

## 3. 端到端浏览器测试结果
- 问题 1 (新能源那个项目): [答案 + steps 截图]
- 问题 2 (剩余金额): [数字 + steps 截图]
- ...
- 多轮对话 3 轮: [截图]
- 降级测试: [截图]

## 4. 已知问题 / 留 TODO
- (列 5-10 个 I-11 测出来的 Prompt 调优点)
- (列任何没做完的)

## 5. owner 审请关注
- 决策对齐: plan-I §0.3 三个踩坑预警都没踩? ✓/✗
- 零回归: 关开关走老路径? ✓/✗
- 多轮对话: 3 轮上下文保留? ✓/✗
```

---

## 📂 9. 仓库结构(扁平化,2026-06-08)

```
projects-online/
├── README.md                                # 本文件(接手 AI 入口)
├── .gitignore
│
├── backend/                                 # Spring Boot 3.3 + JPA
│   ├── pom.xml
│   ├── startup.ps1
│   └── src/main/java/com/archive/
│       ├── agent/                           # 🆕 Plan I 新增(~14 个文件)
│       │   ├── AgentConfig.java
│       │   ├── AgentEngine.java
│       │   ├── AgentRequest.java
│       │   ├── AgentResponse.java
│       │   ├── AgentStep.java
│       │   ├── ChatMemoryConfig.java        # I-13
│       │   ├── MultiTurnController.java     # I-13
│       │   ├── MultiTurnService.java        # I-13
│       │   ├── prompt/
│       │   │   ├── AgentSystemPrompt.java
│       │   │   └── AgentFewShots.java
│       │   ├── tool/
│       │   │   ├── SearchFulltextTool.java  # I-4
│       │   │   ├── FindProjectTool.java     # I-5
│       │   │   ├── QueryMysqlTool.java      # I-6 重点
│       │   │   ├── LlmSummarizeTool.java    # I-7
│       │   │   ├── AskClarificationTool.java# I-7
│       │   │   └── GetProjectBusinessDataTool.java  # I-8
│       │   └── listener/
│       │       ├── LlmCallListener.java
│       │       └── ToolCallListener.java
│       ├── controller/QaController.java     # 🆕 I-10 改造
│       ├── service/GlmService.java          # 降级路径用
│       ├── provider/LLMProvider.java        # 抽象层
│       ├── repository/ProjectRepository.java # 🆕 I-5 加 searchByNameOrCustomerFulltext
│       ├── common/LlmScenario.java          # 🆕 I-9 加 AGENT_STEP/FINAL/TOOL/FALLBACK
│       └── ...(现有 M0~M2 代码,零回归)
│
├── frontend/
│   ├── src/views/Knowledge.vue              # 🆕 I-12 改造
│   └── src/components/
│       └── AgentStepsPanel.vue              # 🆕 I-12 新增
│
├── docs/                                    # ⭐ 11 份核心文档
│   ├── REQUIREMENTS-v1.md                   # 业务全貌
│   ├── ARCHITECTURE-v2.md                   # 现有架构
│   ├── DB-SCHEMA-v2.md                      # 现有 schema
│   ├── SIMILAR-PRODUCTS.md
│   ├── ARCH-REUSE-AUDIT.md
│   ├── DEV-STANDARDS.md                     # §7.2 完工交回清单
│   ├── TEAM-ARCHIVE.md                      # 沙箱 SSH + 环境
│   ├── LESSONS-LEARNED.md                   # 15+ 踩坑
│   ├── DEPLOYMENT-LOG.md
│   ├── ENVIRONMENT-DEPENDENCIES.md         # 🆕 硬件/网络/凭证
│   ├── AGENT-IMPL-PLAN.md                   # 🆕 Plan I 总览
│   ├── AGENT-REQUIREMENTS.md                # 🆕 业务需求
│   ├── AGENT-RESEARCH.md                    # 🆕 调研
│   └── AGENT-FRAMEWORK-DECISION.md          # 🆕 决策(踩坑预警)
│
├── .mavis/plans/
│   ├── plan-A~G/                            # 已完工
│   ├── plan-H-smart-qa-agent.md             # Plan H 任务
│   └── plan-I-agent-implementation.md       # 🆕 Plan I 主 spec(接手 AI 主读)
│
├── config/                                  # 模板(用户复制填真实值)
├── deploy/                                  # Caddy / WinSW / SQL
└── scripts/sync.sh                          # minimax 跟 main 同步
```

---

## 🌿 10. Git 工作流(接手 AI 必守)

```bash
# 每子项一个 commit
git add <改动文件>
git commit -m "<type>(<scope>,I-N): <description>"

# commit 模板
feat(agent,I-1): add Spring AI 1.1 BOM + 4 starters
feat(agent,I-9): add AgentEngine with ChatClient + @Tool + 5-step ReAct loop
test(agent,I-11): 10 end-to-end integration tests
fix(agent,I-12): Knowledge.vue import http error
docs(agent,Plan I): complete - 13 subitems, all green  # 聚合 commit

# 推 minimax(不直推 main)
git push origin minimax

# 完工后
git tag plan-I-v1.0
git push origin plan-I-v1.0
```

**不囤 commit** —— 13 子项 = 13+ commit + 1 聚合。

---

## 📞 11. 找谁 / 紧急情况

| 项 | 联系人 |
|---|---|
| **业务方 / 项目方** | frisker(投委会秘书)— 业务决策 + 过目 PR |
| **Mavis 沙箱 PM** | 主 agent —— 写方案 + 审完工,不写代码 |
| **接手 AI** | 你(生产环境 AI)—— 写代码 + 提 PR |
| **运维** | 无人(单机,自己维护) |

**紧急情况**:
- 后端崩了 → `docs/TEAM-ARCHIVE.md` §11
- 数据库连不上 → §11.3
- LLM 限速 → 切 `spring.ai.openai.chat.options.model=glm-4-flash` 或换 GLM-4-Plus
- 推不上去 → `docs/TEAM-ARCHIVE.md` §11.5(SSH key 检查)
- **Spring AI 装不上** → 报 `deliverable.md` 给 Mavis,**不要死磕**

---

## 📜 12. 文档演进(基线 1473461 之前)

| 时间 | 事件 |
|---|---|
| 2026-06-05 | v1 架构(双机主备,RAG 纠结) |
| 2026-06-06 | v2 架构(定位单机轻量) |
| 2026-06-07 | v3 架构定稿(智谱 + 不向量化) + M0 端到端 |
| 2026-06-08 上午 | M1 档案 CRUD 完工 |
| 2026-06-08 下午 | M2 知识库问答框架 + 修复 |
| 2026-06-08 傍晚 | 业务需求 v1 + 8 份核心文档 + 6 个 plan |
| 2026-06-08 晚 | 仓库扁平化 + README 重写 |
| 2026-06-09 上午 | Plan A~G 完工 + Plan H/I 方案定稿 |
| 2026-06-09 中午 | **Plan I v1.1 评审修**(3 P0 + 12 P1) — 接手 AI 现在用的基线 |

---

*接手 AI 看完 README,直接 `cat .mavis/plans/plan-I-agent-implementation.md` §0,然后 §1 执行顺序表开工。*
*Mavis(沙箱 PM)在此待命审完工。*
