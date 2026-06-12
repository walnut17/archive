# 项目评价报告（2026-06-12）

> 评估对象：投委会档案管理系统  
> 评估范围：仓库结构、后端、前端、Python qa-agent、测试、文档、配置与部署资产  
> 评估方式：基于当前仓库静态阅读与配置/测试资产盘点，未接入生产环境、真实 MySQL 或真实 GLM 调用

---

## 1. 总体结论

这是一个面向投委会业务的档案管理与智能分析系统，核心目标是把项目材料、会议决议、业务事实抽取和智能问答集中到一个 Web 应用中。项目已经具备较完整的业务建模、文档体系、后端测试基础和单机部署方案；同时也处在明显的架构演进期，尤其是智能问答能力正在从 Java 内嵌 Agent 迁移到 Python FastAPI 微服务。

综合评价：**业务方向清晰、工程沉淀较好，但交付工程化和质量门禁仍偏弱。** 当前项目适合继续迭代，但在正式扩大使用范围前，建议优先补齐 CI、敏感配置防护、MySQL 专项测试、qa-agent 回归缺陷闭环和前端测试。

| 维度 | 评价 | 说明 |
|---|---|---|
| 业务价值 | 较高 | 围绕投委会档案、事实抽取、跨项目问答，场景明确且痛点集中。 |
| 架构设计 | 良好 | 后端 BFF + Vue SPA + Python qa-agent 的方向合理；但 Java Agent、Python qa-agent、FULLTEXT 老路径并存，维护复杂度偏高。 |
| 后端质量 | 较好 | Spring Boot 分层清楚，测试覆盖了不少核心工具和 v1.1 集成场景。 |
| 前端质量 | 中等 | Vue 3 技术栈清晰，页面规模可控，但缺少自动化测试。 |
| 测试与质量门禁 | 中等偏弱 | 后端和 qa-agent 有测试，前端无测试；仓库未发现 CI/CD 配置。 |
| 文档与协作 | 较强 | README、架构、需求、运维、review、handoff 文档体系完整，多 Agent 协作规范明确。 |
| 部署与运维 | 中等 | Windows 单机 + Caddy + WinSW 方案清楚，但平台路径绑定明显，自动化程度不足。 |
| 安全与配置 | 中等 | JWT/RBAC 基线存在；但 `.gitignore` 未显式忽略 `config/config.json`，CORS 和 actuator 默认策略需生产收紧。 |

---

## 2. 项目概览

### 2.1 项目定位

项目 README 将系统定义为“投委会专属档案管理与智能分析 Web 应用”，目标是让投委会秘书或委员能快速回答项目材料、抵押物处置、跨项目统计等问题。当前核心能力包括：

- 项目档案与材料版本管理。
- 智能问答 Agent。
- 关键事实抽取。
- 跨项目聚合分析。
- 业务术语字典。

依据：`README.md`、`docs/requirements/REQUIREMENTS.md`、`docs/architecture/`。

### 2.2 技术栈

| 层 | 技术栈 | 主要文件 |
|---|---|---|
| 后端 | Java 17、Spring Boot 3.3.5、Spring Data JPA、Spring Security、Spring AI、MySQL、Tika、POI、OpenPDF | `backend/pom.xml` |
| 前端 | Vue 3.5、Vite 5、TypeScript、Element Plus、Pinia、Vue Router、Axios | `frontend/package.json` |
| QA 微服务 | FastAPI、Uvicorn、Pydantic、PyMySQL、pytest | `qa-agent/requirements.txt` |
| 数据库 | MySQL 8、FULLTEXT、SQL 初始化/迁移脚本 | `deploy/sql/` |
| 部署 | Windows 单机、Caddy、WinSW、批处理脚本 | `deploy/README.md`、`deploy/caddy/`、`deploy/winsw/` |

### 2.3 仓库规模与结构

当前仓库主要结构如下：

```text
backend/          Spring Boot 后端
frontend/         Vue 3 SPA
qa-agent/         Python FastAPI 问答/抽取微服务
config/           配置模板与说明
deploy/           SQL、Caddy、WinSW、脚本
docs/             需求、架构、评审、运维、交接文档
test/             测试策略
test_task/        自动化测试案例
test-to-settle/   缺陷与回归 round
upgrade_to_settle/升级 plan
```

盘点结果：

- `backend/src/main/java`：约 192 个 Java 文件。
- `backend/src/main/java/com/archive/controller`：21 个 Controller。
- `frontend/src`：36 个 TypeScript/Vue 源文件。
- `backend/src/test/java`：12 个测试类，约 94 个 `@Test`。
- `qa-agent/tests`：6 个测试文件，约 35 个测试函数。
- 仓库未发现 `.github/workflows`、`Jenkinsfile`、`Dockerfile` 或 `docker-compose`。

---

## 3. 主要优点

### 3.1 业务边界清楚，需求拆解充分

系统不是泛化知识库，而是围绕投委会档案、议案、材料、事实抽取、回收率等专门业务对象展开。`docs/requirements/`、`docs/architecture/` 和 `README.md` 对业务目标、模块边界、架构演进和协作方式都有比较明确的说明。

这一点对后续迭代很重要：Agent、检索、事实抽取和跨项目统计都有明确业务语境，不容易退化成难维护的通用聊天系统。

### 3.2 后端分层和领域模块较完整

后端采用典型 Spring Boot 分层：

- `controller/` 提供 REST API。
- `service/` 承载业务逻辑。
- `entity/`、`repository/` 承载 JPA 数据访问。
- `security/` 提供 JWT 和 RBAC。
- `agent/` 和 `qaagent/` 分别承载 Java Agent 与 Python qa-agent 接入。
- `common/` 提供统一响应、异常处理、存储和 AOP 等基础设施。

从结构看，项目已经从原型阶段进入了较完整的业务系统阶段。

### 3.3 文档体系成熟

文档不仅有 README，还覆盖了：

- 需求：`docs/requirements/`
- 架构：`docs/architecture/`
- 评审与经验：`docs/reviews/`
- 运维：`docs/operations/`
- 交付：`docs/handoff/`
- 测试策略：`test/test-strategy.md`
- 缺陷闭环：`test-to-settle/`
- 升级计划：`upgrade_to_settle/`

这类文档体系对多 Agent 协作和多人接手都很有帮助，是本项目比较突出的资产。

### 3.4 后端和 qa-agent 已有测试基础

后端测试覆盖了 Agent 工具、QueryMysql、全文检索工具、路径防护、v1.1 集成场景等；qa-agent 也有 API 契约、配置加载、parser、工具单元测试。

`test/test-strategy.md` 也明确指出 H2 集成测试与 staging MySQL 补测的边界，说明团队对测试差异有认知。

### 3.5 配置外置方向正确

`config/config.example.json` 与 `config/README.md` 说明了真实配置不进 Git，Java 与 Python qa-agent 共用配置文件。这对 GLM key、数据库密码、生产路径等敏感信息管理是正确方向。

### 3.6 生产部署资料较完整

`deploy/` 下有 SQL、Caddy、WinSW 和脚本，`docs/operations/` 与 `docs/handoff/` 也包含部署、运行、环境依赖和上线指南。对于 Windows 单机部署场景，这些资料已经比较完整。

---

## 4. 主要问题与风险

### R1. 缺少自动化 CI/CD，质量门禁不足

仓库内未发现 `.github/workflows`、`Jenkinsfile`、`Dockerfile` 或 `docker-compose`。虽然 `test/test-strategy.md` 提到了“集成测试（CI 用）”，但当前没有落地的自动化流水线。

影响：

- 合并前无法自动执行 `mvn test`、`pytest`、前端 typecheck/build。
- 回归依赖人工记忆，容易遗漏。
- 多 Agent 或多人协作时，质量标准不可自动执行。

建议优先级：**P0**。

### R2. 敏感配置防误提交存在缺口

`config/README.md` 明确说明 `config/config.json` 是真实配置且不进 Git，但根 `.gitignore` 当前未显式忽略：

```text
config/config.json
```

影响：

- 开发者或 Agent 按文档复制配置后，真实 API key、数据库密码存在误提交风险。
- 该风险虽不一定已经发生，但防护成本极低，应尽快补齐。

建议优先级：**P0**。

### R3. 智能问答路径并存，维护和行为一致性风险较高

`backend/src/main/java/com/archive/controller/QaController.java` 中存在三条问答路径：

1. Python qa-agent。
2. Java AgentEngine。
3. 老 FULLTEXT 路径。

`docs/architecture/08-qa-agent-python-service.md` 又明确说明智能问答和立项 LLM 抽取正在迁出 Java Spring AI，由 FastAPI 专司。这说明当前处于架构迁移中间态。

影响：

- 同一个问题在不同开关组合下可能得到不同结果。
- 故障降级虽然提升可用性，但也可能掩盖主路径错误。
- 测试矩阵变大，开发者理解成本上升。

建议优先级：**P1**。

### R4. 当前仍有 qa 回归缺陷处于 OPEN

`test-to-settle/STATUS.md` 显示 `round-2026-06-12-qa-regression.md` 状态为 OPEN，摘要包含“多轮 500”“思考链”等问题。

影响：

- 智能问答是项目核心卖点，稳定性问题会直接影响用户信任。
- 如果此类缺陷未闭环即上线，后续排障成本会明显增加。

建议优先级：**P1**，如计划近期上线则应提升到 P0。

### R5. MySQL 特性缺少自动化覆盖

`test/test-strategy.md` 明确写出 H2 不覆盖：

- MySQL FULLTEXT。
- MySQL 方言。
- 触发器 `SIGNAL`。
- 真实 LLM 调用。
- Caddy 反代和 HTTPS。

这些差异在文档中已有识别，但目前主要依赖 staging 手工补测。

影响：

- FULLTEXT、触发器、迁移脚本这类生产关键能力可能在 CI 层面缺失保护。
- H2 通过不等于 MySQL 可用。

建议优先级：**P1**。

### R6. 前端没有自动化测试

`frontend/package.json` 只有 `dev`、`build`、`preview`、`lint`，未发现 Vitest、Jest、Cypress 或 Playwright 相关配置。当前前端包含登录、项目、知识库、聊天、Agent steps、通知、管理后台等页面和组件。

影响：

- 聊天 UI、权限路由、API 错误处理、表单提交流程缺少回归保护。
- 后端 API 变化时，前端问题可能要到手工验收阶段才暴露。

建议优先级：**P2**。

### R7. 平台与路径绑定较明显

`backend/src/main/resources/application.yml` 默认包含：

- `D:/archive/files`
- `D:/archive/parsed`
- `D:/archive/logs`
- `D:/archive/logs/backend.log`

配置文档和部署文档也围绕 Windows 单机展开。

影响：

- 当前生产环境是 Windows 单机，这个选择可以理解。
- 但对 Linux 开发环境、容器化、CI service container 和未来迁移不够友好。

建议优先级：**P2**。

### R8. 生产安全默认项需要收紧

后端安全基线具备 JWT、无状态 Session、RBAC 和方法级权限，但仍需注意：

- `SecurityConfig` 对 `/actuator/**` 直接 `permitAll`。
- CORS 允许 `https://*`，且允许 credentials。
- `application.yml` 默认 JWT secret 是开发默认值。

这些可能是开发便利配置，但生产需要确保由 `application-prod.yml`、反代或外置配置进行收敛。

建议优先级：**P1/P2**，取决于生产当前实际配置。

### R9. README 中规模描述可能过时

`README.md` 描述后端“~50 文件, 200+ 测试”，而当前盘点为：

- 后端 main Java 文件约 192 个。
- 后端 `@Test` 约 94 个。

影响：

- 不影响运行，但会影响接手者对项目规模和测试成熟度的判断。

建议优先级：**P3**。

---

## 5. 改进建议

### 5.1 P0：立即补齐基础质量门禁

1. 新增 CI 流水线，至少执行：
   - `backend`: `mvn test`
   - `qa-agent`: `pytest`
   - `frontend`: `npm ci && npm run build`
2. 将 `config/config.json` 加入 `.gitignore`。
3. CI 中检查敏感配置：
   - 禁止提交真实 `config.json`。
   - 禁止提交 `.env`、key、token、数据库密码。

### 5.2 P1：收敛核心业务风险

1. 关闭 `test-to-settle/round-2026-06-12-qa-regression.md` 中的 OPEN 问题。
2. 明确智能问答最终主路径：
   - 若 Python qa-agent 是长期方向，应减少 Java AgentEngine 的业务分叉。
   - 保留降级路径时，需要明确降级条件、日志、监控和用户可见提示。
3. 为 MySQL 特性增加专项自动化：
   - 使用 CI service container 或本地测试 profile 跑 MySQL 8。
   - 覆盖 FULLTEXT、触发器、迁移脚本、关键 SQL 白名单。
4. 收紧生产安全配置：
   - actuator 不应整体公开。
   - CORS 生产环境应指定域名。
   - JWT secret 必须只来自外置配置。

### 5.3 P2：增强可维护性和前端质量

1. 前端引入 Vitest，先覆盖：
   - API client 错误标准化。
   - auth store。
   - router 权限守卫。
   - ChatMessage / AgentStepsPanel 等关键组件。
2. 引入轻量 E2E，例如 Playwright：
   - 登录。
   - 项目列表。
   - 知识库问答 smoke。
3. 路径配置化：
   - 默认值尽量改为环境变量或相对路径。
   - 保留 Windows 生产默认，但避免开发/CI 被 `D:/` 绑定。

### 5.4 P3：文档同步和治理

1. 更新 README 中规模、测试数量和当前阶段描述。
2. 在 `docs/reviews/README.md` 或 `docs/README.md` 中补充本报告索引，避免后续评审文档分散。
3. 为“架构迁移状态”增加一个短页：
   - 当前主路径。
   - 兼容路径。
   - 废弃计划。
   - 验收条件。

---

## 6. 建议路线图

| 优先级 | 建议 | 预期收益 |
|---|---|---|
| P0 | CI 跑 backend、qa-agent、frontend build | 让每次提交具备最低自动质量门禁。 |
| P0 | `.gitignore` 增加 `config/config.json` | 降低真实密钥和密码误提交风险。 |
| P1 | 关闭 qa 回归 OPEN round | 保障核心问答体验稳定性。 |
| P1 | MySQL 8 专项测试 | 覆盖 H2 无法验证的生产关键能力。 |
| P1 | 收敛问答双栈/三路径 | 降低维护复杂度和行为不一致。 |
| P1 | 收紧 actuator/CORS/JWT 生产配置 | 降低生产暴露面。 |
| P2 | 前端 Vitest + 少量 E2E | 防止关键 UI 和权限流程回归。 |
| P2 | 路径配置化 | 改善 Linux/CI/容器化适配。 |
| P3 | README 与文档索引同步 | 降低接手成本。 |

---

## 7. 最终评价

本项目最大的优势是：**业务目标明确、文档体系完整、后端领域建模已经展开、智能问答方向有清晰演进记录。** 这说明项目不是简单 Demo，而是已经进入可持续迭代的工程形态。

当前最大的短板是：**自动化质量门禁不足、核心问答链路处在迁移中间态、生产关键的 MySQL/Caddy/真实 LLM 场景仍依赖手工补测。** 这些问题不会否定项目价值，但会影响稳定交付和多人协作效率。

建议下一阶段以“交付可靠性”为主线：先补 CI 和敏感配置防护，再关闭 qa 回归问题并补 MySQL 专项测试，随后逐步收敛 Agent 架构和补齐前端测试。完成这些后，项目的可维护性和上线信心会明显提升。

