# 投委会档案管理系统 — 完整架构方案

> 场景:Windows 内网服务器部署,同事通过浏览器访问,纯内网使用
> 范围:5 项核心功能(项目管理 / 项目-议案-材料三级档案 / 知识库问答 / 时点提取与日程 / 节点触发)
> 适用:3-5 人小团队,2.5-3.5 个月主体交付
> 阅读时间:全文约 25 分钟,执行摘要 3 分钟可读完

---

## 0. Executive Summary

### 核心选型(一句话)

| 维度 | 主选 | 一句话理由 |
|---|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus | 国内中后台生态成熟,招人成本低 |
| 后端 | Java 17 + Spring Boot 3.x | 金融/政企内网最常见,WinSW 服务化成熟 |
| 数据库 | PostgreSQL 16 | 开源、JSONB/全文检索/分区强,Windows 原生 |
| 文件存储 | MinIO(单机纠删码) | S3 协议,Windows 原生二进制,后续可扩多节点 |
| 文档解析 | Apache Tika 2.9+ | 一站式 PDF/Word/Excel/PPT,中文 PDF 走 Tesseract 桥接 |
| 向量库 | Qdrant 1.10+(Windows 原生二进制) | 单机部署简单,中文召回稳定 |
| 全文检索 | Elasticsearch 8.x 或 OpenSearch 2.x | 中文用 IK,推荐 OpenSearch 避免 SSPL 风险 |
| 任务调度 | XXL-JOB 2.4+ | 分布式调度中心,可视化 |
| 消息通知 | 企业微信(自建应用 + 群机器人)+ SMTP 邮件 | 实名可回执 + 审计归档 |
| OCR | PaddleOCR 2.7+ + PP-Structure | 中文 SOTA,Apache 2.0,表格抽取必备 |
| 大模型 | **本地 Ollama + Qwen2.5-14B-Instruct(默认完全内网)** | 数据 0 出网,合规最稳 |

### 关键决策

- **完全内网自洽**(默认):不依赖任何外网 API,合规零风险
- **双机主备(温备)+ Docker Compose + 共享存储**:简单可控,RTO 5-15 分钟
- **Rancher Desktop 替代 Docker Desktop**:规避商业授权风险(员工 >250 或年收入 >$10M 需付费)
- **规则引擎(5 号需求)独立成阶段(P5)**:1.5 人月,可视化编辑 + 事件订阅 + 试运行沙箱
- **不设专职算法岗**:P3/P4 预留 1 人月做 Prompt + 评估集专项
- **数据不出网**:云端 LLM/OCR 仅作 fallback(需脱敏网关),默认不启用

### 阶段概览(总 10-11 人月 / 2.5-3.5 月并行)

```
P0 启动(0.5 人月) → P1 基础平台(1.5) → P2 项目+档案(2.5) ─┬─→ P3 知识库(2.0) → P5 规则引擎(1.5)
                                                          ├─→ P4 时点(1.0) ─┘
                                                          └────────────────→ P6 灰度+运维(1.0)
```

### 待用户拍板的关键决策点(11 项)

详见 §11。默认假设:
1. 不允许云端 LLM fallback
2. 不采购商业云 OCR
3. 委员主要使用企业微信
4. 接受完全内网邮件中继
5. 允许白名单受控出网(仅补丁/病毒库)
6. LLM 模型 14B(单 GPU 24GB 可跑)
7. 接受 Rancher Desktop 替代 Docker Desktop
8. 评估集业务方投入 5-10 人天标注
9. 1 周历史档案盘点
10. 灰度 2 周 + 试运行 2-4 周
11. 规则配置员由业务方指派 1-2 人

---

## 1. 需求理解与目标

### 5 项核心功能

| # | 需求 | 关键能力 |
|---|---|---|
| 1 | **新建项目和项目管理** | 立项→审议→投后→退出全生命周期,项目经理负责制 |
| 2 | **项目-议案-议案材料三级档案** | 支持 doc/pdf/xlsx/会议纪要等多种文件,材料多版本管理 |
| 3 | **知识库与问答** | 扫描提取摘要,用户可在界面上提问并获得引用溯源 |
| 4 | **时点提取与日程管理** | 自动从合同/备忘录提取 + 手工设置,对外提醒(邮件/企微) |
| 5 | **节点触发(规则引擎)** | 预定义规则,事件触发自动生成任务(如"收款凭证入库 → 平往来款待办") |

### 非功能性需求

- **合规**:数据不出网,涉敏感材料不向量化,审计可追溯
- **可用性**:工作时段 8×5,SLA ≥ 99.5%,P0 故障 30 分钟内响应
- **性能**:P95 API 时延稳定在阈值,5000 份历史档案一次性建库 1-2 周
- **可扩展**:5 年内可容纳 100-300 万 chunk,500 用户

---

## 2. 系统架构总览

### 2.1 架构图

```mermaid
flowchart LR
    subgraph Client["客户端"]
        Browser[浏览器]
    end

    subgraph FE["前端层"]
        Web[Web 应用]
        Admin[管理后台]
    end

    subgraph BE["后端服务层"]
        Gateway[API 网关 / 鉴权]
        AppSvc[业务应用服务]
        RuleEng[规则引擎]
        Sched[任务调度]
        Notif[消息通知]
    end

    subgraph DataPlane["数据与能力平面"]
        DB[(主数据库)]
        FileStore[(文件存储)]
        DocParse[文档解析 / OCR]
        KB[知识库<br/>向量 + 全文]
        LLM[大模型]
    end

    Browser <-->|HTTPS| Web
    Browser <-->|HTTPS| Admin
    Web -->|REST/JSON| Gateway
    Admin -->|REST/JSON| Gateway
    Gateway --> AppSvc
    AppSvc --> DB
    AppSvc --> FileStore
    AppSvc -->|入队| DocParse
    AppSvc -->|入队| Sched
    AppSvc -->|写入| KB
    AppSvc -->|调用| LLM
    AppSvc -->|调用| RuleEng
    Sched -->|触发| AppSvc
    Sched -->|触发| Notif
    RuleEng -->|写任务| AppSvc
    DocParse -->|写结构化结果| AppSvc
    Notif -->|邮件/企微/钉钉/飞书| Browser
```

### 2.2 5 个核心模块

| # | 模块 | 职责 | 上游 | 下游 |
|---|---|---|---|---|
| M1 | **项目管理** | 维护"项目"主数据,贯穿立项→审议→投后全生命周期 | 用户、角色 | M2 议案、M4 时点、M5 任务 |
| M2 | **三级档案库** | 维护议案与材料的多版本结构化档案 | M1 项目、文档解析 | M3 知识库、M4 时点抽取、M5 规则触发 |
| M3 | **知识库问答** | 切片、向量化、检索、结合 LLM 回答 | M2 材料版本、LLM | 用户(直接)、M5 反馈回流 |
| M4 | **时点提取与日程** | 抽取关键时点,转日程并提醒 | M2 文档解析结果 | M5 规则、用户日程视图 |
| M5 | **节点触发(规则引擎)** | 监听事件,按规则生成任务、状态变更、通知 | M2、M4、用户操作 | 任务中心、通知、状态机 |

### 2.3 跨模块事件

- `material.uploaded` — 材料上传完成
- `material.parsed` — 文档解析完成
- `timepoint.extracted` — 时点抽取完成
- `timepoint.approaching` / `timepoint.overdue` — 时点临近/逾期
- `proposal.status_changed` — 议案状态变更
- `rule.fired` — 规则命中

### 2.4 文件入库数据流

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant FE as 前端
    participant API as 后端 API
    participant FS as 文件存储
    participant DP as 文档解析/OCR
    participant DB as 主数据库
    participant TP as 时点抽取(M4)
    participant KB as 知识库(M3)
    participant RE as 规则引擎(M5)
    participant SCH as 任务调度
    participant NF as 消息通知

    U->>FE: 上传材料
    FE->>API: 提交文件 + 元数据
    API->>FS: 落盘原始文件
    API->>DB: 写 MaterialVersion 记录
    API->>DP: 入队解析任务
    DP-->>API: 解析结果
    API->>DB: 更新 parse_status=成功
    API->>TP: 触发时点抽取
    TP-->>DB: 写入 TimePoint
    API->>KB: 切片 + 写入 KBChunk
    API->>RE: 发布 material.parsed 事件
    RE->>DB: 规则匹配,创建 Task
    SCH->>NF: 到点提醒
    NF-->>U: 邮件/企微
```

---

## 3. 数据模型(ER)

### 3.1 ER 图

```mermaid
erDiagram
    ROLE ||--o{ USER : "授予"
    USER ||--o{ PROJECT : "负责"
    USER ||--o{ QA_RECORD : "提问"
    USER ||--o{ TASK : "承办"
    USER ||--o{ RULE : "维护"
    PROJECT ||--o{ PROPOSAL : "包含"
    PROJECT ||--o{ TIMEPOINT : "关联"
    MEETING ||--o{ PROPOSAL : "审议"
    PROPOSAL ||--o{ PROPOSAL_MATERIAL : "挂载"
    PROPOSAL_MATERIAL ||--o{ MATERIAL_VERSION : "多版本"
    MATERIAL_VERSION ||--|| FILE_OBJECT : "对应实体文件"
    MATERIAL_VERSION ||--o{ KB_CHUNK : "切分为"
    MATERIAL_VERSION ||--o{ TIMEPOINT : "抽取自"
    RULE ||--o{ TASK : "触发产生"
    RULE }o--o{ TIMEPOINT : "监听"
    RULE }o--o{ MATERIAL_VERSION : "监听"
    TASK ||--o{ NOTIFICATION : "产生"
    TIMEPOINT ||--o{ NOTIFICATION : "产生"
    NOTIFICATION }o--|| USER : "收件人"

    PROJECT {
        string id PK
        string code UK
        string name
        string stage
        string status
        string owner_id FK
        decimal amount
        date started_at
        date deadline_at
    }
    PROPOSAL {
        string id PK
        string project_id FK
        string meeting_id FK
        string code UK
        string title
        string type
        string status
        text summary
        string decision
    }
    PROPOSAL_MATERIAL {
        string id PK
        string proposal_id FK
        string name
        string type
        boolean is_required
        int sort_order
    }
    MATERIAL_VERSION {
        string id PK
        string material_id FK
        int version_no
        string file_id FK
        string parse_status
        string ocr_status
        int page_count
        string hash
    }
    FILE_OBJECT {
        string id PK
        string storage_path
        string file_name
        string mime_type
        bigint size_bytes
        string hash
    }
    TASK {
        string id PK
        string code UK
        string name
        string type
        string status
        int priority
        string owner_id FK
        string related_project_id FK
        string related_proposal_id FK
        string related_rule_id FK
        json payload
        datetime due_at
    }
    TIMEPOINT {
        string id PK
        string project_id FK
        string proposal_id FK
        string material_version_id FK
        string name
        string type
        date due_at
        json reminder_days
        string status
        text source_text
        int source_page
        float confidence
        string extracted_by
    }
    KB_CHUNK {
        string id PK
        string source_type
        string source_id
        string material_version_id FK
        int chunk_index
        text content
        string content_hash
        string embedding_status
        string embedding_id
        json metadata
    }
    QA_RECORD {
        string id PK
        string session_id
        string user_id FK
        text question
        text rewritten_query
        json retrieved_chunk_ids
        text answer
        json citations
        int feedback_score
        int latency_ms
        string llm_model
    }
    RULE {
        string id PK
        string code UK
        string name
        text description
        string trigger_type
        json trigger_condition
        string action_type
        json action_payload
        boolean enabled
        int priority
        datetime effective_from
        datetime effective_to
    }
    NOTIFICATION {
        string id PK
        string channel
        string target_user_id FK
        string related_task_id FK
        string related_timepoint_id FK
        text content
        string status
        int retry_count
    }
    USER {
        string id PK
        string username UK
        string display_name
        string email
        string role_id FK
        string department
        string status
    }
    ROLE {
        string id PK
        string code UK
        string name
        json permissions
    }
    MEETING {
        string id PK
        string code UK
        string name
        datetime scheduled_at
        string location
        string status
        text agenda
    }
```

### 3.2 关键设计要点

| 实体 | 设计要点 |
|---|---|
| **Project** | 业务主轴;所有议案/材料/时点/任务挂在项目下 |
| **Proposal** | 投委会审议的最小单元;`status` 走状态机 |
| **ProposalMaterial + MaterialVersion** | 一份材料可能多次补件,**通过 `version_no` 区分**;`hash` 字段去重 |
| **Task** | 规则引擎产生或人工创建;`related_rule_id` 记录触发来源 |
| **TimePoint** | 系统的"闹钟";`confidence + extracted_by` 支持低置信度回退人工 |
| **KBChunk** | 知识库最小检索单元;`source_type + source_id` 指向原始档案;`content_hash` 去重 |
| **QARecord** | 每次问答必落库;支持审计与"答案有据可查" |
| **Rule** | `trigger_condition` 用 JSON/DSL 表达;`action_type` 支持组合动作;`enabled + effective_from/to` 支持灰度 |

### 3.3 跨实体一致性约束

- **MaterialVersion ↔ FileObject** 一一对应;`hash` 全局唯一(内容寻址)
- **TimePoint** 的产生来源必须能在 `material_version_id` / `proposal_id` / `project_id` 三角中回溯
- **Task** 关闭必须联动 **Notification** 清空
- **Rule** 修改不追溯影响已生成的 **Task**;新规则只作用于新事件

---

## 4. 技术选型与开源组件

### 4.1 技术选型表

| # | 组件 | 主选 | 备选 | 主选理由 |
|---|------|------|------|----------|
| 1 | 前端 | Vue 3 + Vite + TypeScript + Element Plus | React 18 + Antd Pro | 国内生态成熟,Element Plus 表格/表单/上传组件完备 |
| 2 | 后端 | Java 17 + Spring Boot 3.x | Python 3.11 + FastAPI | 金融/政企内网最常见,WinSW 服务化成熟,生态库多 |
| 3 | 数据库 | PostgreSQL 16 | SQL Server 2019/2022 | 开源、JSONB/全文检索/分区强,Windows 原生 |
| 4 | 文件存储 | MinIO(RELEASE.2024+) | Windows SMB + DB 存路径 | S3 协议,Windows 原生二进制,后续扩多节点平滑 |
| 5 | 文档解析 | Apache Tika 2.9+ | Unstructured.io + Pandoc | 一站式 PDF/Word/Excel/PPT,中文 PDF 走 Tesseract 桥接 |
| 6 | 向量库 | Qdrant 1.10+(Windows 原生) | Milvus 2.4+(Docker Compose) | 单机部署 1 进程,中文召回稳定,运维简单 |
| 7 | 全文检索 | Elasticsearch 8.x / OpenSearch 2.x | PG tsvector + pg_trgm | 中文用 IK/拼音/同义词;**推荐 OpenSearch 避免 SSPL** |
| 8 | 任务调度 | XXL-JOB 2.4+ | Spring @Scheduled + Quartz | 分布式调度中心,Web UI,失败重试,邮件告警 |
| 9 | 消息通知 | 企业微信(自建应用+群机器人)+ SMTP 邮件 | 钉钉/飞书 | 实名可读回执 + 审计归档 |
| 10 | OCR | PaddleOCR 2.7+ + PP-Structure | Tesseract 5.x | 中文 SOTA,表格/版面/印章抽取,Apache 2.0 |
| 11 | 大模型 | **本地 Ollama + Qwen2.5-14B-Instruct** | vLLM + Qwen2.5-14B | 完全不出网,合规最稳,16GB GPU 可跑 |
| 配套 | Embedding | BAAI/bge-m3 | BAAI/bge-large-zh-v1.5 | 中文 SOTA,完全开源 |
| 配套 | 规则引擎 | Drools 8.x | Aviator 5.x | 表达力强,审计/版本管理友好 |

### 4.2 选型逻辑一致性(交叉约束)

- **Java 主线**:Spring Boot ↔ Tika ↔ XXL-JOB ↔ Drools ↔ Spring Mail,全部同语言,JAR 单一进程
- **存储分层**:PG 存元数据 + 关系;MinIO 存文件;Qdrant 存向量;ES 存索引;职责清晰
- **AI 链路**:`Ollama/Qwen` 生成 ↔ `bge-m3` Embedding ↔ `Qdrant` 召回 ↔ `PG/ES` 过滤,完全内网闭环
- **退路**:每类都有"更轻"或"更重"备选,允许按数据量和团队规模 6 个月内不重构替换

### 4.3 开源组件清单(40 项)

| # | 名称 | 用途 | 许可证 | 推荐版本 | 内网 |
|---|------|------|--------|----------|------|
| 1 | Vue | 前端 | MIT | 3.4.x | ✓ |
| 2 | Vite | 前端构建 | MIT | 5.x | ✓ |
| 3 | TypeScript | 前端语言 | Apache 2.0 | 5.4+ | ✓ |
| 4 | Element Plus | UI 组件库 | MIT | 2.7+ | ✓ |
| 5 | Spring Boot | 后端框架 | Apache 2.0 | 3.3.x | ✓ |
| 6 | OpenJDK | 运行时 | GPLv2+CE | 21 LTS | ✓ |
| 7 | Spring Data JPA / MyBatis-Plus | ORM | Apache 2.0 | 3.x / 3.5+ | ✓ |
| 8 | Spring Security + JJWT | 认证/鉴权 | Apache 2.0 / MIT | 6.x / 0.12+ | ✓ |
| 9 | Spring AI | LLM 集成 | Apache 2.0 | 1.0+ | ✓ |
| 10 | Apache Tika | 文档解析 | Apache 2.0 | 2.9.x | ✓ |
| 11 | PDFBox / POI | PDF/Office 底层 | Apache 2.0 | 3.0 / 5.2+ | ✓ |
| 12 | WinSW | Windows 服务化 | MIT | 3.0 | ✓ |
| 13 | PostgreSQL | 主数据库 | PostgreSQL License | 16.x | ✓ |
| 14 | pg_trgm / zhparser | 模糊/中文分词 | PostgreSQL / MIT | 同 PG | ✓ |
| 15 | MinIO | 对象存储 | AGPLv3 ⚠ | 2024-05+ | ✓(自用) |
| 16 | Qdrant | 向量库 | Apache 2.0 | 1.10+ | ✓ |
| 17 | Elasticsearch | 全文检索 | Elastic v2 / SSPL ⚠ | 8.13+ | 自用 OK |
| 18 | **OpenSearch**(推荐替代 ES) | 全文检索 | Apache 2.0 | 2.x | ✓ |
| 19 | IK Analyzer | ES/OpenSearch 中文分词 | Apache 2.0 | 8.x | ✓ |
| 20 | XXL-JOB | 分布式调度 | GPLv3 ⚠(运行 OK) | 2.4+ | ✓ |
| 21 | Drools | 规则引擎 | Apache 2.0 | 8.x | ✓ |
| 22 | Aviator | 轻量规则 | MIT | 5.x | ✓ |
| 23 | Spring Mail | 邮件 | Apache 2.0 | 3.x | ✓ |
| 24 | PaddleOCR | OCR(主) | Apache 2.0 | 2.7+ | ✓ |
| 25 | Tesseract | OCR(备) | Apache 2.0 | 5.3+ | ✓ |
| 26 | Ollama | 本地 LLM 推理 | MIT | 0.3+ | ✓ |
| 27 | Qwen2.5 | 大语言模型 | Apache 2.0 | 14B-Instruct | ✓ |
| 28 | BAAI/bge-m3 | 中文 Embedding | MIT | 1.0+ | ✓ |
| 29 | LangChain4j | LLM 编排(可选) | Apache 2.0 | 0.36+ | ✓ |
| 30 | Logback | 结构化日志 | EPL 1.0 | 1.5+ | ✓ |
| 31 | Micrometer + Prometheus | 指标采集 | Apache 2.0 | 1.13+ | ✓ |
| 32 | Grafana | 监控可视化 | AGPLv3 ⚠(自用) | 11.x | ✓ |
| 33 | Redis / Memurai | 缓存/会话 | RSAL/BSD | 7.2.x / 5.x | ✓ |
| 34 | Caddy | 反向代理 | Apache 2.0 | 2.7+ | ✓ |
| 35 | Nacos | 配置/注册中心 | Apache 2.0 | 2.4+ | ✓ |
| 36 | 7-Zip | 备份压缩 | LGPL | 24.x | ✓ |
| 37 | Rancher Desktop | 容器运行时 | Apache 2.0 | 2.x | ✓(替代 Docker Desktop) |
| 38 | PostgreSQL 流复制 | 主备 | PostgreSQL | 16.x | ✓ |
| 39 | pg_basebackup | 备份 | PostgreSQL | 16.x | ✓ |
| 40 | windows_exporter | Windows 监控 | MIT | 0.24+ | ✓ |

### 4.4 许可证合规速查

- **AGPL(MinIO / Grafana)**:仅内网"组织内部使用"无需开源,严禁对外提供托管/转售
- **SSPL(Elasticsearch 8.x)**:不允许"托管服务"形式对外;**自用合规,但推荐直接换 OpenSearch**
- **GPL(XXL-JOB)**:仅运行不修改不二次分发即可
- **Redis 7.4+**:已转 RSAL/SSPL,**内网自用需锁 7.2.x 或换 Memurai 5.x(BSD)**
- **其他 Apache 2.0 / MIT / BSD / EPL**:商用无忧

---

## 5. 外部资源规划

### 5.1 一句话结论

**默认推荐"完全内网自洽"**:LLM 用本地 Ollama + Qwen2.5-14B,OCR 用 PaddleOCR + PP-Structure,消息用企业微信 + SMTP。在合规允许时再补"半内网"通道(LLM 走云端,文档先脱敏)。

### 5.2 大模型方案

| 路线 | 数据出网 | 一次性成本 | 推理性能 | 适用 |
|---|---|---|---|---|
| **A. 本地 Ollama + Qwen2.5-14B**(主推) | **完全不出** | GPU ¥30k-80k | 60+ token/s(GPU) | **长期主用** |
| B. 云端 API(OpenAI/Qwen/DeepSeek) | 文档/问答上行 | 0 | 100ms-2s | 备选(需合规) |
| C. 不接 LLM(BM25 + 规则) | 完全不出 | 0 | 毫秒级 | 兜底,模板化问答 |

**推荐节奏**:
- PoC(1-2 月):C 为主,A 留接口
- 上线(3-6 月):**A(Ollama + Qwen2.5-14B,单 GPU)**
- 长期(12+ 月):A 为主,B 作 fallback(走脱敏网关)

### 5.3 OCR 方案

| 方案 | 中文 | 表格 | 印章 | 离线 | 单页耗时 |
|---|---|---|---|---|---|
| Tesseract 5 | ★★★ | ✗ | ★ | ✓ | 0.3-0.8s |
| **PaddleOCR + PP-Structure**(主推) | ★★★★★ | ★★★★ | ★★★ | ✓ | 0.4-1.2s(GPU 0.1s) |
| RapidOCR(蒸馏) | ★★★★ | ★★★ | ★★ | ✓ | 0.2-0.5s |
| 商业云 OCR | ★★★★★ | ★★★★★ | ★★★★ | ✗ | 1-3s |

**推荐**:**PaddleOCR + PP-Structurev2**(投委会**表格抽取**是刚需)

### 5.4 消息通道

| 场景 | 通道 | 理由 |
|---|---|---|
| 委员个人即时通知(时点 T-0) | **企业微信自建应用(工作通知)** | 实名、可读回执、到达率高 |
| 项目群通报(新材料上传) | **企业微信群机器人** | 群内透明 |
| 正式议案通知(需归档) | **SMTP 邮件** | 邮件本身就是档案,审计最完整 |
| 兜底(企业微信未读) | **短信网关** | 仅最关键时点 |

### 5.5 内外网隔离决策表(默认 L1 完全隔离)

| 资源 | L1 默认 | L2 受控 | L3 半内网 |
|---|---|---|---|
| 大模型推理 | **本地 Ollama** | 同左 | + 云端 fallback |
| OCR 引擎 | **PaddleOCR** | 同左 | 同左 |
| 邮件 SMTP | **内网邮件中继** | 同左 | 同左 |
| 企业微信 | 自建应用 + 群机器人 | 同左 | 同左 |
| 云对象存储 | 不接受 | 经审批 | 走专线 |
| 商业 BI | 不接受 | 脱敏导出 | 同左 |

### 5.6 5 条决策原则

1. **"涉敏感即不出"**:投委会材料含未公开经营数据,任何涉敏资源默认走内网
2. **"能开源就不商业"**:PaddleOCR、Qwen2.5、bge、Ollama 全部 Apache/MIT
3. **"能规则就不 LLM"**:节点触发、时点抽取优先规则引擎;LLM 只用于"问答"等语义检索
4. **"可审计 > 可用性"**:邮件归档 > 即时推送;工作通知带回执 > 群机器人
5. **"出网走脱敏网关"**:即便未来允许云端 LLM/OCR,文档必须先经脱敏网关

---

## 6. 模块开发安排与里程碑

### 6.1 阶段划分(总 10-11 人月 / 2.5-3.5 月并行)

| 阶段 | 名称 | 目标 | 关键产出 | DoD | 人月 |
|---|---|---|---|---|---|
| **P0** | 启动与需求细化 | 锁需求、拆任务 | PRD、原型、接口契约、CI/CD 脚手架 | 客户签字 + 契约冻结 | 0.5 |
| **P1** | 基础平台 | 搭骨架 | 用户/角色/权限、文件存储、DB schema、CI/CD、监控 | 4 基础模块集成测试通过 | 1.5 |
| **P2** | 项目管理 + 三级档案(需求 1+2) | 实现 #1 #2 | CRUD + 版本树 + 解析入库 + 全文索引 | E2E 100% 通过 | 2.5 |
| **P3** | 知识库问答(需求 3) | 切片 + 检索 + LLM 回答 | 知识库后台、问答 UI、评估报告 | Recall@5 ≥ 90%,准确率 ≥ 85% | 2.0 |
| **P4** | 时点提取与日程(需求 4) | 自动 + 手工 + 提醒 | 时点抽取服务、日程视图、通知对接 | F1 ≥ 0.85,提醒到达率 ≥ 99% | 1.0 |
| **P5** | 节点触发(需求 5,**独立成阶段**) | 规则引擎 | 可视化编辑、事件订阅、规则执行器、试运行沙箱 | 5 类典型规则可演示,误触发 < 2% | 1.5 |
| **P6** | 试运行 + 运维 | 灰度、培训、SLA | 灰度报告、培训材料、Runbook | 灰度 2 周零 P0,SLA 4 周达标 | 1.0 |

### 6.2 阶段依赖

```
P0 → P1 → P2 ─┬─→ P3 → P5
              ├─→ P4 ─┘
              └──────────→ P6(灰度)
```

- **P2 必须先于 P3/P4**:知识库与时点都依赖"材料已解析入库"
- **P5 独立但需 P3/P4 数据**:规则引擎在 P3、P4 之后做联调

### 6.3 关键里程碑

| 里程碑 | 时间点 | 标志事件 |
|---|---|---|
| M1 — 骨架就绪 | +0.5 月末 | P1 退出 |
| M2 — 档案能查 | +1.5 月末 | P2 退出 |
| M3 — 能问能提 | +2.5 月末 | P3+P4 退出 |
| M4 — 规则能跑 | +3.0 月末 | P5 退出 |
| M5 — 试运行上线 | +3.5 月末 | P6 退出 |

### 6.4 人员配置(4 人稳态团队)

| 角色 | 人数 | 主要职责 |
|---|---|---|
| PM / 产品 | 1(可由后端兼任 P0-P1) | 需求、优先级、业务对齐、灰度协调 |
| 后端开发 | 1.5-2 | 框架、API、解析流水线、规则引擎、检索 |
| 前端开发 | 1 | 管理端 UI、问答 UI、规则编辑器 |
| 测试 | 0.5(可与开发互转) | 用例、自动化、评估集标注、灰度跟踪 |
| 运维 | 0.5(与后端合并) | CI/CD、备份、监控、值班 |

> **不设专职算法岗**;P3、P4 阶段各预留 0.5 人月做"Prompt + 评估集"专项,若选 B 路径(本地 LLM)再外聘兼职顾问。

---

## 7. 测试与数据迁移

### 7.1 测试金字塔

| 层级 | 工具 | 覆盖率目标 | 责任方 |
|---|---|---|---|
| 单元 | pytest/Jest | 核心行覆盖 ≥ 70%,规则/解析器 ≥ 85% | 后端 |
| 集成 | Testcontainers + Pact | 每个外部依赖有 1 套契约 | 后端 |
| E2E | Playwright | 关键用户旅程 100% 自动化 | 前端+测试 |
| 非功能 | k6/Locust + OWASP ZAP | P95 时延、安全回归 | 测试+后端 |

### 7.2 文档解析质量指标(模块 1+2)

| 指标 | 目标值 |
|---|---|
| PDF 文本抽取字符准确率 | ≥ 99% |
| OCR 字符准确率(印刷体/手写) | ≥ 97% / ≥ 92% |
| 表格识别结构准确率 | ≥ 90%(单元格)/ ≥ 85%(合并/跨页) |
| 字段抽取(项目名/议案号/金额/日期) | ≥ 95% |
| 解析失败率 | ≤ 1% |
| 解析时延 | P95 ≤ 30s/份(纯文本)/ ≤ 90s/份(扫描+OCR) |

### 7.3 知识库问答评估指标(模块 3)

评估集:**300-500 条 QA**,覆盖高频/边缘/对抗(故意答不出来)。

| 类别 | 指标 | 目标值 |
|---|---|---|
| 检索 | Top-5 召回率(Recall@5) | ≥ 90% |
| | MRR | ≥ 0.75 |
| | 拒答率(无相关结果应拒答) | ≥ 95% |
| 回答 | 人工评估准确率 | ≥ 85% |
| | 引用片段正确率 | ≥ 90% |
| | 幻觉率(无依据编造) | ≤ 5% |
| 性能 | P95 端到端时延 | ≤ 5s(含 LLM) |

### 7.4 数据迁移(假设 5000 份材料)

| 步骤 | 单页耗时 | 3 worker 并发 |
|---|---|---|
| 可复制 PDF 抽取 | 1s | ~1.8 小时 |
| 扫描件 OCR | 6s | ~5.3 小时 |
| Office 解析 | 0.5s | ~0.2 小时 |
| 切片 + 嵌入 | 0.5s/页 | ~1.5 小时 |
| 时点抽取(LLM) | 3s/份 | ~1.4 小时 |
| **合计(纯计算)** | | **约 10-12 小时** |
| + 人工审核/重试 buffer | | **2-3 天** |
| **建议预留 1-2 周**(含盘点) | | |

**冷启动应对**:知识库为空时降级为"全文检索 + 提示'知识库建设中'";LLM 直接基于"项目元数据 + 已上传材料"做回答(无需向量库)。

---

## 8. Windows Server 部署方案

### 8.1 服务器规格(中型场景,推荐)

| 角色 | 配置 | 数量 |
|---|---|---|
| 应用主(AP1) | 2× Xeon Silver 4310 / 64 GB / 2× 480 GB SSD(系统+DB)/ 4 TB HDD RAID10 / 2× 10 GbE | 1 |
| 应用备(AP2) | 同上(可略低) | 1 |
| AI/GPU 节点(可选) | RTX 4090 / 24GB / 8C / 64GB / 1TB NVMe | 1(本地 LLM 时) |
| 共享存储 | NAS(iSCSI/SMB)4-8 TB RAID6 | 1 |
| 跳板机 | 2C/8G/256G(AD/DNS/监控/备份) | 1 |

**操作系统**:Windows Server 2022 Standard

### 8.2 部署架构(推荐:双机主备 + Docker Compose + 共享存储)

> **RTO 5-15 分钟,RPO ≈ 0(同步流复制)或 ≤ 30 秒(异步)**

```
内网办公网段(192.168.10.0/24)
  → VIP(192.168.10.100)
    → Caddy(主 AP1 / 备 AP2)
      → AP1(主):Postgres primary / Redis master / MinIO / Qdrant / 后端 / 前端
      → AP2(温备):Postgres standby(流复制) / Redis replica / MinIO / Qdrant / 后端镜像同版本
  → NAS(iSCSI/SMB)共享存储
  → 备份服务器 → 异地/蓝光
```

### 8.3 Windows 上跑 Linux 容器方案对比

| 方案 | 授权 | 推荐度 |
|---|---|---|
| Docker Desktop for Windows | ⚠ 员工 >250 需付费 | ⭐⭐(合规允许时) |
| WSL2 + 原生 Docker Engine | ✅ 免费 | ⭐⭐⭐(过渡方案) |
| **Rancher Desktop 2.x + WSL2 + containerd** | ✅ **免费开源** | ⭐⭐⭐⭐(**最推荐**) |
| Hyper-V + Linux VM | ✅ 免费 | ⭐⭐(WSL2 不稳时) |
| Windows 原生容器 | 不能跑 PG/MinIO/Qdrant | ✗ |

**强烈推荐 Rancher Desktop**:
- Apache 2.0 全开源,无 Docker Desktop 商业授权风险(2022/8 起员工 >250 或年收入 >$10M 需付费订阅)
- containerd 与 K8s 同源,未来上 K8s 无需切换运行时
- 镜像、Compose 文件与 Docker 兼容

### 8.4 端口规划(全部内网,严禁暴露公网)

| 端口 | 服务 | 暴露范围 |
|---|---|---|
| 80, 443 | Caddy HTTP/HTTPS | **唯一对外入口**(经 VIP) |
| 9100 | windows_exporter | Prometheus 拉取 |
| 22, 3389 | SSH/RDP | 限堡垒机 + MFA |
| 5432, 6379, 9000/9001, 6333/6334, 9200, 8000, 3000 | PG/Redis/MinIO/Qdrant/ES/后端/前端 | **只对内部 Docker 网络开放** |

### 8.5 HTTPS 与反向代理

- **推荐 Caddy 2.7+**:配置极简,可绑定私有 CA
- **证书**:内网 AD CS 签发 + 组策略下发,或 mkcert 本地签发 + 客户端导入根证书
- **TLS**:仅 1.2/1.3,禁用弱 cipher

### 8.6 备份策略(3-2-1 原则)

| 对象 | 工具 | 频率 | 保留 | RPO | RTO |
|---|---|---|---|---|---|
| PostgreSQL 全量 | pg_basebackup | 每日 02:00 | 30 天 | ≤ 24h | 1-2h |
| PostgreSQL WAL | 流复制 + 归档 | 持续 | 7 天 | ≤ 5min | — |
| 文件(MinIO) | mc mirror / restic | 每日 03:00 | 30 天 + 月度 12 月 | ≤ 24h | 2-4h |
| 向量库(Qdrant) | snapshot API | 每日 04:00 | 14 天 | ≤ 24h | 1h |
| Windows Server 系统状态 | Windows Server Backup | 每周 | 4 周 | — | 4h |

**异地**:每周一次蓝光寄送或加密上传至总部异地机房。

**演练**:每月 1 日做"恢复演练",在隔离的 AP2 上恢复最近一次全量 + WAL,核对 5 条样本数据。

---

## 9. 运维、安全、监控

### 9.1 网络分段

- 办公网段(192.168.10.0/24)→ 仅可访问 VIP 443
- 应用网段(192.168.20.0/24)→ AP1/AP2 互访,DB/向量端口
- 运维网段(192.168.30.0/24)→ 跳板、监控、备份服务器
- 三段间用 Windows Defender Firewall / 硬件防火墙 ACL 隔离

### 9.2 最小权限

- 后端服务账号:`svc-app`,**非管理员**
- 数据库账号分离:`app_readwrite` / `app_readonly` / `backup_operator`
- 容器以非 root 用户运行

### 9.3 防病毒

- **Windows Defender for Endpoint**(或企业 EDR)常驻,定义每周自动更新
- 白名单:`D:\containers\data\*`、`D:\files\*` 加入排除项
- 上传文件:前端扩展名 + MIME 白名单 + 后端 ClamAV 二次扫描

### 9.4 Windows 更新

- WSUS 统一由运维审批
- 更新窗口:每月第 2 个周六 02:00-06:00
- 关键补丁(RCE 类)48 小时内应用
- 内核更新需重启 → 备机先行,切流量,再主机

### 9.5 密码策略(域控 GPO)

- 长度 ≥ 12 字符,含大小写+数字+符号
- 90 天强制更换,新密码不可与最近 12 次重复
- 5 次失败锁定 15 分钟
- 管理员账号启用 **MFA**(微软 Authenticator / 硬件令牌)

### 9.6 审计日志

- GPO:账户登录、对象访问、特权使用、策略更改、系统事件 → Windows Event Log Security
- 转发至 WEF 收集器,或经 promtail/Winlogbeat → Loki/Elasticsearch
- 保留:在线 90 天,离线 1 年
- 后端应用日志结构化 JSON,关键事件带 actor/action/resource/result
- 关键告警:4624/4625 登录、4672 特权登录、4688 进程创建、7045 服务安装、**1102 日志被清除(最高优先级)**

### 9.7 监控告警

| 层 | 工具 | 采集 | 告警 |
|---|---|---|---|
| 基础设施 | Prometheus + windows_exporter | CPU/内存/磁盘/网络 | 邮件/企微 |
| 应用 | cAdvisor + /metrics | 容器 QPS/延迟/错误率 | 同上 |
| 数据库 | postgres_exporter | 连接数/慢查询/复制延迟 | 同上 |
| 业务 | 自定义埋点 | 上传/解析/问答/规则触发 | 同上 |

**关键阈值**(初始基线,1 个月后微调):

| 指标 | 阈值 | 级别 |
|---|---|---|
| Postgres 主备切换 | 主不可达 1min | P0(电话) |
| 备份失败 | 任意 | P1 |
| 证书到期 | < 7 天 | P0 |
| 磁盘已用 | > 90% | P1 |
| Postgres 复制延迟 | > 60s | P2 |
| 后端 5xx 比例 | > 1% | P2 |
| 解析任务积压 | > 500/30min | P2 |

---

## 10. 升级、回滚、上线策略

### 10.1 升级流程(零停机)

```
1. 备份(pg_basebackup + MinIO mirror + Qdrant snapshot)
2. 拉取新镜像 → 备机先起,跑健康检查
3. 切 VIP(AP1→AP2)
4. AP1 停服,拉新镜像,跑迁移(Flyway/Liquibase)
5. 健康检查通过 → 切回 VIP(AP2→AP1)
6. AP2 拉新镜像,全量升到 N
7. 观察 30 min,无异常 → 维护结束
```

### 10.2 数据库迁移(expand-contract)

- **Expand**:加列(可空)或加表,新旧版本都正常
- **Migrate**:数据回填(分批)
- **Contract**:删列 / 改约束(仅下一个大版本做)
- 每次 schema 变更用 Flyway / Liquibase 维护

### 10.3 镜像治理

- 内网搭 **Harbor**,所有镜像只允许从内网 registry 拉取
- 每次升级保留 N-1 / N-2 镜像 tag,回滚可恢复

### 10.4 灰度发布(2-4 周)

| 阶段 | 范围 | 周期 | 通过条件 |
|---|---|---|---|
| 内测 | 项目组 + IT | 1 周 | P0/P1 故障闭环 |
| 小范围灰度 | 1-2 部门 / 投委会秘书 | 2 周 | 5 工作日零 P0 故障 |
| 扩大灰度 | 50% 用户 | 2 周 | SLA 全部达标 |
| 全量 | 全公司 | 持续 | 进入运维期 |

### 10.5 培训(上线前 2 周完成)

- 普通用户(委员/经办/归档员):操作视频 + 现场培训 + 操作卡
- 业务管理员(投委会秘书):半脱产 0.5 天
- 系统管理员(IT 运维):1 天(部署/备份/监控/规则)
- 规则配置员(业务方 1-2 人):半天规则编辑器培训

### 10.6 SLA 初版(可在 P6 协商)

- 工作时段 8×5 可用性 ≥ 99.5%
- P0 ≤ 30min 响应,P1 ≤ 4h,P2 ≤ 1 工作日
- RPO ≤ 24h,RTO ≤ 4h

---

## 11. 风险清单

| 类别 | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| **合规** | 出网导致数据泄露 | — | 极高 | 默认 L1 完全隔离;任何出网必经脱敏网关 |
| **合规** | AGPL 组件(MinIO/Grafana)对外提供服务 | 低 | 高 | 仅内网"组织内部使用",严禁对外 |
| **合规** | 涉密项目入知识库 | 中 | 高 | 标记"不入知识库"白名单,只做档案 |
| **合规** | 商业 ES 的 SSPL 风险 | 中 | 中 | 默认用 OpenSearch 替代 |
| **技术** | 历史档案扫描件多、OCR 准确率低 | 高 | 中 | P0 阶段启动盘点;扫描件 >50% 触发方案重审 |
| **技术** | 中文 PDF 解析走 OCR(乱码) | 高 | 中 | Tika 默认走文本层;OCR 仅作 fallback |
| **技术** | Docker Desktop 商业授权被 IT 拦 | 中 | 中 | 默认 Rancher Desktop 规避 |
| **技术** | zhparser 需自编译(Windows MSVC 工具链) | 中 | 中 | 换 scws + zhparser 预编译包,或 PG simple + ik 前置 |
| **技术** | Ollama Windows 预览版稳定性 | 中 | 中 | 改用 WSL2 + Ollama Linux 版(更稳) |
| **技术** | 向量库内存膨胀(千万级) | 低 | 中 | Qdrant 调 HNSW 参数 + 量化;超阈值迁 Milvus |
| **性能** | LLM 推理吞吐低(问答并发排队) | 中 | 中 | 简单问题走"规则+全文"快速通道;LLM 只做重排序 + 生成 |
| **性能** | OCR + Embedding + LLM 共用一张卡 | 中 | 中 | 物理分卡:OCR 一张、LLM 一张 |
| **进度** | 规则引擎需求膨胀(可视化变复杂) | 中 | 高 | P5 拆 2 子迭代:M5.1 表达式版,M5.2 拖拽版;灰度期只承诺 M5.1 |
| **进度** | 业务方对"问答准确率"预期过高 | 中 | 中 | 评估集 + 期望管理;允许"拒答/转人工" |
| **进度** | 评估集标注拖延 | 高 | 中 | 业务方 P3 启动前 2 周开始标注;最小 100 条即可启动 |
| **进度** | 3-5 人团队并行多阶段超载 | 中 | 中 | 严格按里程碑;P5/P6 重叠期允许运维+测试合并 |
| **运维** | DB 单实例故障 | 中 | 极高 | 主备流复制 + 每日全量 + 30min 增量;季度恢复演练 |
| **运维** | MinIO 单机 IOPS 瓶颈 | 中 | 中 | RAID10 + 4 盘起步;后续多节点 |
| **运维** | WinSW 服务化后路径/字符集乱码 | 高 | 中 | JVM 加 `-Dfile.encoding=UTF-8` + `-Dconsole.encoding=UTF-8` |
| **运维** | Defender 误杀容器二进制 | 中 | 中 | 数据/可执行目录加排除项 |
| **运维** | 备份脚本失败无人发现 | 中 | 高 | 备份带日志写库 + 失败邮件;监控盯"过去 24h 无新备份" |

---

## 12. 待用户确认的关键决策点(11 项)

| # | 决策点 | 默认假设 | 备选 | 影响范围 |
|---|---|---|---|---|
| 1 | 是否允许云端 LLM 作为 fallback? | **否** | 是(走脱敏网关) | GPU 采购、运维成本 |
| 2 | 是否采购商业云 OCR 兜底? | **否** | 是(单据类) | PaddleOCR 调优工作量 |
| 3 | 委员主要使用哪款 IM? | **企业微信** | 钉钉 / 飞书 / 全做 | 消息通道主选 |
| 4 | 是否接受"完全内网邮件中继"? | **是** | 申请公网域名 | IT 基础设施 |
| 5 | 是否允许受控出网(白名单)用于补丁/病毒库? | **是(限白名单)** | 完全离线 | 运维复杂度 |
| 6 | LLM 模型规模? | **14B-Instruct** | 7B(省钱)/ 32B(更准) | GPU 选型 |
| 7 | 接受 Rancher Desktop 替代 Docker Desktop? | **是(强烈建议)** | 申请 Docker Desktop 商业授权 | 部署基础 |
| 8 | 评估集建设:业务方投入 5-10 人天标注 300-500 条 QA? | **是** | 缩减到 100 条启动 | P3 效果与上线质量 |
| 9 | 1 周历史档案盘点(文件服务器清单 + 命名规范)? | **是** | 启动后再补 | 冷启动质量与时长 |
| 10 | 灰度范围与时长(1-2 部门、2 周 + 2-4 周试运行)? | **是** | 缩短/延长 | 上线风险 |
| 11 | 规则配置员由业务方指派 1-2 人,权限如何隔离? | **业务方指派** | IT 兼任 | 规则可维护性 |

---

## 13. 附录

### 13.1 关键命令片段速查

#### Rancher Desktop 安装(PowerShell 管理员)

```powershell
dism.exe /online /enable-feature /featurename:Microsoft-Hyper-V-All /all /norestart
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
wsl --set-default-version 2
# 安装 RancherDesktop-2.x.x-windows.msi(选 containerd + 禁 k3s)
nerdctl --version
nerdctl compose version
```

#### docker-compose.yml(关键片段)

```yaml
networks:
  backend: { driver: bridge }
  monitoring: { driver: bridge }

volumes:
  pgdata:    { driver: local, driver_opts: { type: none, o: bind, device: D:/containers/data/postgres } }
  pgwal:     { driver: local, driver_opts: { type: none, o: bind, device: D:/containers/data/postgres-wal } }
  redisdata: { driver: local, driver_opts: { type: none, o: bind, device: D:/containers/data/redis } }
  miniodata: { driver: local, driver_opts: { type: none, o: bind, device: D:/containers/data/minio } }
  qdrantdata:{ driver: local, driver_opts: { type: none, o: bind, device: D:/containers/data/qdrant } }
  esdata:    { driver: local, driver_opts: { type: none, o: bind, device: D:/containers/data/elasticsearch } }
  filesroot: { driver: local, driver_opts: { type: none, o: bind, device: D:/files } }

services:
  postgres:
    image: postgres:16.3-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: archive
      POSTGRES_USER: ${PG_USER}
      POSTGRES_PASSWORD: ${PG_PASSWORD}
    command:
      - "postgres" -c "wal_level=replica" -c "max_wal_senders=5"
      -c "wal_keep_size=2GB" -c "archive_mode=on"
      -c "archive_command=copy \"%p\" \"D:/containers/data/postgres-wal/%f\""
    volumes: [pgdata:/var/lib/postgresql/data, pgwal:/var/lib/postgresql/wal]
    networks: [backend, monitoring]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${PG_USER} -d archive"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7.2-alpine
    restart: unless-stopped
    command: ["redis-server", "--appendonly", "yes", "--maxmemory", "2gb", "--maxmemory-policy", "allkeys-lru"]
    volumes: [redisdata:/data]
    networks: [backend, monitoring]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s

  minio:
    image: minio/minio:RELEASE.2024-05-10T01-41-38Z
    restart: unless-stopped
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    volumes: [miniodata:/data]
    networks: [backend, monitoring]

  qdrant:
    image: qdrant/qdrant:v1.7.4
    restart: unless-stopped
    volumes: [qdrantdata:/qdrant/storage]
    networks: [backend, monitoring]
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:6333/healthz"]

  backend:
    image: harbor.internal/archive/backend:${APP_VERSION}
    restart: unless-stopped
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
      minio:    { condition: service_healthy }
      qdrant:   { condition: service_healthy }
    environment:
      DATABASE_URL: postgresql+psycopg://${PG_USER}:${PG_PASSWORD}@postgres:5432/archive
      REDIS_URL: redis://redis:6379/0
      S3_ENDPOINT: http://minio:9000
      QDRANT_URL: http://qdrant:6333
    volumes: [filesroot:/mnt/files]
    networks: [backend, monitoring]

  frontend:
    image: harbor.internal/archive/frontend:${APP_VERSION}
    restart: unless-stopped
    networks: [backend, monitoring]

  caddy:
    image: caddy:2.7-alpine
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./caddy_data:/data
      - ./caddy_config:/config
    depends_on: [backend, frontend]
    networks: [backend, monitoring]
```

#### Caddyfile(内网 HTTPS)

```caddyfile
{
    admin localhost:2019
    auto_https off
}

(rate-limit) {
    @bottlenot path /api/*
    rate_limit @bottlenot 100r/m
}

https://archive.internal.example.cn {
    import rate-limit
    encode zstd gzip
    reverse_proxy backend:8000 {
        header_up Host {host}
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }
    handle /assets/* { reverse_proxy frontend:3000 }
    handle /* { reverse_proxy frontend:3000 }
}

https://grafana.internal.example.cn {
    @denied not remote_ip 192.168.30.0/24
    respond @denied 403
    reverse_proxy monitoring_grafana:3000
}
```

#### 备份脚本(PowerShell,Windows 计划任务)

```powershell
# D:\scripts\backup-postgres.ps1 - 每天 02:00
$ErrorActionPreference = 'Stop'
$ts   = Get-Date -Format 'yyyyMMdd-HHmm'
$root = 'D:\backups\postgres'
New-Item -ItemType Directory -Force -Path $root | Out-Null

# 全量基础备份
nerdctl exec pg bash -c "pg_basebackup -U replica_backup -D /tmp/pgbase -Ft -z -Xs -P" 2>&1 | Tee-Object "$root\pgbase-$ts.log"
nerdctl cp pg:/tmp/pgbase "$root\pgbase-$ts.tar.gz"
nerdctl exec pg rm -rf /tmp/pgbase

# 打包 WAL
Compress-Archive -Path "D:\containers\data\postgres-wal\*" -DestinationPath "$root\wal-$ts.zip" -Force

# 清理 30 天前
Get-ChildItem $root -File | Where-Object LastWriteTime -lt (Get-Date).AddDays(-30) | Remove-Item -Force
```

```powershell
# D:\scripts\backup-minio.ps1 - 每天 03:00
$ts   = Get-Date -Format 'yyyyMMdd-HHmm'
$dst  = "D:\backups\minio\$ts"
New-Item -ItemType Directory -Force -Path $dst | Out-Null
nerdctl exec minio mc alias set local http://localhost:9000 $env:MINIO_USER $env:MINIO_PASSWORD
nerdctl exec minio mc mirror --remove --overwrite local/archive "$dst/archive"
```

```powershell
# D:\scripts\backup-qdrant.ps1 - 每天 04:00
$ts = Get-Date -Format 'yyyyMMdd-HHmm'
$dst = "D:\backups\qdrant\$ts"
New-Item -ItemType Directory -Force -Path $dst | Out-Null
$cols = @("kb_chunks","kb_projects")
foreach ($c in $cols) {
    nerdctl exec qdrant curl -s -X POST "http://localhost:6333/collections/$c/snapshots"
}
nerdctl cp qdrant:/qdrant/storage/snapshots "$dst"
```

#### 开机自启(计划任务 SYSTEM)

```powershell
$action  = New-ScheduledTaskAction -Execute "C:\Windows\System32\cmd.exe" `
    -Argument "/c cd /d D:\deploy && nerdctl compose up -d"
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName "ArchiveComposeUp" `
    -Action $action -Trigger $trigger -User "SYSTEM" -RunLevel Highest -Settings $settings
```

### 13.2 一次性初始化清单

1. Windows Server 2022 标准化镜像(加域,命名 AP1/AP2)
2. 安装 Hyper-V、WSL2、Rancher Desktop
3. 装 nerdctl / mc / mkcert
4. 内网 Harbor 推送后端/前端/Caddy 镜像,拉取到 AP1/AP2
5. 创建数据目录(白名单加进 Defender 排除)
6. 创建服务账号 `svc-app`、`svc-backup`
7. 申请 AD CS 证书或用 mkcert 签发
8. Caddy 配置 → 启动 → 验证 HTTPS
9. `backup-*.ps1` 接入计划任务
10. Prometheus / Grafana / windows_exporter 接入
11. Runbook 写完,值班交接

### 13.3 资料参考(开源项目地址)

| 组件 | 仓库 / 官网 |
|---|---|
| Spring Boot | https://spring.io/projects/spring-boot |
| Apache Tika | https://tika.apache.org/ |
| MinIO | https://min.io/ |
| Qdrant | https://qdrant.tech/ |
| OpenSearch | https://opensearch.org/ |
| Elasticsearch | https://www.elastic.co/ |
| IK Analyzer | https://github.com/medcl/elasticsearch-analysis-ik |
| XXL-JOB | https://github.com/xuxueli/xxl-job |
| Drools | https://www.drools.org/ |
| PaddleOCR | https://github.com/PaddlePaddle/PaddleOCR |
| Tesseract | https://github.com/tesseract-ocr/tesseract |
| Ollama | https://ollama.com/ |
| Qwen2.5 | https://github.com/QwenLM/Qwen2.5 |
| bge-m3 | https://huggingface.co/BAAI/bge-m3 |
| WinSW | https://github.com/winsw/winsw |
| Rancher Desktop | https://rancherdesktop.io/ |
| Caddy | https://caddy.server/ |
| Prometheus | https://prometheus.io/ |
| Grafana | https://grafana.com/ |
| windows_exporter | https://github.com/prometheus-community/windows_exporter |

---

> **结语**:本方案已在 Windows Server + 内网隔离 + 3-5 人小团队的约束下做了完整闭环设计。核心优势:**完全内网自洽、合规零风险、部署可控、规则引擎独立成阶段保障 5 号需求落地**。请基于 §12 的 11 项决策点进行拍板,后续可直接进入 P0 启动。
