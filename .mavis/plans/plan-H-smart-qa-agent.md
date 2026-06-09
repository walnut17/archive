# Plan H: 智能问答改造 — 引入 Agent 框架

> **状态**: 多轮分析中(用户已批准启动 3 agent)
> **优先级**: 🟡 P5(改善体验,不是阻塞)
> **不阻塞主业务**:用户先去忙别的,本 plan 异步进行
> **3 agent 协作**:业务专员 + 架构研究员 + 架构师
> **目标**: 出一份"是否引入 agent 框架 + 引入哪个 + 怎么集成"的决策文档

## 背景

当前 `/api/qa/ask` 流程:
```
用户问题
  ↓
KnowledgeSearchService.search() — MySQL FULLTEXT 找 top N
  ↓
GlmService.rerank() — GLM-4-Flash 重排
  ↓
GlmService.chat() — GLM-4-Flash 拼 prompt 生成答案
  ↓
返回 { sources, answer }
```

**痛点**:
1. 流程**写死**,不能根据问题动态决定要不要再搜
2. 不能**调 MySQL 查**(比如查项目金额、查贷后状态)
3. 不能**多轮推理**(agent 风格:思考 → 行动 → 观察 → 再思考)
4. 用户问"PRJ-001 现在的剩余金额是多少",**答不出**——要查 DB
5. 一次性 prompt,长 context 容易超 token,效果差

## 目标

让智能问答具备**Agent 能力**:
- ✅ **自主规划**:能决定"先查 DB 还是先搜材料"
- ✅ **工具调用**:能调 MySQL、能调 FULLTEXT、能调 LLM
- ✅ **多轮推理**:ReAct / Plan-and-Execute 模式
- ✅ **权限隔离**:agent 调 DB 走白名单表,不能 SELECT *
- ✅ **可观测**:能看到 agent 的每一步思考(便于调试 + 演示)
- ✅ **可降级**:LLM/agent 框架挂掉时,fallback 到当前 FULLTEXT 流程

## 调研范围

### A. Java 生态的 Agent 框架
- **LangChain4j** — 主流,Java 17 友好
- **Spring AI** — Spring 官方,集成度高
- **Agents Flex**(原 Smart-Speaker) — 国产,中文社区
- **自研 ReAct** — 简单循环 + 工具注册
- **Dify / Coze 工作流** — 不算框架,可视化搭建

### B. Agent 模式
- **ReAct** — Reasoning + Acting 循环
- **Plan-and-Execute** — 先规划再执行
- **Reflexion** — 反思 + 改进
- **Multi-Agent** — 多个 agent 协作

### C. 工具调用协议
- **OpenAI Function Calling**(智谱 GLM 兼容)
- **LangChain Tool**
- **MCP(Model Context Protocol)** — 新兴标准

### D. 中文 LLM 的 Agent 能力
- **智谱 GLM-4-Flash** — 基础 function call 支持
- **智谱 GLM-4-Plus** — 更强
- **Qwen2.5 / DeepSeek** — 对比

## 决策点(本 plan 输出)

1. **是否引入** agent 框架?还是自研?
2. **引入哪个**(具体到版本)?
3. **模式选哪个**?
4. **工具集**怎么定义?最少几个?
5. **怎么与现有 FULLTEXT 流程共存**(降级路径)?
6. **数据库安全**:怎么防止 agent 乱查?
7. **工作量评估**(多少 commit、多少时间)
8. **可观测性**:怎么让用户看到 agent 在干嘛?

## 范围(本 plan H,**只调研不实现**)

✅ **输出**:
- 1 份 `docs/AGENT-FRAMEWORK-DECISION.md`(决策 + 推荐 + 不推荐项)
- 1 份 `docs/AGENT-DESIGN.md`(选定框架后,工具集 + ReAct 流程设计)
- 1 份施工 plan(Plan I),拆 commit,准备实施

❌ **不**:
- 不写任何 .java / .vue 代码
- 不动现有 QaController / KnowledgeSearchService
- 不引新依赖(到 plan I 再引)

## 3 agent 任务分工

### 业务需求分析专员

**任务**:从**业务角度**回答"用户期望"是什么

**输入**:
- 现有 Knowledge.vue / QaController / GlmService 的痛点
- 用户的诉求:"让 agent 来驱动调用 mysql / llm,改善问答效果"

**输出**: `docs/AGENT-REQUIREMENTS.md` ~200-300 行
- 用户场景(用户实际怎么用)
- 必须支持的能力清单(查 DB / 搜材料 / 多轮)
- 用户体验期望(响应时间 / 可观测)
- 失败时用户预期(降级)

### Architecture-Researcher

**任务**:**网上调研**当前主流 agent 框架,评分,推荐候选

**输出**: `docs/AGENT-RESEARCH.md` ~500-800 行
- 6+ 个 Java agent 框架对比(LangChain4j / Spring AI / 自研 / 等)
- 评分维度:学习成本 / 与 Spring Boot 集成度 / 中文 LLM 兼容性 / 性能 / 维护
- 推荐 Top 3,理由 + 不推荐项
- 实际代码片段(Hello World)

### 架构师

**任务**:**结合前两者输出,出技术方案**

**输入**:
- 业务专员的用户场景
- 研究员的框架对比
- 现有代码(M0~M2 + Plan A-G)

**输出**: `docs/AGENT-FRAMEWORK-DECISION.md` ~300-500 行
- 决策:**用 X 框架 + Y 模式**
- 与现有架构的集成点
- 工具集设计(查 DB / 搜材料 / 追问)
- 降级路径
- 性能 / 安全评估
- 风险 + 缓解
- 工作量估算

**额外产出**:`.mavis/plans/plan-I-agent-implementation.md` — Plan H 决策后的实施 plan,拆 commit

## 验证标准

3 个 agent 都完工 + 我汇总后:
- 决策清晰(不是模棱两可)
- 推荐 1 个框架,理由充分
- 与现有架构兼容(零回归)
- 工作量评估可信(<2 周)
- 有降级路径(LLM 挂 / agent 框架挂)

## 时序

```
[Round 1] 3 agent 并行
  ├─ 业务专员: 写 AGENT-REQUIREMENTS.md
  ├─ 架构研究员: 写 AGENT-RESEARCH.md
  └─ 架构师: 先看现有代码,等 1&2 完

[Round 2] 串联
  └─ 架构师: 综合前两者 → AGENT-FRAMEWORK-DECISION.md + plan-I

[汇总] 我读全部 → 决定是否提交用户 / 哪里要补充
```

## 不在本 plan 范围

- 实施代码(下一轮 plan I 做)
- 改前端 UI(等 plan I 决策后再设计)
- 切 LLM provider(本期 GLM-4-Fash 不变)

---

## v1.0 更新(2026-06-09): 决策定稿 → 转入实施

**决策已定**:`docs/AGENT-FRAMEWORK-DECISION.md` v1.0
- 推翻初版"自研 ReAct" → 采用 **Spring AI 1.1 + spring-ai-starter-model-openai**
- 理由:生产特性现成 + 中文 LLM OpenAI 兼容 + 社区背书

**实施 spec**:`.mavis/plans/plan-I-agent-implementation.md`(12 子项,~7.3 天)
**总方案**:`docs/AGENT-IMPL-PLAN.md`(给项目方 + 接手 AI)

**下一站**:项目方(用户)把仓库同步给生产环境 AI Agent,由其执行 plan-I 12 子项。

**本 plan-H 任务关闭**。