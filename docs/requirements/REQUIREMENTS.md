# 投委会档案管理系统 — 业务需求 v1

> 文档说明: 本文档是**纯业务视角**的完整需求,作为后续架构落地、API 设计、测试用例的依据
> 版本: v1 / 2026-06-08
> 阅读对象: 架构师 / 开发 Agent / 测试 Agent
> 状态: 初版,基于与项目方 4 轮业务访谈梳理

---

## 1. 系统定位与边界

### 1.1 系统做什么

**投委会档案管理与智能分析系统**。围绕投委会(投资决策委员会)审批的项目,从立项到结清全周期内,所有材料、报告、待办、时点、规则**统一归档、检索、分析、提醒**。

系统核心能力:
- **项目档案管理**: 一个项目从立项到结清的全部材料
- **报告智能分析**: 上传报告,自动抽字段、识分类、提时点
- **知识库问答**: 全文检索(基于 MySQL FULLTEXT,不向量化; v1.1 起增 Agent 模式 5 步 ReAct + 简称/拼音语义兜底,见 §5.6)
- **待办中心**: 三来源汇聚(LLM 抽、手工加、规则触发),首页提醒
- **触发规则引擎**: 材料分类 → 自动生成待办
- **累计金额计算**: 初始 - 付出 + 收回(全自动,DECIMAL)

### 1.2 系统不做什么(明确边界)

> 这是本系统的**最重要设计决策之一**。

| 不做 | 说明 | 谁来补 |
|---|---|---|
| ❌ 议程编排 | 投委会会议排期、议程列表 | 现有 OA / 会议系统 |
| ❌ 投票表决 | 委员实名/匿名投票、计票、决议结果 | 现有 OA / 会议系统 |
| ❌ 会议管理 | 会议室预定、会议纪要(系统只存"纪要材料") | 现有 OA / 会议系统 |
| ❌ 财务记账 | 银行流水、记账凭证、会计科目 | 现有 ERP / 财务系统 |
| ❌ 合同管理 | 合同起草、审批、用印 | 现有合同管理系统 |
| ❌ 客户关系(CRM) | 客户跟进、销售漏斗 | 现有 CRM |
| ❌ 移动 App / PWA | 移动端访问 | 不做,内网 PC 浏览器即可 |
| ❌ 历史数据迁移 | 旧系统数据导入 | 本期不做 |

### 1.3 重要替代说明

- **会议纪要作为贷后材料**: 会议开了之后,纪要文件上传到本系统做归档(触发"补充纪要"待办)
- **决议作为申请报告附件**: 投委会的决议文字写在申请报告里(系统抽"决议"字段)
- **财务凭证作为贷后材料**: 收付款凭证上传到系统,触发"走账"待办 + 累加减金额

---

## 2. 核心业务对象(领域模型)

### 2.1 实体清单

```
项目 (Project)
├── 客户名称
├── 项目名称
├── 初始总金额 (DECIMAL(18,2))
├── 剩余金额 (自动算: 初始 - 累计付出 + 累计收回)
├── 起投时间
├── 服务商名称
├── 阶段: 立项 / 申请 / 贷后 / 结清
├── 报告 (4 阶段各 1 份,贷后 N 份)
│   ├── 报告材料 (1 份对应 N 份"材料")
│   │   └── 材料版本 (version_no, parsed_text, parse_status, sha256)
│   │       └── 章节 (chapter_no, content, summary)
│   └── 提取的时点 (timepoint)
├── 触发规则 (按项目设置)
├── 待办 (3 来源)
└── 审计日志 (audit_log)
```

### 2.2 实体字段表

#### 2.2.1 Project 项目

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| customer_name | VARCHAR(128) | ✓ | 客户名称(从立项报告抽) |
| name | VARCHAR(255) | ✓ | 项目名称(从立项报告抽) |
| initial_amount | DECIMAL(18,2) | ✓ | 初始总金额 |
| remaining_amount | DECIMAL(18,2) | - | 剩余金额,**自动算**: 初始 - 累计付出 + 累计收回 |
| start_date | DATE | ✓ | 起投时间 |
| service_provider | VARCHAR(255) | ✓ | 服务商名称 |
| stage | VARCHAR(32) | ✓ | 当前阶段: INITIAL / APPLY / POST_LOAN / SETTLED |
| stage_locked | BOOLEAN | - | 阶段锁定(结清后置 true,不可改) |
| trigger_rules_enabled | BOOLEAN | - | 是否启用触发规则(项目级开关) |
| created_at / updated_at | DATETIME | - | 审计 |
| created_by / updated_by | VARCHAR(64) | - | 审计 |

#### 2.2.2 Material 材料(每个报告对应 1 份 material)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| project_id | BIGINT FK | ✓ | 所属项目 |
| title | VARCHAR(255) | ✓ | 材料标题(默认 = 文件名) |
| category | VARCHAR(64) | ✓ | 分类(从字典来): 立项报告/申请报告/贷后议案/贷后事项/付款凭证/收款凭证/律师函/法院传票/会议纪要/风险报告/贷后检查报告/结清报告/其他 |
| stage | VARCHAR(32) | ✓ | 阶段: INITIAL/APPLY/POST_LOAN/SETTLED |
| status | VARCHAR(32) | ✓ | 状态: 草稿/已提交/已归档 |
| current_version_id | BIGINT FK | - | 当前版本 |
| description | TEXT | - | 备注 |
| auto_classified | BOOLEAN | - | 分类是否由 AI 识别(true) / 手工选(false) |
| created_at / updated_at | DATETIME | - | 审计 |

#### 2.2.3 MaterialVersion 材料版本(每次上传 1 份新版本)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| material_id | BIGINT FK | ✓ | 所属材料 |
| version_no | INT | ✓ | 版本号(从 1 开始,递增) |
| storage_path | VARCHAR(512) | ✓ | 文件存储路径 |
| file_name | VARCHAR(255) | ✓ | 原始文件名 |
| file_size | BIGINT | ✓ | 文件大小(byte) |
| sha256 | CHAR(64) | ✓ | 文件 SHA-256(去重用) |
| parsed_text | LONGTEXT | - | Tika 解析的全文(供 FULLTEXT 检索) |
| parse_status | VARCHAR(32) | - | pending/running/success/failed |
| parse_error | VARCHAR(500) | - | 解析失败原因 |
| parse_started_at / parse_finished_at | DATETIME | - | 解析起止 |
| change_note | VARCHAR(500) | - | 版本变更说明(谁传的、改了什么) |
| uploaded_by | VARCHAR(64) | ✓ | 上传人 |
| created_at | DATETIME | - | 审计 |

#### 2.2.4 ChapterSummary 章节(M2 应该有,本期确认落地)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| material_version_id | BIGINT FK | ✓ | 所属版本 |
| chapter_no | INT | ✓ | 章节序号 |
| chapter_title | VARCHAR(255) | - | 章节标题(若 SectionService 抽出) |
| content | MEDIUMTEXT | - | 章节内容 |
| summary | TEXT | - | 章节摘要(LLM 生成) |
| keywords | VARCHAR(512) | - | 关键词(逗号分隔) |
| page_start / page_end | INT | - | 起止页 |
| created_at | DATETIME | - | 审计 |

#### 2.2.5 Timepoint 时点(LLM 从报告里抽)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| material_version_id | BIGINT FK | ✓ | 所属版本 |
| project_id | BIGINT FK | ✓ | 所属项目(冗余,便于查) |
| matter | VARCHAR(255) | ✓ | 事项名,如"催付款" |
| due_date | DATE | ✓ | 截止日期 |
| confidence | DECIMAL(3,2) | - | LLM 置信度(0-1) |
| evidence | TEXT | - | 引用证据(原文摘录) |
| extraction_method_id | BIGINT FK | - | 用的抽取方法 |
| source_category | VARCHAR(64) | - | 触发的材料分类 |
| status | VARCHAR(32) | - | pending/active/expired/closed |
| created_at | DATETIME | - | 审计 |

#### 2.2.6 Todo 待办(3 个来源,统一管理)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| project_id | BIGINT FK | ✓ | 所属项目 |
| source | VARCHAR(32) | ✓ | 来源: TIMEOUT(时点)/ MANUAL(手工)/ TRIGGER(规则) |
| source_ref_id | BIGINT | - | 源 ID(时点 ID / 手工则 NULL / 规则触发的素材 ID) |
| title | VARCHAR(255) | ✓ | 待办标题 |
| description | TEXT | - | 描述 |
| due_date | DATE | - | 截止日期(过期变红) |
| priority | VARCHAR(16) | - | HIGH/MEDIUM/LOW |
| status | VARCHAR(32) | ✓ | pending/active/overdue/done/cancelled |
| done_by | VARCHAR(64) | - | 谁标了"已办" |
| done_at | DATETIME | - | 何时标"已办" |
| created_at / updated_at | DATETIME | - | 审计 |

#### 2.2.7 TriggerRule 触发规则(项目级)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| project_id | BIGINT FK | ✓ | 所属项目(可空 = 全局规则) |
| name | VARCHAR(128) | ✓ | 规则名 |
| condition_json | JSON | ✓ | 条件,如 `{"material_category":"律师函"}` |
| action_json | JSON | ✓ | 动作,如 `{"create_todo":{"title":"评估法律风险","priority":"HIGH","due_days":7}}` |
| enabled | BOOLEAN | - | 是否启用 |
| created_at / updated_at | DATETIME | - | 审计 |

#### 2.2.8 ExtractionMethod 字段抽取方法(用户/管理员可加)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| name | VARCHAR(128) | ✓ | 方法名,如"立项报告-抽取客户名" |
| target_field | VARCHAR(64) | ✓ | 抽取目标字段(写到 project/material 哪个字段) |
| prompt_template | TEXT | ✓ | LLM prompt 模板 |
| is_builtin | BOOLEAN | - | 是否系统预置(预置不允许删,只允许改) |
| enabled | BOOLEAN | - | 是否启用 |
| created_by | VARCHAR(64) | - | 创建人 |
| created_at / updated_at | DATETIME | - | 审计 |

#### 2.2.9 ComparisonMethod 对比方法(立项-申请对比)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | - | 自增 |
| name | VARCHAR(128) | ✓ | 方法名 |
| prompt_template | TEXT | ✓ | LLM prompt 模板 |
| is_builtin | BOOLEAN | - | 系统预置 |
| enabled | BOOLEAN | - | |
| created_at | DATETIME | - | |

#### 2.2.10 DictType + DictItem 参数表(分类字典)

| 表 | 字段 | 类型 | 说明 |
|---|---|---|---|
| dict_type | type_code | VARCHAR(64) PK | 字典分类编码,如 `material_category` |
| dict_type | type_name | VARCHAR(128) | 字典名,如"材料分类" |
| dict_type | is_system | BOOLEAN | 系统内置不允许删 |
| dict_item | id | BIGINT PK | |
| dict_item | type_code | VARCHAR(64) FK | 关联 dict_type |
| dict_item | item_key | VARCHAR(64) | 枚举值 |
| dict_item | item_value | VARCHAR(256) | 展示值 |
| dict_item | is_default | BOOLEAN | 新建时是否默认选 |
| dict_item | sort_order | INT | 排序 |
| dict_item | enabled | BOOLEAN | 是否在下拉框显示 |

#### 2.2.11 AuditLog 审计日志(全量)

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT | 操作人 |
| action | VARCHAR(64) | 操作:LOGIN/UPLOAD/DELETE_TODO/... |
| entity_type | VARCHAR(64) | 实体类型 |
| entity_id | BIGINT | 实体 ID |
| before_json | JSON | 修改前(全量) |
| after_json | JSON | 修改后(全量) |
| ip | VARCHAR(64) | 来源 IP |
| ts | DATETIME | 时间 |

### 2.3 实体关系图

```
┌──────┐ 1   N ┌──────────┐ 1   N ┌─────────────────┐ 1   N ┌──────────────────┐
│ User │──────│ Project  │──────│ Material       │──────│ MaterialVersion │
└──────┘       └──────────┘       └─────────────────┘       └──────────────────┘
                  │ N 1                  │ 1 N                     │ 1 N
                  ▼                       ▼                          ▼
              ┌───────┐               ┌────────┐         ┌────────────────────┐
              │Todo   │               │Trigger │         │ChapterSummary      │
              │       │               │Rule    │         │Timepoint(LLM抽)    │
              └───────┘               └────────┘         └────────────────────┘
                  │
                  ▼
              ┌──────────────┐
              │DictType/Item │  (项目状态/分类/优先级 等字典)
              └──────────────┘

全局(非项目级):
┌──────────────────┐ ┌────────────────┐ ┌──────────┐
│ExtractionMethod  │ │ComparisonMethod│ │AuditLog  │
└──────────────────┘ └────────────────┘ └──────────┘
```

---

## 3. 业务角色与权限

### 3.1 角色清单

只有 2 个角色:

| 角色代码 | 业务岗位 | 主要职责 |
|---|---|---|
| `admin` | 系统管理员 | 管参数(字典/抽取方法/对比方法) + 任何项目操作 |
| `user` | 普通用户(投委会秘书/项目经理/业务部门/法务等) | 任何项目/材料 CRUD + 查看 + 知识库问答 |

### 3.2 权限矩阵

| 功能 | admin | user |
|---|---|---|
| 登录/登出 | ✓ | ✓ |
| 项目 CRUD | ✓ | ✓ |
| 材料 CRUD | ✓ | ✓ |
| 材料版本上传/下载 | ✓ | ✓ |
| 知识库问答 | ✓ | ✓ |
| 待办 CRUD(自己) | ✓ | ✓ |
| 时点查看/管理 | ✓ | ✓ |
| 触发规则 CRUD(项目级) | ✓ | ✓ |
| 抽取方法 CRUD(全局) | ✓ | ✗ |
| 对比方法 CRUD(全局) | ✓ | ✗ |
| 字典 CRUD(全局) | ✓ | ✗ |
| 审计日志查看 | ✓ | ✗ |
| 用户管理 | ✓ | ✗ |

> **简化策略**: 不做细粒度字段级权限、不做"只能看自己项目"的隔离。本期所有项目对所有登录用户可见。

---

## 4. 业务流程(4 阶段)

### 4.1 立项(INITIAL,1 次)

**触发**: 业务部门决定要做一个项目。

**必交材料**: 立项报告(尽调报告,通常 30-50 页 Word/PDF)

**必抽字段**(从立项报告里抽):
- `customer_name` 客户名称
- `name` 项目名称
- `initial_amount` 初始总金额
- `start_date` 起投时间
- `service_provider` 服务商名称
- (可选) 投委会"待落实问题"清单

**模式 A:先传报告,AI 抽,弹窗补**
1. 用户选文件上传
2. 后端 Tika 解析(后台异步)
3. 触发 AI 抽取(走 extraction_method,可多个)
4. 弹窗:已抽到的字段(默认值) + 待补字段
5. 用户补完后保存 → 项目进入 INITIAL 完成

**模式 B:不上传,全手工**
1. 弹窗
2. 用户全手工填字段
3. 保存

**两种模式都走同一个弹窗**——AI 抽到的字段做默认值,空白让用户补。

**输出**: 创建 1 个 Project(stage=INITIAL),1 个 Material(category=立项报告),1 个 MaterialVersion,可选的 Timepoint 列表。

**阶段流转**: 立项"已提交"后,可走"申请"阶段。

### 4.2 申请(APPLY,1 次)

**触发**: 立项完成后,业务部门正式向上申请。

**必交材料**: 申请报告(终版报告)。

**抽字段**: 同立项(同套 extraction_method)。

**核心动作: 立项-申请对比**
1. 用户上传申请报告
2. 系统自动对比:
   - 默认方法 `Q&A 验证`: 拿立项报告里的"待落实问题"清单,逐个问 LLM "在申请报告里有没有回答?",LLM 答 yes/no + 引用
   - 可用其他 comparison_method(用户加的)
3. 展示对比结果页:
   - 每个"待落实问题"一张卡片
   - `已落实 ✓(置信度 0.92,引用 "报告 P.12 第 3 段")` 或 `未落实 ✗`
   - 全部"已落实"才能进下一阶段

**输出**: Project.stage = APPLY 完成,可走"贷后"。

**重要**: 申请阶段**只跟立项对比**,不跟前阶段对比。

### 4.3 贷后(POST_LOAN,N 次)

**触发**: 申请通过后,项目进入贷后(可多次进入)。

**3 类材料 + 各自触发**:

| 材料分类 | 触发的待办 | 金额变化 |
|---|---|---|
| 贷后议案(换服务商/调整费率等) | "重评估 XXX" | 无 |
| 贷后事项(会议纪要/风险报告等) | "补充纪要" / "风险复核" | 无 |
| 付款凭证 | "确认付款 XXX" | remaining -= amount |
| 收款凭证 | "确认收款 XXX" | remaining += amount |

> 律师函 / 法院传票 等"特殊材料"也属于贷后事项(分类走 AI 自动 + 手工覆盖)。

**模式**: 同立项(上传 + AI 分类 / 纯手工填)。

**待办 + 时点**: 材料上传后,系统:
1. 触发规则引擎(如果项目启用了)
2. 异步 LLM 抽时点(配置 enabled)
3. 时点自动转"待办"(source=TIMEOUT)

**金额联动**:
- 收到付款凭证 → 自动 `累计付出 += amount` → 自动重算 `remaining`
- 收到收款凭证 → 自动 `累计收回 += amount` → 自动重算 `remaining`
- **DECIMAL(18,2) 精度,严禁 float**

**输出**: Project.stage 仍 = POST_LOAN,但有 1+ 份新 Material/Version。可持续 N 次。

**阶段流转**: 贷后任意时点可"结清"。

### 4.4 结清(SETTLED,1 次)

**触发**: 项目到期还清 / 提前还款 / 不良处置。

**必交材料**: 结清报告。

**不可逆**: 结清后 Project.stage_locked = true,**不能再回退**。所有材料仍然可查,只是不能再操作。

**输出**: Project.stage = SETTLED,stage_locked = true。审计日志记一笔。

---

## 5. 关键功能详述

### 5.1 报告智能分析

#### 5.1.1 字段抽取(可扩展)

- 系统有 N 个预置 `ExtractionMethod`(每个对应 1 个 target_field)
- 上传材料时,异步触发所有 enabled 的方法
- 每个方法走 LLM,prompt 模板在数据库里
- **用户可加方法**: admin 在"参数管理 → 抽取方法"页面加新行,填 prompt + target_field

**默认预置方法**:
1. `立项报告-抽取客户名` → target=project.customer_name
2. `立项报告-抽取项目名` → target=project.name
3. `立项报告-抽取初始金额` → target=project.initial_amount
4. `立项报告-抽取起投时间` → target=project.start_date
5. `立项报告-抽取服务商` → target=project.service_provider
6. `报告-抽取待落实问题` → 写入 material 备注 / timepoint

#### 5.1.2 分类识别(预设 10+ 分类)

**预设分类**(写入 dict_item seed):
- 立项报告 / 申请报告 / 结清报告
- 贷后议案 / 贷后事项 / 贷后检查报告
- 付款凭证 / 收款凭证
- 律师函 / 法院传票
- 会议纪要 / 风险报告
- 其他

**两种分类方式**:
- **AI 自动分类**: 上传时 LLM 识别 → 自动写入 material.category,`auto_classified=true`
- **手工改**: 用户在编辑页面下拉选,`auto_classified=false`

#### 5.1.3 时点提取

- 异步: 解析成功后,后台跑 LLM
- 输出结构: `[{ matter, due_date, confidence, evidence }]`
- 入库到 `timepoint` 表
- **同时**自动转成 1 个 Todo(source=TIMEOUT)
- 用户可编辑 / 删除

### 5.2 时点 + 待办

#### 5.2.1 待办 3 来源

| 来源 | 触发时机 | 字段 | 是否可改 |
|---|---|---|---|
| TIMEOUT(时点) | 时点入库时自动 | title=matter, due_date=due_date | 可编辑 |
| MANUAL(手工) | 用户在"待办"页面加 | 全用户填 | 可编辑 |
| TRIGGER(规则) | 材料入库后规则匹配 | 规则 action 决定 | 可编辑 |

#### 5.2.2 时点临近提醒

- **可配置天数**(全局配置,默认 7 天)
- 临近 + 过期的待办 → 首页显示
- **过期变红**(el-date 标签 / row class)
- "已办"按钮 → status=done → 列表里消失

#### 5.2.3 首页(双模)

- **有待办**: 显示待办列表(按 due_date 升序,过期的在最上)
- **没待办**: 显示知识库问答快捷入口

### 5.3 触发规则引擎

#### 5.3.1 规则模式(简单条件)

```json
{
  "condition": {
    "material_category": "律师函"
  },
  "action": {
    "create_todo": {
      "title": "评估法律风险",
      "priority": "HIGH",
      "due_days": 7
    }
  }
}
```

#### 5.3.2 默认预置规则(全局)

| 条件 | 待办 | 优先级 | deadline |
|---|---|---|---|
| category=律师函 | 评估法律风险 | HIGH | 7 天后 |
| category=法院传票 | 应诉准备 | HIGH | 3 天后 |
| category=收款凭证 | 确认走账 | MEDIUM | 当天 |
| category=付款凭证 | 确认付款 | MEDIUM | 当天 |
| category=贷后议案 | 重评估议案 | MEDIUM | 5 天后 |
| category=贷后事项 | 补充纪要 | LOW | 14 天后 |
| category=会议纪要 | 复核纪要 | LOW | 7 天后 |
| category=风险报告 | 风险复核 | HIGH | 3 天后 |
| category=贷后检查报告 | 审阅报告 | MEDIUM | 5 天后 |
| (项目级)收到付款凭证 | (项目自定义) | - | - |

#### 5.3.3 规则执行时机

- 材料 version parseStatus=success 后
- 异步执行
- 失败不阻塞材料保存,记录到 audit_log + 重试 3 次

### 5.4 累计金额计算

**公式**:
```
remaining_amount = initial_amount - 累计付出 + 累计收回
```

**实现**:
- 付款凭证入账 → `累计付出 += 凭证金额`(DECIMAL)
- 收款凭证入账 → `累计收回 += 凭证金额`
- 每次入账后,**事务内**重算 `remaining_amount` 写回 project 表
- 写入 `audit_log`(before/after)
- **DECIMAL(18,2) 精度**,float 会出精度问题,严禁

**示例**:
- 初始: 1,000,000.00
- 6 月付 200,000.00 → remaining = 800,000.00
- 9 月收 50,000.00 → remaining = 850,000.00
- 12 月付 300,000.00 → remaining = 550,000.00
- 3 月收 550,000.00 → remaining = 0(可结清)

### 5.5 立项-申请对比

#### 5.5.1 默认方法: Q&A 验证

Prompt 模板(预置):
```
你是一个投委会审批秘书。
立项报告里的"待落实问题"列表:
{questions_json}

申请报告全文:
{apply_report_text}

请对每个问题,逐个回答:
1. 申请报告里是否回答了? (yes/no)
2. 如果 yes,引用原文(标注页码或段落)
3. 置信度 (0-1)
4. 如果 no,简要说缺什么

输出 JSON 数组。
```

#### 5.5.2 用户可加方法

- admin 在"参数管理 → 对比方法"页面加
- 选 LLM 同款 prompt 模板
- 上传申请报告时,所有 enabled 的方法**都跑**(可设置必跑 vs 可选)

#### 5.5.3 结果展示

- 立项报告 + 申请报告 + 对比结果三栏(可折叠)
- 每个"待落实问题"一个卡片:
  - ✓ 已落实 (绿色,置信度 X.XX,引用 "P.12 第 3 段 '...'")
  - ✗ 未落实 (红色,提示缺什么)
  - ? 部分落实 (黄色,引用 + 提示)

---

### 5.6 智能问答 (Agent 模式)

> v1.1 变更: 智能问答从"单轮 FULLTEXT 检索 + LLM 总结"升级为 **Agent ReAct 循环**,支持多步工具调用 + 简称/拼音/口头语语义匹配.

#### 5.6.1 架构

```
用户问题
   ↓
┌────────────────────────────────────────────┐
│  AgentEngine.run() — 手写 5 步 ReAct 循环   │
│                                            │
│  for i = 1..MAX_ITERATIONS (默认 5):       │
│    1. LLM 看上下文 (system prompt + 历次   │
│       thought/tool/observation) → 输出     │
│       {thought, tool, args}                 │
│    2. 如果 tool = FINAL_ANSWER → 跳出       │
│    3. 否则 dispatch tool                    │
│    4. observation 喂回 LLM, 进入下一轮      │
│    5. 死循环检测 (loopTriggerThreshold=1)   │
│       - 第 1 次触发 → 注入 hint             │
│       - 第 2 次触发 → 强制 FINAL_ANSWER     │
└────────────────────────────────────────────┘
```

#### 5.6.2 工具清单 (4 个)

| 工具 | 作用 | 何时调 |
|---|---|---|
| `find_project(query, topN)` | 4 级兜底定位项目 | 任何业务问题必须先调 |
| `search_fulltext(query, topN, projectCode)` | MySQL FULLTEXT 检索材料 | 找材料内容时 |
| `query_mysql(table, where, columns, limit)` | 查业务数据 (白名单 6 表) | 找数据时 |
| `get_project_business_data(projectCode)` | 项目业务汇总 | 已知 projectCode 时 |

#### 5.6.3 find_project 4 级兜底链 (核心设计)

| 级别 | 方法 | 适用场景 | 成本 |
|---|---|---|---|
| 1 | `findByCode` (精确) | 用户报 projectCode | 0 LLM, <10ms |
| 2 | `searchByNameOrCustomerFulltext` (MySQL FULLTEXT) | 完整名称 / 客户名 | 0 LLM, ~50ms |
| 3 | `searchByKeywordAsList` (JPQL LIKE %%) | 简称 / 模糊词 (新增 v1.1) | 0 LLM, ~100ms |
| 4 | `glmService.semanticMatchProjects` (LLM 兜底) | 拼音首字母 / 口头语 (如 "lmz" → "林谋志") | 1 LLM, ~1.5s |

**触发条件**：
- 级别 1: 总是跑, 命中即返回
- 级别 2: 级别 1 miss 时跑
- 级别 3: 级别 2 miss 时跑 (新增 v1.1)
- 级别 4: 级别 3 miss **且** `projectRepo.count() <= llmFallbackMaxTotal` (默认 300, 可配 `FIND_PROJECT_LLM_FALLBACK_MAX_TOTAL` 环境变量) 时跑

**为什么不维护静态索引 (如 MD 文件 / 额外表)**：
- 项目数 < 300 是有限可枚举的, 全量喂给 LLM 比维护索引更省事
- 数据自动同步 (项目表新增/改名/别名无需手工更新)
- LLM 兜底 = "把项目列表当 prompt 上下文",**0 维护成本**

#### 5.6.4 死循环保护 (v1.1 新增)

**问题**: 5 步 ReAct 容易让 LLM 死循环调同 tool 同 args (如连续 5 次 find_project("lmz")).

**策略** (可配 `spring.ai.agent.find-project.loop-trigger-threshold`, 默认 1):
- 连续 (阈值) 步 (tool, toolArgs) 完全相同 → 视为死循环
- 第 1 次触发: 注入 hint "你已连续 N 次用相同工具和参数得到相同结果, 请换不同关键词/工具 或直接 FINAL_ANSWER"
- 第 2 次触发: 强制 FINAL_ANSWER, 不再让 LLM 重试 (节省 token + 避免 5 步全空跑)

**总死循环预算 = 3 步** (步 1, 步 2 触发 hint, 步 3 又触发 → 强制 FINAL_ANSWER), MAX_ITERATIONS=5 留 2 步给真正干活.

#### 5.6.5 性能 (修订)

| 模式 | 响应时间 | 适用 |
|---|---|---|
| 单轮检索 (无 Agent) | < 3s | 简单问题, 已知 projectCode |
| Agent 模式 (5 步 ReAct) | < 30s | 业务问题, 需多步推理 |

**前端 axios timeout = 120s**, 给 Agent 模式留 4x buffer.

#### 5.6.6 验收 (v1.1 修订)

- [ ] 简称 → 全名: "lmz项目" 找到 "林谋志" 项目 (走 LLM 兜底, 1.5s 内返回)
- [ ] 死循环保护: 模拟 LLM 死循环 (mock tool 返回同 obs), 第 3 步强制 FINAL_ANSWER
- [ ] 5 步预算: 单次 qa-ask 调用 LLM ≤ 5 次 (稳态), < 7 次 (含死循环保护)
- [ ] 响应时间: Agent 模式 < 30s (P95)

---

## 6. 报告字段抽取扩展机制

### 6.1 流程

1. admin 进"参数管理 → 抽取方法"页面
2. 点"新增"
3. 填:
   - 名称(自由,如"立项报告-抽法定代表人")
   - 目标字段(下拉选:project.* / material.*)
   - prompt 模板(可变量:`{report_text}`、`{context}`)
4. 保存 → 立即生效,下次上传材料时自动跑

### 6.2 字段约束

- target_field 必须是已有 entity 的可写字段
- prompt 必须含 `{report_text}` 占位符
- 预置方法 `is_builtin=true`,不允许删,允许改

### 6.3 何时跑

- 材料上传时,**所有 enabled 的方法**都跑
- 异步,不阻塞上传响应
- 失败不影响其他方法,记录日志

---

## 7. 触发规则示例集

> 跟 §5.3 一致,这里给出 10+ 条预置规则的具体配置示例。

```json
[
  {
    "name": "律师函 → 评估法律风险",
    "condition": {"material_category": "律师函"},
    "action": {"create_todo": {"title": "评估法律风险", "priority": "HIGH", "due_days": 7}}
  },
  {
    "name": "法院传票 → 应诉准备",
    "condition": {"material_category": "法院传票"},
    "action": {"create_todo": {"title": "应诉准备", "priority": "HIGH", "due_days": 3}}
  },
  {
    "name": "收款凭证 → 走账确认 + 加金额",
    "condition": {"material_category": "收款凭证"},
    "action": {
      "create_todo": {"title": "确认走账", "priority": "MEDIUM", "due_days": 0},
      "adjust_amount": {"direction": "in", "field": "remaining_amount"}
    }
  },
  {
    "name": "付款凭证 → 走账确认 + 减金额",
    "condition": {"material_category": "付款凭证"},
    "action": {
      "create_todo": {"title": "确认付款", "priority": "MEDIUM", "due_days": 0},
      "adjust_amount": {"direction": "out", "field": "remaining_amount"}
    }
  },
  {
    "name": "贷后议案 → 重评估",
    "condition": {"material_category": "贷后议案"},
    "action": {"create_todo": {"title": "重评估议案", "priority": "MEDIUM", "due_days": 5}}
  },
  {
    "name": "贷后事项 → 补充纪要",
    "condition": {"material_category": "贷后事项"},
    "action": {"create_todo": {"title": "补充纪要", "priority": "LOW", "due_days": 14}}
  },
  {
    "name": "会议纪要 → 复核纪要",
    "condition": {"material_category": "会议纪要"},
    "action": {"create_todo": {"title": "复核纪要", "priority": "LOW", "due_days": 7}}
  },
  {
    "name": "风险报告 → 风险复核",
    "condition": {"material_category": "风险报告"},
    "action": {"create_todo": {"title": "风险复核", "priority": "HIGH", "due_days": 3}}
  },
  {
    "name": "贷后检查报告 → 审阅报告",
    "condition": {"material_category": "贷后检查报告"},
    "action": {"create_todo": {"title": "审阅报告", "priority": "MEDIUM", "due_days": 5}}
  }
]
```

---

## 8. UI 风格

### 8.1 主题色

| 用途 | 色号 | 备注 |
|---|---|---|
| 主色(深蓝) | `#1f4e79` | 投委会专业感 |
| 警告(橙) | `#e6a23c` | 过期待办 |
| 成功(绿) | `#67c23a` | 已完成 |
| 危险(红) | `#f56c6c` | 错误/超期 |
| 背景(浅灰) | `#f5f7fa` | 内容区背景 |
| 文字(深灰) | `#303133` | 主文字 |
| 侧边栏(深色) | `#001529` | Ant Design Pro 风 |

### 8.2 字号

- 主内容: 14px
- 辅助/标签: 12px
- 标题: 18 / 20 / 24px(三档)
- 数字大屏: 32px

### 8.3 布局

- **顶部**: 系统名 + 用户菜单
- **左侧**: 侧边栏(深色背景,200px 宽,可折叠到 64px)
- **右侧**: 内容区(浅色背景,圆角 8px 卡片)
- **表格**: Element Plus 默认,斑马纹

### 8.4 侧边栏菜单

```
┌──────────────────────────────┐
│ 投委会档案管理               │
├──────────────────────────────┤
│ 🏠 首页                       │
│ 📁 项目管理                  │
│   └─ 项目列表                 │
│   └─ 我的项目(最近)            │
│ 💬 智能问答                  │
│ ✅ 待办                      │
│   └─ 我的待办                 │
│   └─ 待办设置(admin)          │
│ ⚙️ 参数管理(admin)           │
│   └─ 字典管理                 │
│   └─ 抽取方法                 │
│   └─ 对比方法                 │
│   └─ 触发规则                 │
└──────────────────────────────┘
```

### 8.5 首页(双模)

```
模式 1: 有待办
┌─────────────────────────────┐
│ 📌 待办提醒                   │
│ ┌─────────────────────────┐ │
│ │ 🔴 [超期] 评估法律风险   │ │
│ │ 项目: PRJ-001 / 律师函  │ │
│ │ 截止: 2026-06-10 (3天前)│ │
│ │ [已办] [详情]            │ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ 🟡 [今日] 确认走账       │ │
│ │ 项目: PRJ-002 / 收款凭证 │ │
│ │ [已办] [详情]            │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘

模式 2: 没待办
┌─────────────────────────────┐
│ 💬 知识库问答                 │
│ ┌─────────────────────────┐ │
│ │ [问题输入框]              │ │
│ │ [提问]                    │ │
│ └─────────────────────────┘ │
│ 推荐问:                       │
│ - "PRJ-001 的风险点"          │
│ - "今年所有律师函"            │
└─────────────────────────────┘
```

### 8.6 兼容性

- **必须**: Chrome 100+ / Edge 100+
- **不要求**: IE / Safari / 移动端

---

## 9. 非功能性需求

### 9.1 性能

- **不要求快**: 后台慢慢分析(LLM 调用可能 10-30 秒/次)
- 单用户系统,**没有高并发诉求**
- 上传后 1-2 分钟内进入"已解析"状态可接受
- 首页加载 < 2 秒
- 知识库问答 (v1.1 修订): 单轮检索 < 3s, Agent 模式 < 30s, 前端 axios timeout = 120s

### 9.2 安全

- **审计**: 所有写操作(增删改)+ 登录/登出 + 失败登录 → audit_log
- **文件完整性**: SHA-256 校验,存到 material_version.sha256
- **原始文件**: 不存数据库,只存路径
- **解析文本**: 存到 parsed_text 字段 + 单独 .txt 备份
- **LLM 调用**: 全部 log(prompt、response、token 数、耗时)
- **JWT**: 8 小时过期,secret 从 config.json 读(>= 32 byte)
- **密码**: BCrypt 哈希(已实现)
- **限流**: 登录 5 次/分钟/IP(防爆破,见 P3-1)

### 9.3 可扩展

- **LLM Provider 抽象**: `LLMProvider` 接口,实现 GLM/OpenAI/Mock,`llm.provider` 配切换
- **抽取方法可加**: 数据库存,不改代码
- **对比方法可加**: 同上
- **触发规则可加**: admin 页面 CRUD
- **字典可加**: admin 页面 CRUD

### 9.4 数据迁移

- **本期不做**。如果未来要做,需要单独 plan。

### 9.5 浏览器

- Chrome / Edge
- **不做移动端响应式**

### 9.6 部署

- 单机 Windows Server 2012 R2
- WinSW + Caddy + MySQL 8.0.16
- 已落 M0~M2,本版本沿用

### 9.7 沿用清单(已有不动)

- 数据库: `archive_db`,MySQL 8.0.16
- 后端: Spring Boot 3.3 + JPA + Tika + 智谱 GLM
- 前端: Vue 3 + TypeScript + Element Plus + Vite + Pinia
- 部署: WinSW + Caddy
- 检索: MySQL FULLTEXT ngram(不向量化; v1.1 增 JPQL LIKE 模糊 + LLM 语义兜底)
- 鉴权: JWT + BCrypt

---

## 10. 验收标准

### 10.1 业务验收清单

每条都可在浏览器 + 数据库上**实测**:

#### 立项
- [ ] 上传立项报告 → AI 抽到客户名/项目名/金额/起投时间/服务商
- [ ] 弹窗补全字段后 → 创建项目 + 1 份材料 + 1 份版本
- [ ] 不上传报告 → 全手工填也能创建项目
- [ ] 项目 stage = INITIAL

#### 申请
- [ ] 上传申请报告 → 自动跟立项报告对比
- [ ] 立项的"待落实问题"逐个显示 ✓/✗ + 引用
- [ ] 全部"已落实"才能进入贷后
- [ ] 项目 stage = APPLY

#### 贷后
- [ ] 收款凭证上传 → 待办"确认走账"出现 + remaining_amount += amount
- [ ] 付款凭证上传 → 待办"确认付款"出现 + remaining_amount -= amount
- [ ] 律师函上传 → 待办"评估法律风险"(高优,7 天后)
- [ ] 法院传票上传 → 待办"应诉准备"(高优,3 天后)
- [ ] 贷后议案上传 → 待办"重评估议案"
- [ ] 会议纪要上传 → 待办"复核纪要"
- [ ] 风险报告上传 → 待办"风险复核"
- [ ] LLM 抽时点 → 自动转待办

#### 待办
- [ ] 首页: 有待办显示列表 / 没待办显示问答
- [ ] 过期变红 + "已办" 按钮可清除

#### 抽取方法
- [ ] admin 加 1 个新方法 → 立刻对新上传的材料生效
- [ ] 预置方法不允许删,允许改 prompt

#### 触发规则
- [ ] admin 在项目级加 1 条规则 → 立刻对新上传材料生效

#### 字典
- [ ] admin 在字典加"项目分类-不动产类" → 创建项目下拉出现

#### 审计
- [ ] 任意写操作在 audit_log 有记录(before/after JSON)

### 10.2 技术验收

- [ ] 沿用 M0~M2 已有模块,**零回归**
- [ ] 后端 `mvn compile -DskipTests` 通过
- [ ] 前端 `npm run build` 通过
- [ ] 数据库 v2-schema.sql 一次性建表 + 索引
- [ ] LLM Provider 切换测试: glm → openai → mock,功能不变
- [ ] 部署: 1 条命令启动后端,1 条命令启动前端,1 条命令启动 MySQL

### 10.3 性能验收

- [ ] 首页加载 < 2 秒(100 个项目规模)
- [ ] 1 个材料上传 → 5 分钟内完成解析 + 字段抽取 + 时点抽取 + 规则触发
- [ ] 知识库问答 10 万字库 < 3 秒返回 (v1.1 修订: 单轮检索 < 3s, Agent 模式 < 30s)

---

## 11. 不在范围内(明确排除)

- 移动端
- 数据导出(Excel / PDF 报告)
- 历史数据迁移
- 多语言(只中文)
- 高可用 / 主备
- 第三方系统对接(API out)
- 邮件/短信通知(只站内)
- 工作流引擎(签批/驳回,本期不做)
- 投委会会议管理(议程、投票、决议原文归档除外)

---

## 12. 后续版本规划(预留,本期不做)

- v3: 移动端响应式
- v3: 数据导出 + BI 报表
- v3: 与 OA 系统的 API 对接(归档完成后回调)
- v3: 邮件 / 钉钉通知
- v3: 工作流引擎(签批流)

---

*本文档由 Mavis 与项目方经过 4 轮业务访谈整理,作为 v1 基线。任何 v1.1+ 改动需要走变更流程,记录到 CHANGELOG。*


---

## 13. v1.1 业务规则细化与补充 (Mavis 拓展)

> 本章节为 v1.1 草稿补充内容, 含 3 部分:
> - §13.1 业务规则细化 (10 处, 把模糊条款变具体)
> - §13.2 隐含业务规则 (6 大类, Mavis 推理补)
> - §13.3 业务方没说但应该有的功能 (6-8 项, Mavis 拓展)
### 13.1 业务规则细化 (10 处)

#### 13.1.1 置信度 3 级体系 (替换原 §5.8.3 "0.6 阈值")

原 §5.8.3 用 1 个阈值 (0.6) 二元判定, 不够精细. 改为 3 级体系:

| 置信度区间 | 行为 | UI 标识 |
|---|---|---|
| **>= 0.85** | 自动入库, status=confirmed, 不需确认 | 无标识 |
| **0.60 - 0.84** | 自动入库, status=confirmed, **但标 "AI 推测" 灰色徽章** | 灰底 "AI 推测" |
| **< 0.60** | 不入库, status=pending_review, 标 "待人工确认" 红色徽章 | 红底 "待确认" |

**业务意义**: 投委会业务对准确性要求高, 1 个阈值过于严苛, 0.6-0.85 的"AI 推测"既保留信息又提醒用户复核.

#### 13.1.2 隐式项目切换 5 级判定 (替换原 §5.6.7.4 "0.95 阈值")

原 §5.6.7.4 用 1 个阈值 0.95, 业务边界不清. 改为 5 级判定:

| 条件 | 行为 | 业务含义 |
|---|---|---|
| 新 find_project 命中 locked 同 projectCode, conf >= 0.7 | 保持锁定 | 同一项目, 上下文延续 |
| 新 find_project 命中 locked 同 projectCode, conf 0.5-0.7 | 保持锁定, **提示 "可能是同项目, 请确认"** | 模糊, 让用户拍 |
| 新 find_project 命中 locked 同 projectCode, conf < 0.5 | 保持锁定, **自动 inject 到 LLM: "用户问的项目可能不是当前锁定项目, 请重新确认"** | LLM 自行重试 find_project |
| 新 find_project 命中**不同** projectCode, conf >= 0.95 | **自动切换锁定** | 明确指新项目, 切换 |
| 新 find_project 命中**不同** projectCode, conf 0.7-0.95 | 保持锁定, **反问弹窗 "是切到 X 项目吗?"** | 模糊, 让用户拍 |
| 都不命中 | 保持锁定 | 没法判断 |

#### 13.1.3 决议变更业务规则

原 §5.11.5.5 决议 5 选 1, 但没说"通过后能否撤销". 补充:

| 当前状态 | 允许变更 | 业务校验 |
|---|---|---|
| **草稿** (未开过投委会) | 是 | 无, 投委会可改决议 |
| **已开投委会 (任何决议)** | 否 (议案归档) | 投委会决议不能事后改, **走"复议"流程 = 新建一个议案** |
| **附条件通过** | 条件满足后状态自动改 | **新业务**: proposal.condition_status 字段 (pending / met / unmet) |

**新业务字段** (proposal 增 2 字段):
- `condition_text` TEXT - 附条件内容 (仅 decision=附条件通过 时填)
- `condition_status` VARCHAR(16) - pending / met / unmet (默认 pending)
- `condition_met_at` DATETIME - 条件满足时间 (LLM 自动识别 / 人工标记)

**UI 提醒**: 项目详情页"议案" tab 显示附条件议案的条件满足状态 (红/黄/绿).

#### 13.1.4 投委会编号的"预留 / 撤销 / 改系列" 业务规则

原 §5.11.3.3 只说"生成", 但没说"预留 / 撤销". 补充:

**编号预留** (创建项目时未确认, 用户推迟):
- 状态: proposal.status=draft_reserved
- 编号: 系统生成后写到 proposal.code, 但 **不增加 series.current_seq**
- 超时: 24h 未确认 → 自动释放 (status=released, seq 不变)

**编号撤销** (用户主动撤销):
- 状态: proposal.status=revoked
- 编号: 保留 (审计需要), 但加 `.revoked` 后缀 (e.g. `tx26003.revoked`)
- 序列: **当前 seq 不回退** (避免和后续编号冲突, 业务可接受)

**改系列** (用户错选系列):
- 仅在 status=draft_reserved 时允许
- 改后: 旧编号释放, 新编号按新 series 生成, 新增 proposal.code
- 业务方: 不能改已确认的议案系列 (业务禁止)

#### 13.1.5 网络查 API 字典 (替换原 §5.10.3 "1-2 个 API")

原 §5.10.3 太粗, 改为可配字典 + 4 候选:

| API | 类型 | 优先级 | 适用 |
|---|---|---|---|
| **百度百科** | 商业 API | 1 (默认) | 中文金融术语, 商业可用 |
| **维基百科** | 公开 | 2 | 权威, 部分内容墙, 兜底 |
| **金融百科** (业务方自建) | 自有 | 3 | 投委会业务专有 (待业务方提供 URL) |
| **互动百科** | 公开 | 4 | 兜底 |

**流程**: 优先级 1 → 2 → 3 → 4 依次调, 首个返回非空结果用, 都空 → 提示 "网络查无结果, 请手工填".

**业务方可在"参数管理 → 网络查源"配置**: 启用 / 停用某个 API, 改优先级.

#### 13.1.6 跨项目批量工具安全白名单 (补充 §5.9.2)

原 §5.9.2 filters 没说边界. 补充安全白名单:

| 字段 | 白名单值 |
|---|---|
| `region` | 字典: 江苏/浙江/上海/广东/北京/... (业务方维护) |
| `industry` | 字典: 金融/房地产/制造业/... (业务方维护) |
| `stage` | CREATE / POST_LOAN / SETTLED |
| `fact_type` | mortgage / guarantor / settlement / milestone / risk / decision / transaction |
| `time_bucket` | year / quarter / month |

**业务校验** (BE 必做, 防 LLM 注入):
- filters 里的字段必须在白名单, 否则拒绝
- 数值字段 (amount 等) 必须有上限 (e.g. amount <= 100,000,000), 防 DOS
- result 行数必须 <= 1000, 超过截断 + 提示 "结果过多, 请缩小范围"

#### 13.1.7 关键事实事件流字段细化 (补充 §5.8.5)

原 §5.8.5 每项维度只有"业务问句 + 数据源", 缺 status / owner / due_date. 补充统一字段:

每条事件 (`project_fact_event`) 增 4 字段:
- `owner_id` BIGINT FK - 责任人 (业务方指定谁负责跟进)
- `due_date` DATE - 跟进截止日 (业务方填)
- `resolved_at` DATETIME - 处置完成时间 (已和解 / 已处置 / 已履行 时填)
- `resolution_note` TEXT - 处置备注 (业务方说明怎么处置的)

**业务视图**:
- 关键事实事件流 = 投委会的"行动项" (类似 Todo, 但 Todo 是面向用户的, 事件流是面向事实)
- UI: 项目详情页 → "待处置" 列表 (fact.status != resolved AND due_date < today)

#### 13.1.8 主页"双模"过渡动画 (补充 §5.6.8.1)

原 §5.6.8.1 只说"没待办=问答 / 有待办=列表", 但没过渡. 补充:

- **过渡时长**: 300ms (CSS transition, 不用 JS)
- **触发条件**: 待办数从 0 → 1 (新增待办) 时, 主页从"问答"平滑过渡到"待办", **保留问答入口折叠**
- **触发条件**: 待办数从 N → 0 (全部已办) 时, 主页从"待办"平滑过渡到"问答"
- **保留**: 任何状态, 顶部"问点什么"按钮始终可见 (避免动画丢入口)

#### 13.1.9 LLM 抽字段失败的兜底 (补充 §5.11.4.2)

原 §5.11.4.2 只说"抽失败 → 表单留空", 太粗. 补充:

| 失败类型 | 行为 | UI 提示 |
|---|---|---|
| **LLM API 调用失败** (网络/超时/500) | 弹"AI 抽取失败, 请手工填写" | 顶部红 banner + 表单全空 + "重试" 按钮 |
| **LLM 返回非 JSON** | 同上 | 同上, 日志记录 raw output |
| **JSON 字段缺失** (e.g. 没抽到 projectName) | 缺啥空啥, 不报错 | 字段标黄 "未抽取" |
| **JSON 字段值异常** (e.g. amount=-1) | 标红 "AI 抽取值异常, 请修正" | 字段标红 |
| **材料 parseStatus=failed** | 阻断项目创建 | 弹窗 "材料解析失败, 请重传或换文件" |

**业务意义**: LLM 不稳定是常态, 兜底要细致, 不能"一失败就崩".

#### 13.1.10 提案 / 议案 / 材料 / 事实 4 个实体删除策略 (补充 §2.2)

原 §2.2 没说删除. 补充:

| 实体 | 删除策略 | 原因 |
|---|---|---|
| **Project** | **软删** (status=deleted, 30 天可恢复, 之后归档) | 项目是核心, 不能物理删 |
| **Proposal** | **软删** (status=revoked, 编号保留 + .revoked 后缀) | 议案是审计对象, 编号已对外公布 |
| **Material** | **软删** (status=deleted, 30 天可恢复) + 物理删 (回收站清理) | 材料是文档, 30 天后可物理删 |
| **Fact Event** | **不可删** (仅 INSERT, 不可 UPDATE/DELETE) | 事件流 = 不可变审计 |
| **BusinessTerm** | **软删** (status=deprecated) | 术语可能复用, 软删保留 |

**业务字段** (4 个实体分别增 `status` / `deleted_at` / `deleted_by`):
- `*_entity.status` 增 `deleted` / `revoked` / `deprecated` 等枚举
- `*_entity.deleted_at` DATETIME
- `*_entity.deleted_by` BIGINT

**回收站 UI**: 主页侧边栏 "🗑 回收站" 入口, 列已删除项目/材料, 30 天内可"恢复" / "永久删除".
---

### 13.2 隐含业务规则 (6 大类, Mavis 推理)

> 这些规则业务方没明说, 但投委会档案馆业务上必然要面对. 业务方事后可拍板调整.

#### 13.2.1 撤销 / 回滚 / 反悔 业务规则

**业务场景**: 用户创建项目/议案/材料后, 想反悔.

**项目撤销** (用户在项目创建后 24h 内, 没开过投委会):
- 允许 "整项目撤销" → 项目 status=deleted, **所有材料/议案/事实/待办 全部级联软删**
- 超过 24h, 或开过投委会 → 不允许整项目撤销, **单个议案可"撤销"** (业务上"撤案")

**议案撤销** (用户登记议案后, 投委会还没开):
- 允许 → proposal.status=revoked, 编号加 .revoked 后缀
- 投委会已开 → 不允许撤销, 走"复议" (新议案)

**材料撤销** (用户上传后 24h 内):
- 允许 → material.status=deleted, 进回收站
- 超过 24h, 但没被引用 (没被 LLM 抽, 没关联到议案) → 仍允许撤销
- 超过 24h, 已被引用 → **不允许物理删, 业务上"归档"** (status=archived, 不再显示在材料列表)

**回滚** (用户编辑了项目/议案, 想回到上一版):
- 项目/议案/材料 全部支持"历史版本" (每改一次留 1 版), UI 提供"查看历史"和"回滚到此版"
- **回滚也是新事件** (event 流加一条 UPDATE), 保留所有历史

#### 13.2.2 并发编辑 业务规则

**业务场景**: 用户 A 在编辑项目 X, 用户 B 也在编辑项目 X.

**单用户系统** (本系统) → 实际不会真并发. 但 **多 tab 打开 + 切换** 可能出现.

**乐观锁** (推荐方案):
- `project` / `proposal` / `material` 增 `version` 字段 (INT, 默认 1, 每次 UPDATE + 1)
- 提交时: SQL 加 `WHERE id=? AND version=?`, 影响行数 0 → 提示 "数据已被他人修改, 请刷新"
- 业务方: 单用户系统可豁免, 但 v2 多用户时要启用

#### 13.2.3 权限细分 业务规则

原 §3.2 权限矩阵粗 (admin / user), 实际投委会业务有 5 个角色:

| 角色 | 看 | 改 | 业务 |
|---|---|---|---|
| **admin** (系统管理员) | 全部 | 全部 (含参数配置 / 用户管理) | 系统运维 |
| **项目经理** | 自己负责的项目 + 公开项目 | 自己负责的项目 | 立项/申请/贷后业务 |
| **业务部门** (法务/财务/合规) | 全部项目 (按需) | 自己职责内 (e.g. 法务能加律师函相关待办) | 配合工作 |
| **投委会委员** | 全部项目 (脱敏视图) | **只读** | 审议/表决 |
| **秘书** | 全部项目 | 登记议案 / 维护待办 | 投委会会议 |

**关键业务校验**:
- 投委会委员的"脱敏视图": 客户名/金额 显示 `***` (但可点击"申请脱敏查看"留痕)
- 议案决议 (decision 字段) **只有投委会委员能改** (登记议案时是 "草稿", 投委会开完才能改决议)

**RBAC 表设计** (新增):
- `role` (id / code / name) - 已存在 (§2.2.10 字典), 扩展 5 个角色
- `user_role` (user_id / role_id) - 多对多
- `project_member` (project_id / user_id / role_in_project) - 项目级角色 (项目经理 / 业务对接人)

#### 13.2.4 审计加强 业务规则

原 §9.2 审计 = "写操作 + 登录登出", 太粗. 补充:

**审计事件分类**:

| 类型 | 记录内容 | 业务 |
|---|---|---|
| **写审计** (已有) | 谁 / 何时 / 改了什么 (before/after) | 业务必做 |
| **登录登出** (已有) | 谁 / 何时 / IP | 安全必做 |
| **敏感查看审计** (新增) | 谁 / 何时 / 看了什么敏感数据 (e.g. 投委会委员脱敏查看的金额) | 合规必做 |
| **数据导出审计** (新增) | 谁 / 何时 / 导出了什么 / 几条 | 合规必做 |
| **LLM 调用审计** (已有 §5.1) | 谁 / 何时 / prompt / response / token | AI 透明必做 |

**新审计表** `audit_log` 增 type 字段 (WRITE / LOGIN / SENSITIVE_VIEW / EXPORT / LLM).

**审计查询 UI**: admin 页面 "审计日志", 支持按时间 / 用户 / 类型 / 对象 筛选.

#### 13.2.5 数据生命周期 业务规则

原 §2 没提数据保留期. 补充:

**材料物理删除规则** (与 §13.1.10 软删配套):

| 阶段 | 行为 | 周期 |
|---|---|---|
| 软删 (status=deleted) | 在回收站, 可恢复 | 30 天 |
| 物理删 (回收站清理) | 文件 + parsed_text 物理删, DB 记录保留 (审计) | 30 天后自动 |
| 归档 (status=archived) | 移到冷存储, DB 仍可查 | 项目结清后 1 年 |
| 长期归档 | 离线备份, DB 不在线查 | 5 年 |
| **永久删除** | 不允许 (业务必保留审计) | - |

**业务方**: 数据保留期可能受合规约束 (e.g. 金融档案 5-10 年), 需法务定具体年数.

#### 13.2.6 失败兜底 全景图 (补充到 §13.1.9 之上)

所有关键路径的失败兜底一览:

| 路径 | 失败类型 | 兜底行为 |
|---|---|---|
| **项目创建** | 材料 parseStatus=failed | 阻断, 弹窗 "材料解析失败, 请重传" |
| **项目创建** | LLM 抽字段失败 | 表单留空, 顶部 banner 提示 + "重试" 按钮 |
| **项目创建** | 编号生成失败 (DB 锁超时) | 重试 3 次, 仍失败 → 弹窗 "系统繁忙, 请稍后再试" |
| **议案登记** | 编号已存在 (唯一索引冲突) | 重试 1 次, 仍冲突 → 弹窗 |
| **材料上传** | Tika 解析失败 | 标 parseStatus=failed, 业务方可重传 |
| **材料上传** | 文件 SHA-256 已存在 (重复) | 提示 "文件已存在, 版本号 +1" |
| **LLM 调用** | API 500/timeout | 重试 1 次 (不同 prompt), 仍失败 → 业务降级 (走旧规则 / 标待人工) |
| **LLM 调用** | API key 失效 | 紧急报警 + 业务降级 + 通知 admin |
| **SSE 流式** | 客户端断开 | 后端停 LLM 调用 (cancel future), 释放资源 |
| **SSE 流式** | 用户点 "停止" | 同上, < 1s 内中断 |

**新业务**: 失败兜底日志统一进 `failure_log` 表 (供 admin 查).
---

### 13.3 业务方没说但应该有的功能 (6-8 项, Mavis 拓展)

> 投委会档案馆日常业务必然用到的功能, 业务方没主动提, 但漏了会影响日常使用.

#### 13.3.1 项目看板 (v1.1 P0)

**业务场景**: 投委会秘书/项目经理 想"扫一眼"所有项目状态, 不用一个个点开.

**功能**:
- 主页 "🏠 首页" 增 "项目看板" 子页面
- 视图: 表格 / 卡片 / 看板 (按阶段分组) 3 选 1
- 筛选: 地区 / 状态 / 风险等级 / 阶段 / 客户名 / 时间段 / 投委会编号
- 排序: 金额 / 起投时间 / 累计议案数 / 风险等级
- 列: 项目编号 / 名称 / 客户 / 阶段 / 累计议案数 / 风险等级 / 剩余金额 / 待办数 / 最后更新时间

**数据源**: project / proposal / project_fact_event (聚合) / todo (聚合).

**业务价值**: 替代手工 Excel 跟踪, 投委会日常管理必备.

#### 13.3.2 站内通知中心 (v1.1 P0)

**业务场景**: 待办 / 议案通知 / 待确认事实 / 失败报警 → 用户应"主动收到提醒", 不能光看首页.

**功能**:
- 顶部 "🔔 铃铛" 图标, 未读数红点
- 弹窗: 通知列表 (按时间倒序), 类型分类 (待办 / 议案 / 事实 / 系统)
- 已读 / 未读 / 全部已读 操作
- 通知来源:
  - **待办** (新待办 + 即将到期 + 已过期)
  - **议案** (我被指定为责任人 + 我登记的议案有更新)
  - **事实** (我负责的项目有新待确认事实)
  - **系统** (LLM 抽失败 / 解析失败 / 备份完成)
- 业务方暂不接入邮件/钉钉, 站内通知必做

**数据模型** (新增): `notification` (id / user_id / type / title / content / link / read / created_at).

#### 13.3.3 数据导出 (v1.1 P0)

**业务场景**: 投委会秘书/项目经理 要给领导/外部 汇报, 需要 PDF/Excel.

**功能**:
- 项目详情页 → "导出" 按钮
- 格式: PDF (单项目报告) / Excel (材料清单/事实清单/议案清单)
- PDF 内容: 项目基础信息 + 关键事实清单 + 议案时间线 + 风险信号 + 待办
- Excel: 列表导出, 字段可选, 行数无限制 (业务方接受大文件)
- 导出日志: 审计 §13.2.4 (谁 / 何时 / 导了什么)

**技术提示**: PDF 生成用开源 (iText / OpenPDF), Excel 用 Apache POI. 不引入新服务.

#### 13.3.4 附件预览 (v1.1 P0)

**业务场景**: 用户在项目详情页想"直接看"材料, 不下载.

**功能**:
- 材料列表 → 点文件名 → 浏览器内嵌预览
- 支持格式: PDF / Word (doc/docx) / 图片 (jpg/png) / 文本 (txt)
- 不支持格式 (e.g. CAD) → 显示 "下载" 按钮
- 预览 + LLM 抽取的章节高亮联动 (PDF 章节标黄)

**技术提示**: PDF 用 pdf.js (前端), Word 用 mammoth.js (前端), 文本直接显示. 后端只返回文件流.

#### 13.3.5 关键事实变更对比视图 (v1.1 P0)

**业务场景**: 用户问"抵押物 A 怎么从"在押"变"已处置"的?", 要看"前后差异".

**功能**:
- 关键事实时间线 → 点单条 UPDATE 事件 → 弹窗 "变更对比"
- 左右对比: before / after 字段 diff (JSON tree)
- 证据引用: evidence_snippet 原文 quote + 链接到 evidence_material_id
- 业务方: 投委会审议"风险处置"时高频使用

**业务价值**: 把"事件流"从"日志"升级成"可视化的变更史".

#### 13.3.6 业务术语"中英对照" (v1.1 P1)

**业务场景**: 投委会业务有专业术语, 部分委员/秘书 看英文资料时需要对照.

**功能**:
- `business_term` 表增 `english_name` VARCHAR(128) 字段 (可选)
- UI: 术语详情页 显示 中 / 英 / 别名 / 定义
- 智能问答: 用户问英文术语 → 自动查表返回中文定义

**业务方不要求做 v1.1**, 但**留个字段**以备未来 (v2 再说完整多语言).

#### 13.3.7 旧系统 Excel 导入接口 (v1.1 P1, 接口预留)

**业务场景**: 业务方历史数据在 Excel, 业务方说"不做数据迁移", 但**留个接口**未来要.

**功能**:
- admin 页面 "数据导入" 入口 (v1.1 灰度, 业务方手动触发)
- 支持导入: 项目 / 材料 / 议案 / 关键事实 (4 类, 各自 Excel 模板)
- 模板下载: admin 页提供 .xlsx 下载
- 导入校验: 字段必填 / 类型 / 唯一索引冲突 (业务方负责清洗)
- 导入审计: 跟 §13.2.4 一致, 记录 "导入 X 条, 失败 Y 条"

**v1.1 实施**: 只做**接口 + 模板**, 不做实际导入流程 (业务方没要求).

#### 13.3.8 数据脱敏视图 (v1.1 P0, 配合 §13.2.3 委员只读)

**业务场景**: 投委会委员审议时, 客户名/金额 应脱敏, 避免利益冲突.

**功能**:
- 投委会委员登录后, 看到的是"脱敏视图":
  - 客户名: `张**` / `张XX有限公司` (隐 1 字 + 隐公司后缀)
  - 金额: `***万` (只显示数量级, 不显示具体数)
  - 关联方: 全部脱敏
  - 决策类字段: 正常显示 (投委会需要看)
- 委员可申请 "脱敏查看" → 留痕审计 (§13.2.4 敏感查看)
- 委员 "脱敏查看" 时, admin 立即收到通知 (§13.3.2)

**数据模型**: `user.sensitive_view_enabled` BOOLEAN - 是否允许查看敏感数据 (默认 false, 仅 admin = true).

**业务价值**: 投委会合规核心需求, 委员审议时利益冲突防控.