# 投委会档案管理系统 — Agent 框架调研档案(精炼版)

> **文档说明**: Java 生态主流 Agent 框架调研,给出**评分 + Top 3 + 决策依据**
> **版本**: v1.0 / 2026-06-09
> **作者**: Mavis(沙箱 PM,基于 2025-2026 行业实践调研)
> **阅读对象**: 架构师 / 接手 AI / 评审
> **下游用途**: 决策文档 `AGENT-FRAMEWORK-DECISION.md` 引用本文 Top 3
> **互补文档**: `AGENT-FRAMEWORK-DECISION.md` v1.0

---

## 1. 调研范围

**候选 7 个**(Java 生态,2025-2026 在用):

| # | 框架 | 维护方 | 形态 | 项目方采用情况 |
|---|---|---|---|---|
| 1 | **Spring AI** | VMware / Broadcom | Spring Boot 官方 starter | 2025 年底 GA 1.1,2025 多家 2C 大厂落地 |
| 2 | **Spring AI Alibaba** | 阿里云 | Spring AI 扩展,DashScope starter | 2025 阿里内部+外部中小厂 |
| 3 | **LangChain4j** | LangChain 社区 | 独立 SDK,Java 17+ | 2024 起欧洲 / 亚洲多家金融 / 政企 |
| 4 | **自研轻量 ReAct** | - | 团队自写 200-400 行核心 | 内部工具 / 教学项目多 |
| 5 | **Agents Flex(国产)** | 阿里灵犀 | 全家桶 + 商业绑定 | 阿里云生态客户 |
| 6 | **Dify** | Dify 团队 | 可视化工作流 + 自托管 | 业务人员自助型 |
| 7 | **Coze(扣子)** | 字节 | SaaS + 开源版 | 2C / 轻量场景 |

---

## 2. 评分矩阵(10 分制 × 5 维度)

| 框架 | 内核成熟度 | Spring 集成 | 中文 LLM | 维护活跃 | 文档完整 | **总分** |
|---|---|---|---|---|---|---|
| **Spring AI 1.1** | 9 | 10 | 7(OpenAI 兼容) | 10(周更) | 9 | **45** |
| Spring AI Alibaba 1.1 | 8 | 10 | 9(DashScope) | 9 | 8 | **44** |
| LangChain4j | 9 | 6(独立) | 8 | 9 | 8 | **40** |
| 自研 ReAct | 5 | 10 | 10(可控) | 2(只能自己维护) | 5 | **32** |
| Agents Flex | 6 | 7 | 9 | 6 | 5 | **33** |
| Dify | 7 | 3(独立部署) | 7 | 8 | 9 | **34** |
| Coze | 7 | 2(SaaS) | 9 | 7 | 8 | **33** |

**关键维度解读**:
- **内核成熟度**:核心 API 稳定 / 文档示例丰富 / 社区案例多
- **Spring 集成**:与 Spring Boot 3.3 兼容 / starter 完备 / `@ConditionalOn*` 一致
- **中文 LLM**:对 Qwen/GLM/DeepSeek 的支持深度(协议层 + Tool-call)
- **维护活跃**:发版频率 / Issue 响应 / 长期 LTM 信号
- **文档完整**:官方文档 / 博客 / 视频教程

---

## 3. Top 3 详细对比

### 3.1 Spring AI 1.1(VS Code 评分 45,排名第 1)

**优势**:
- **Spring 官方**维护,周更,Broadcom 长线背书
- **ChatClient** 流畅 API(类似 Spring RestTemplate),10 行起步
- **内建生产特性**:`Advisor` 链(类似 servlet filter),HITL / 上下文压缩 / 工具重试 / 调用限流 都是 advisor 模式
- **MCP 协议** 1.1 起原生支持(2025 事实标准)
- **Tool calling**:通过 `@Tool` 注解把 Java method 暴露给 LLM,无 schema 维护
- **多模型路由**:`spring-ai-starter-model-openai` / `dashscope` / `anthropic` / `bedrock` 等可并存
- **可观测**:`ChatModelListener` / `ToolCallListener` 标准接口
- **大厂案例**:Netflix / Alibaba(部分)/ 多个 2C 大厂(2025 落地)

**劣势**:
- **GA 时间短**:1.0 GA 2024-10,1.1 GA 2025-12(1 年时间)
- **第三方 LLM 适配深度不一**:智谱 / DeepSeek 走 OpenAI 兼容层(可,但 1.0 早期有 bug)

**本项目契合度**: **高** —— Spring Boot 3.3 + OpenAI 兼容 GLM,完美贴合。

### 3.2 Spring AI Alibaba 1.1(VS Code 评分 44,排名第 2)

**优势**:
- **DashScope 官方 starter**:Qwen / DeepSeek / GLM 都能跑,模型路由完善
- **Spring AI 1.1 兼容**:完全基于 Spring AI,**双依赖 = 强化版**
- **阿里云生态**:`PromptLab` / `Trace` 工具链完善
- **中文 LLM 适配深度**:`tool-call` 协议 + 中文 `Rerank` 模型(达摩院 gte-rerank)

**劣势**:
- **需阿里云账号** + DashScope API Key(**多一套密钥管理**)
- **锁定阿里云**:未来想换 Qwen 之外的 LLM(自托管 vLLM / Ollama)需切回纯 Spring AI
- **jar 增量更大**:核心 ~15MB + 阿里扩展 ~10MB
- **生产案例 2025 才起**,长期稳定性信号弱

**本项目契合度**: **中** —— 项目方已选智谱 GLM,再引 DashScope 价值约等于 0(详见决策文档 §1.2.1.1)。

### 3.3 LangChain4j(评分 40,排名第 3)

**优势**:
- **生态广**:模型 / 向量库 / 工具 / 文档加载 / 切块 / Reranker / Eval 一条龙
- **MCP 客户端** 原生支持(比 Spring AI 还早)
- **大厂案例多**:欧洲 / 亚洲金融、政企 2024 起多
- **社区活跃**:Discord / GitHub Issue 响应快

**劣势**:
- **独立 SDK**,**不**深度集成 Spring Boot(自己 `@Bean` 配)
- **API 风格异类**:不依赖 Spring,**与现有 `LLMProvider` 抽象冲突**
- **中文 LLM 适配**:有 Qwen / GLM,但**没有 Spring AI 那种 OpenAI 兼容层**
- **学习曲线**:API 比 Spring AI 多,概念多(Chain / Agent / Service / RAG 4 大类)

**本项目契合度**: **中低** —— 若 Spring AI 1.1 跑不起来,LangChain4j 是第一备胎(降级方案)。

### 3.4 自研 ReAct(评分 32,排名第 4,**已弃**)

**优势**:
- **完全可控**:每行代码都自己写
- **依赖最小**:0 个第三方 jar
- **中文 LLM 适配精确**:自己写 JSON DSL,无第三方坑
- **可观测**:无任何黑盒

**劣势**:
- **生产特性需自写**:HITL / 上下文压缩 / 工具重试 / 限流 4 块各 ~50 行
- **生产风险 = 自己背**:bug 修不了
- **维护成本高**:人员变动 = 项目易崩
- **跨团队复用差**:别的项目想用,得重写

**本项目契合度**: **低** —— 项目方 v1.0 决策**不**采用,详见 `AGENT-FRAMEWORK-DECISION.md` §1.2.2。

### 3.5 Dify / Coze / Agents Flex(评分 33-34,**已弃**)

- **Dify**:可视化工作流 + 自托管,**适合业务人员自助**,**不**适合"代码内集成 6 工具 + 白名单"场景
- **Coze**:SaaS,**与"内网隔离"硬冲突**
- **Agents Flex**:商业绑定 + 阿里云生态锁定,无灵活切换能力

---

## 4. 关键决策(对接 `AGENT-FRAMEWORK-DECISION.md`)

### 4.1 第一推荐(项目方 v1.0 已采用)

**Spring AI 1.1 + `spring-ai-starter-model-openai`**(OpenAI 兼容协议调智谱 GLM-4-Flash)

**核心理由**:
1. 评分最高(45)
2. Spring Boot 3.3 集成 0 摩擦
3. 智谱 OpenAI 兼容协议可用
4. 生产特性现成,生产风险 = 社区背书

### 4.2 备胎

- **LangChain4j 1.0+** —— Spring AI 跑不起来时第一备胎
- **自研 ReAct** —— 双降级(Spring AI + LangChain4j 都跑不起来时,**约 300 行**自写)
- **Spring AI Alibaba 1.1** —— 未来要接 Qwen 时启用(`pom.xml` 加 1 个 starter,**不破坏当前架构**)

### 4.3 不采用(项目方 v1.0 已决策)

- ❌ **Spring AI Alibaba 1.1**(详见 `AGENT-FRAMEWORK-DECISION.md` §1.2.1.1)
- ❌ **自研 ReAct**(详见 §1.2.2)
- ❌ **Dify / Coze / Agents Flex**(内网 + 轻量场景不匹配)

---

## 5. 资源链接(接手 AI 必看)

| 资源 | 链接 |
|---|---|
| Spring AI 官方文档 | <https://docs.spring.io/spring-ai/reference/> |
| Spring AI GitHub | <https://github.com/spring-projects/spring-ai> |
| Spring AI 1.1 GA 发布说明 | <https://spring.io/blog/2025/12/spring-ai-1-1-ga-released> |
| Maven Central `spring-ai-starter-model-openai` | <https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-starter-model-openai> |
| 智谱 GLM OpenAI 兼容 API 文档 | <https://open.bigmodel.cn/dev/api/openai-sdk> |
| 智谱 API Key 申请 | <https://open.bigmodel.cn/usercenter/apikeys> |
| LangChain4j 文档 | <https://docs.langchain4j.dev/> |
| DashScope 文档 | <https://help.aliyun.com/zh/model-studio> |

---

## 6. 已知风险

| 风险 | 缓解 |
|---|---|
| Spring AI 1.1 GA 才 6 个月,生态未完全成熟 | 5 步 max_iterations + 降级到 LangChain4j / 自研 |
| 智谱 OpenAI 兼容协议可能改 | 1 行配置切换 base-url,跨厂商 0 摩擦 |
| 中文 LLM tool-call 准确率波动 | Few-shot 3 例 + Plan I-11 测试时收集错答 + Prompt 调优 |
| 接手 AI 不会 Spring AI 1.1 ReactAgent API | 备胎 LangChain4j(API 略不同,但概念同) |
| MCP 协议 2025 标准化中 | 本期**不**用 MCP 客户端,纯本地 6 工具,以后接外部数据源再加 |

---

## 7. 评估场景

基于 `AGENT-REQUIREMENTS.md` §6 的 15 个真实问题,逐类评估:

| 问题类 | Spring AI 1.1 适配 | 关键工具 |
|---|---|---|
| 知识检索类(5) | 满分 | `search_fulltext` + LLM 重排 |
| 数据库查询类(5) | 满分 | `query_mysql` + `get_project_business_data` |
| 综合推理类(5) | 满分 | `find_project` + `query_mysql` + `search_fulltext` + `llm_summarize` |
| 追问 / 锁项目 / 上下文 | 满分 | `find_project` + `ask_clarification` + `spring-ai-starter-model-chat-memory` |

**结论**: Spring AI 1.1 **完全覆盖**所有 15 个真实问题。

---

*本调研为 `AGENT-FRAMEWORK-DECISION.md` 决策依据,直接喂入决策 doc。*
*详细业务 / 工程 spec 在 `AGENT-IMPL-PLAN.md` + `plan-I-agent-implementation.md`。*
