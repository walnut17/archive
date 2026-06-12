# 投委会档案管理系统 — 数据库架构

> ⚠️ **字段级真相源已迁至 [`DATABASE.md`](DATABASE.md)**（对齐 `init.sql`）。本文保留 ER 图与分章说明；若列名冲突，以 DATABASE.md 为准。

> 撰写人：Sisyphus | 日期：2026-06-10 | 版本：v1.0 + v1.1 (2026-06-11)

## 1. 总览

**数据库引擎**：MySQL 8.0.16

**数据库名**：`archive_db`

**字符集**：`utf8mb4`

**表总数**：v1.0 为 16 张业务表 + 2 张基础设施表 = 18 张；**v1.1 增量** 7 新表 + 7 ALTER = **30 张实体/表**（含 JDBC 表 user_role / project_member / proposal_series）

---

## 2. 表关系图

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────────┐
│   User   │     │  Project │     │ Proposal │     │   Material   │
│──────────│     │──────────│     │──────────│     │──────────────│
│ id (PK)  │◄────│ id (PK)  │◄────│ id (PK)  │◄────│ id (PK)      │
│ username │     │ user_id  │     │ proj_id  │     │ proposal_id  │
│ password │     │ code (UQ)│     │ code (UQ)│     │ material_id? │
│ role_id  │     │ name     │     │ title    │     │ category     │
│ display  │     │ amount   │     │ status   │     │ status       │
│ login_*  │     │ status   │     │ type     │──────┤ stage        │
└──────────┘     │ stage    │     │ summary  │     └──────┬───────┘
                 │*customer │     └──────────┘            │
                 │*init_amt │                            │
                 │*remain   │     ┌────────────────┐      │
                 │*archive  │     │ MaterialVersion │◄─────┘
                 └──────────┘     │────────────────│
                        │         │ id (PK)        │
                        │         │ material_id    │
                        │         │ version_no (UQ)│
                        │         │ parsed_text     │
                        ▼         │ (FULLTEXT)      │
                 ┌──────────┐     │ file_sha256    │
                 │   Todo   │     └───────┬────────┘
                 │──────────│             │
                 │ id (PK)  │     ┌───────┴────────┐
                 │ proj_id  │     │ChapterSummary   │
                 │ title    │     │────────────────│
                 │ status   │     │ version_id     │
                 │ source   │     │ content/summary│
                 │ due_at   │     │ (FULLTEXT)     │
                 │ priority │     └────────────────┘
                 └──────────┘
```

---

## 3. 完整表清单

### 3.1 核心业务表（6 张）

#### `role`
通用角色表，3 行预置数据。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK AUTO_INCREMENT | |
| name | VARCHAR(32) | UNIQUE NOT NULL | admin / user |
| description | VARCHAR(255) | | |

#### `user`
用户账号，预置 admin 账号。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK AUTO_INCREMENT | |
| username | VARCHAR(64) | UNIQUE NOT NULL | |
| password | VARCHAR(255) | | BCrypt |
| display_name | VARCHAR(64) | | 显示名 |
| role_id | BIGINT | FK → role.id | |
| failed_login_count | INT | DEFAULT 0 | v2 新增，登录限流 |
| lockout_until | DATETIME | | v2 新增 |

#### `project`
项目主表，FULLTEXT 索引。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| code | VARCHAR(32) | UNIQUE | 投委会编号 |
| name | VARCHAR(255) | NOT NULL | 项目名称 |
| user_id | BIGINT | FK → user.id | 负责人 |
| category | VARCHAR(32) | | 分类 |
| status | VARCHAR(16) | | 状态 |
| stage | VARCHAR(16) | | 阶段：立项/申请/贷后/结清 |
| amount_wan | DECIMAL(18,2) | | 金额（万元） |
| scheduled_meeting_at | DATE | | 预定上会日期 |
| summary | TEXT | | 项目概况 |
| customer_name | VARCHAR(255) | | v2/I-5 新增，客户名称 |
| initial_amount | DECIMAL(18,2) | | v2 新增，初始金额 |
| remaining_amount | DECIMAL(18,2) | | v2 新增，剩余金额 |
| archive_status | VARCHAR(16) | | v2 新增，归档状态 |

**FULLTEXT 索引**：`ft_name_cust` ON `(name, customer_name)` WITH PARSER ngram

#### `proposal`
议案表。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| code | VARCHAR(32) | UNIQUE | |
| project_id | BIGINT | FK → project.id | |
| title | VARCHAR(255) | NOT NULL | |
| type | VARCHAR(32) | | 议案类型 |
| status | VARCHAR(16) | | 已提交 / 审议中 / 通过 / 否决 |
| meeting_result | VARCHAR(32) | | 审议结果 |
| llm_summary | TEXT | | LLM 自动摘要 |

#### `material`
材料表。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| proposal_id | BIGINT | FK → proposal.id | |
| title | VARCHAR(255) | NOT NULL | |
| category | VARCHAR(32) | | |
| status | VARCHAR(16) | | |

#### `material_version`
材料版本表，核心检索表。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| material_id | BIGINT | FK → material.id | |
| version_no | INT | (material_id, version_no) UNIQUE | |
| original_filename | VARCHAR(500) | | |
| file_sha256 | VARCHAR(64) | | 去重 |
| parsed_text | LONGTEXT | | Tika 解析文本 |
| current | BOOLEAN | | 是否当前版本 |

**FULLTEXT 索引**：`ft_parsed_text` ON `(parsed_text)` WITH PARSER ngram

### 3.2 智能分析表（3 张）

#### `chapter_summary`
章节摘要。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| version_id | BIGINT | FK → material_version.id | |
| seq | INT | | 章节序号 |
| title | VARCHAR(255) | | 章节标题 |
| content | MEDIUMTEXT | | 章节原文 |
| summary | MEDIUMTEXT | | LLM 摘要 |

**FULLTEXT 索引**：`ft_chapter` ON `(content, summary)` WITH PARSER ngram

#### `timepoint`
时点表，LLM 从文本中提取的关键时间节点。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| project_id | BIGINT | FK → project.id | |
| title | VARCHAR(255) | | 标题 |
| event_date | DATE | | 事件日期 |
| confidence | DECIMAL(3,2) | | 置信度 |
| source | VARCHAR(32) | | 来源 |
| status | VARCHAR(16) | | 待确认 / 已确认 / 已过期 |

#### `todo`
待办事项，支持 3 种来源：TIMEOUT（时点自动转）、MANUAL（手动）、TRIGGER（规则触发）。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| project_id | BIGINT | FK → project.id | |
| title | VARCHAR(255) | NOT NULL | |
| priority | VARCHAR(16) | | HIGH / MEDIUM / LOW |
| status | VARCHAR(16) | | pending / completed |
| source | VARCHAR(32) | | TIMEOUT / MANUAL / TRIGGER |
| due_at | DATETIME | | |
| remark | TEXT | | |

### 3.3 规则引擎表（2 张）

#### `trigger_rule`
触发规则定义。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| name | VARCHAR(255) | NOT NULL | |
| event_type | VARCHAR(32) | | 4 种事件类型 |
| condition | TEXT | | Aviator 表达式 |
| enabled | BOOLEAN | | |

#### `trigger_action`
触发动作。

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| id | BIGINT | PK | |
| rule_id | BIGINT | FK → trigger_rule.id | |
| action_type | VARCHAR(32) | | CREATE_TODO |
| config_json | TEXT | | 动作配置 JSON |

### 3.4 可配置表（4 张）

#### `dict_type` / `dict_item`
字典分类 + 字典项，支持业务维度可配置化（项目分类、议案类型、材料分类等）。

| 表 | 关键列 | 说明 |
|----|--------|------|
| dict_type | code (UNIQUE), name, description | 字典分类 |
| dict_item | type_code (FK), item_key, item_value, sort_order | 字典项 |

#### `extraction_method`
字段抽取方法，可配置的 LLM Prompt 模板。

| 列 | 类型 | 说明 |
|---|------|------|
| id | BIGINT PK | |
| name | VARCHAR(64) UNIQUE | 方法名 |
| target_field | VARCHAR(64) | 目标字段 |
| prompt_template | TEXT | LLM 提示词模板 |
| example_json | TEXT | 示例输出 |
| enabled | BOOLEAN | |

#### `comparison_method`
对比方法配置。

| 列 | 类型 | 说明 |
|---|------|------|
| id | BIGINT PK | |
| name | VARCHAR(64) UNIQUE | |
| description | TEXT | |
| prompt_template | TEXT | LLM 对比提示词 |

### 3.5 审计与日志表（2 张）

#### `audit_log`
关键操作审计（数据无外键，独立存储）。

| 列 | 类型 | 说明 |
|---|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | |
| action | VARCHAR(64) | |
| entity_type | VARCHAR(32) | |
| entity_id | BIGINT | |
| old_value / new_value | TEXT | |
| ip_address | VARCHAR(45) | |

#### `llm_call_log`
LLM 调用埋点。

| 列 | 类型 | 说明 |
|---|------|------|
| id | BIGINT PK | |
| username | VARCHAR(64) | |
| scenario | VARCHAR(32) | |
| model | VARCHAR(64) | |
| duration_ms | INT | |
| status | VARCHAR(16) | |
| prompt_tokens / completion_tokens | INT | |

### 3.6 基础设施表（Plan I 新增）

#### `spring_ai_chat_memory`
Agent 多轮对话记忆（Spring AI 1.1 兼容格式）。

| 列 | 类型 | 说明 |
|---|------|------|
| id | BIGINT PK | |
| conversation_id | VARCHAR(64) | sessionId |
| message_type | VARCHAR(16) | user / assistant / system / tool |
| content | TEXT | |
| created_at | TIMESTAMP | |

**索引**：`idx_conversation_created` ON `(conversation_id, created_at)`

---

## 4. FULLTEXT 索引策略

| 表 | 列 | 索引名 | 用途 |
|----|----|--------|------|
| material_version | parsed_text | ft_parsed_text | 文档全文检索 |
| chapter_summary | content, summary | ft_chapter | 章节检索 |
| project | name, customer_name | ft_name_cust | 项目语义搜索 |

**配置**：均使用 `WITH PARSER ngram`，`ngram_token_size=1`（最小 1 字匹配）。

**不引入外部搜索引擎的理由**：
- 数据量较小（典型场景 <10 万条）
- 单机部署，避免引入 Elasticsearch 的运维负担
- MySQL FULLTEXT + ngram 的中文效果在可控场景下足够

---

## 5. 迁移历史

| 文件 | 阶段 | 改动 |
|------|------|------|
| init.sql | M0 | 18 张表完整定义（含后续迁移整合） |
| M2-fulltext-index.sql | M2 | material_version 加 FULLTEXT（已集成到 init.sql） |
| v2-schema.sql | v2 | 10 张新表 + 3 ALTER（累计金额/归档/限流） |
| G-llm-call-log.sql | Plan G | llm_call_log 表 |
| I-find-project-fulltext.sql | Plan I-5 | project 加 customer_name + FULLTEXT |
| I-chat-memory.sql | Plan I-13 | spring_ai_chat_memory 表 |

**使用方式**：生产环境执行 `init.sql`（DROP + CREATE）全量初始化，开发环境按迁移顺序逐步执行。

---

## 6. 种子数据

### 角色（3 条）

| name | description |
|------|-------------|
| admin | 系统管理员 |
| user | 普通用户 |

### 字典（8 类，50+ 条）

| type_code | 示例项 |
|-----------|--------|
| project_category | 信托贷款/私募股权/固定收益/ABS/REITs/组合投资 |
| project_status | 立项/申请/审议/通过/否决/贷后/结清 |
| project_stage | 立项/申请/贷后/结清 |
| proposal_type | 立项/申请/临时/补充 |
| proposal_status | 草稿/已提交/审议中/通过/否决 |
| material_category | 尽调报告/法律意见书/评估报告/审计报告/租审报告/放款通知/付款凭证/收款凭证/催收函/律师函/法院传票/和解协议/还款计划/其他 |
| material_status | 上传中/已上传/解析中/已解析/解析失败 |
| todo_priority | HIGH/MEDIUM/LOW |

### 抽取方法（3 条）

| name | target_field | 说明 |
|------|-------------|------|
| 合同中金额 | amount | 从尽调报告/评估报告提取金额 |
| 合同中日期 | date | 从法律意见书提取关键日期 |
| 合同中分类 | category | 从材料标题提取业务分类 |

### 触发规则（6 条）

| 事件 | 条件 | 动作 |
|------|------|------|
| 材料上传 | 分类=律师函 | 创建HIGH待办，7天期限 |
| 材料上传 | 分类=法院传票 | 创建HIGH待办，3天期限 |
| 材料上传 | 分类=付款/收款凭证 | 创建MEDIUM待办，当天 |
| 分类变更 | 分类∈各种审计报告 | 创建MEDIUM待办，3天 |
| 状态变更 | 项目状态=结清 | 创建LOW待办，归档 |
| 时点临近 | 时点距离≤3天 | 创建LOW待办 |

---

## v1.1 增量表 (MOD-01, 2026-06-11)

| 类型 | 表/变更 | 说明 |
|---|---|---|
| 新表 | notification, failure_log, import_batch, import_error, proposal_series, user_role, project_member | RI-49/58/61/63/68 |
| ALTER | project, proposal, material, business_term, project_fact_event, audit_log, user | +deleted_at/deleted_by/version/confidence_level 等 |
| 触发器 | trg_pfe_no_delete, trg_pfe_whitelist_update | fact_event 不可删 + 白名单 UPDATE |
