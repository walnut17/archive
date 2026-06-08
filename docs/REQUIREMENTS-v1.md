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
- **知识库问答**: 全文检索(基于 MySQL FULLTEXT,不向量化)
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
- 检索: MySQL FULLTEXT ngram(不向量化)
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
- [ ] 知识库问答 10 万字库 < 3 秒返回

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
