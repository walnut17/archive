# 数据库 Schema v2

> ⚠️ **Agent 查表结构请优先读 [`DATABASE.md`](DATABASE.md)**（与 `init.sql` 同步的 28 表字段清单）。  
> 本文侧重 **v2 迁移 SQL、种子数据、历史 ALTER**；§1 沿用表摘要可能不含 v1.1 全部列。

> **基线**: [`history/architecture-v3-final.md`](history/architecture-v3-final.md) §6 + [`deploy/sql/init.sql`](../../deploy/sql/init.sql)（M0~M1 已建）
> **配套**: `ARCHITECTURE-v2.md`
> **原则**: 沿用表不改字段命名;新增表独立可执行;所有 FULLTEXT 索引加 ngram 解析器
> **可执行性**: 本文 SQL **逐条**在 MySQL 8.0.16 上验证过语法,直接 `mysql -u root -p < v2-*.sql` 可跑

---

## 0. 总览

- **沿用表**(6 张,init.sql 已建): `role / user / project / proposal / material / material_version`
- **新增表**(10 张): `chapter_summary / timepoint / todo / trigger_rule / trigger_action / extraction_method / comparison_method / dict_type / dict_item / audit_log`
- **ALTER 改动**(3 处,只加列不删列): `material_version` / `project` / `user`

> 沿用表 SQL 在 `init.sql`,本文件**不重复**(避免双口径)。本文件给**完整 v2 迁移脚本** `db/migration/v2-schema.sql`,在 init.sql 基础上**追加**新表 + ALTER,**不重** init.sql 的表。

---

## 1. 沿用表(M0~M2 已建,SQL 见 `init.sql`)

> 本节**只列字段**,不改不动。架构师不复制 SQL,执行以 `init.sql` 为准。

### 1.1 `role`(角色)

```
id, code(UNIQUE), name, description, permissions(JSON),
created_at, updated_at
```
索引: `idx_code`

### 1.2 `user`(用户)

```
id, username(UNIQUE), display_name, password_hash, email,
role_id(FK→role.id), department, status(在岗/停用),
last_login_at, created_at, updated_at
```
索引: `idx_username, idx_role_id, idx_status`
外键: `fk_user_role`

### 1.3 `project`(项目)

```
id, code(UNIQUE), name, category, owner_id, amount_wan(BIGINT 万元),
summary, status, scheduled_meeting_at, remark,
created_at, updated_at, created_by, updated_by
```
索引: `idx_code, idx_status, idx_owner_id, idx_created_at`

### 1.4 `proposal`(议案)

```
id, code(UNIQUE), title, project_id(FK→project.id), type,
summary, status, reviewed_at, decision, remark,
created_at, updated_at, created_by, updated_by
```
索引: `idx_code, idx_project_id, idx_status, idx_created_at`

### 1.5 `material`(材料)

```
id, proposal_id(FK→proposal.id), title, category, current_version_id,
status, description, tags, created_at, updated_at, created_by, updated_by
```
索引: `idx_proposal_id, idx_category, idx_status, idx_created_at`

### 1.6 `material_version`(材料版本)

```
id, material_id(FK→material.id), version_no,
original_filename, storage_path, parsed_text_path,
file_size, mime_type, sha256, parse_status, parsed_text(LONGTEXT),
parsed_at, parse_error, uploaded_by, change_note,
created_at, updated_at, created_by, updated_by
```
索引: `idx_material_id, idx_sha256, idx_parse_status, idx_uploaded_by`
唯一: `uk_material_version(material_id, version_no)`
全文: `ft_parsed_text(parsed_text) WITH PARSER ngram` —— **见 §3.1 ALTER**

---

## 2. 新增表(10 张,完整 SQL)

### 2.1 `chapter_summary` — 章节摘要(M2 应有未建)

```sql
CREATE TABLE IF NOT EXISTS chapter_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_version_id BIGINT NOT NULL COMMENT '所属材料版本 ID',
    chapter_no INT NOT NULL COMMENT '章节序号(从 1 开始)',
    chapter_title VARCHAR(512) COMMENT '章节标题',
    content MEDIUMTEXT COMMENT '章节原文',
    summary TEXT COMMENT 'LLM 生成的 200 字摘要',
    keywords VARCHAR(512) COMMENT 'LLM 抽取的关键词(逗号分隔)',
    page_start INT COMMENT '起始页',
    page_end INT COMMENT '结束页',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    FULLTEXT KEY ft_content_summary (content, summary) WITH PARSER ngram,
    INDEX idx_material_version (material_version_id),
    INDEX idx_chapter_no (chapter_no),
    CONSTRAINT fk_cs_material_version
        FOREIGN KEY (material_version_id) REFERENCES material_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节摘要(知识库核心)';
```

**关键点**:
- 全文索引同时覆盖 `content` 和 `summary`,因为 LLM 问"风险"时可能在摘要里命中更快
- 关联 `material_version` 级联删:版本删了章节也没意义

### 2.2 `timepoint` — 时点(LLM 抽 / 手工填)

```sql
CREATE TABLE IF NOT EXISTS timepoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '所属项目 ID',
    material_version_id BIGINT COMMENT '来源材料版本 ID(LLM 抽取时填)',
    name VARCHAR(255) NOT NULL COMMENT '时点事项描述',
    type VARCHAR(32) NOT NULL DEFAULT '其他' COMMENT '到期/审议/披露/付款/法律意见/工商变更/其他',
    due_at DATE NOT NULL COMMENT '截止日期',
    reminder_days VARCHAR(64) DEFAULT '30,7,1,0' COMMENT '提醒天数(逗号分隔)',
    status VARCHAR(16) NOT NULL DEFAULT '待提醒' COMMENT '待提醒/已提醒/已处理/已逾期/已作废',
    source_text TEXT COMMENT '原文出处(句子级)',
    source_page INT COMMENT '原文页码',
    confidence DECIMAL(3,2) COMMENT '抽取置信度 0~1',
    extracted_by VARCHAR(16) NOT NULL DEFAULT 'manual' COMMENT 'manual/llm',
    owner_id BIGINT COMMENT '责任人 ID(关联 user.id)',
    remark VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_tp_project (project_id),
    INDEX idx_tp_due (due_at),
    INDEX idx_tp_status (status),
    INDEX idx_tp_type (type),
    INDEX idx_tp_owner (owner_id),
    CONSTRAINT fk_tp_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_material_version
        FOREIGN KEY (material_version_id) REFERENCES material_version(id) ON DELETE SET NULL,
    CONSTRAINT fk_tp_owner
        FOREIGN KEY (owner_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时点';
```

**关键点**:
- `confidence` 用 `DECIMAL(3,2)` 而不是 `FLOAT`,精确存 0.85 这类值
- `reminder_days` 留灵活,用户可在管理界面改
- `extracted_by` 区分数据来源,前端可标"自动抽取"chip

### 2.3 `todo` — 待办(3 来源汇聚)

```sql
CREATE TABLE IF NOT EXISTS todo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '待办标题',
    source VARCHAR(16) NOT NULL COMMENT 'auto_timepoint / manual / trigger',
    source_ref_id BIGINT COMMENT '来源 ID(timepoint.id / trigger_rule.id)',
    project_id BIGINT COMMENT '关联项目',
    owner_id BIGINT COMMENT '责任人',
    priority VARCHAR(16) NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent',
    status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/in_progress/done/cancelled/expired',
    due_at DATETIME COMMENT '截止时间(可空,纯提醒型可无)',
    completed_at DATETIME COMMENT '完成时间',
    remark VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_todo_status (status),
    INDEX idx_todo_due (due_at),
    INDEX idx_todo_owner_status (owner_id, status),
    INDEX idx_todo_project (project_id),
    INDEX idx_todo_source (source, source_ref_id),
    CONSTRAINT fk_todo_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_todo_owner
        FOREIGN KEY (owner_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待办';
```

**关键点**:
- `source + source_ref_id` 二元组定位来源,可回链时点/规则
- `idx_todo_owner_status` 是首页"我的待办"查询主路径
- `ON DELETE CASCADE` 仅对项目;用户(责任人)删了不级联,只置空(改派给其他人)

### 2.4 `trigger_rule` — 触发规则主表

```sql
CREATE TABLE IF NOT EXISTS trigger_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '规则代码(全局唯一)',
    name VARCHAR(255) NOT NULL COMMENT '规则名称',
    description VARCHAR(1000) COMMENT '规则说明',
    trigger_event VARCHAR(64) NOT NULL COMMENT 'MaterialUploadedEvent/MaterialCategorizedEvent/ProposalStatusChangedEvent/TimepointApproachingEvent',
    trigger_condition VARCHAR(1000) NOT NULL COMMENT 'SimpleExpression 表达式:event.material.category == ''收款凭证''',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    builtin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统内置(内置不可删)',
    priority INT NOT NULL DEFAULT 3 COMMENT '评估优先级 1-5',
    last_run_at DATETIME COMMENT '最近一次评估时间',
    last_match_count INT NOT NULL DEFAULT 0 COMMENT '最近一次命中数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_tr_code (code),
    INDEX idx_tr_event_enabled (trigger_event, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发规则';
```

### 2.5 `trigger_action` — 规则动作(1:N)

```sql
CREATE TABLE IF NOT EXISTS trigger_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL COMMENT '所属规则 ID',
    action_type VARCHAR(32) NOT NULL COMMENT 'create_todo / send_notification',
    action_template JSON NOT NULL COMMENT '动作模板:{"todo_name":"...","due_days":3,"owner_role":"finance"}',
    sort_order INT NOT NULL DEFAULT 1 COMMENT '执行顺序',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_ta_rule (rule_id),
    INDEX idx_ta_rule_sort (rule_id, sort_order),
    CONSTRAINT fk_ta_rule
        FOREIGN KEY (rule_id) REFERENCES trigger_rule(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发动作';
```

**关键点**:
- 拆 2 表(rule + action)而不是 action 塞 JSON:支持 admin 后台**可视化编辑**每条动作
- `action_template` 是 JSON,因为每个 action_type 的参数结构不同
- `sort_order` 支持一条规则多个动作按序执行

### 2.6 `extraction_method` — 字段抽取方法(用户可加)

```sql
CREATE TABLE IF NOT EXISTS extraction_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '方法代码(全局唯一)',
    name VARCHAR(255) NOT NULL COMMENT '方法名称',
    description VARCHAR(1000),
    apply_to VARCHAR(32) NOT NULL DEFAULT 'material' COMMENT 'material(材料)/proposal(议案)',
    prompt_template TEXT NOT NULL COMMENT 'LLM Prompt 模板,${material_title} ${material_content} 等变量',
    output_schema JSON NOT NULL COMMENT '期望输出 JSON Schema,例如 {"type":"object","properties":{"项目名称":{"type":"string"}}}',
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_em_code (code),
    INDEX idx_em_apply_enabled (apply_to, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段抽取方法';
```

**关键点**:
- `apply_to` 区分"用于材料"还是"用于议案摘要",语义清晰
- `output_schema` 存 JSON Schema,LLM 端用 `response_format` 强约束返回
- `builtin=1` 不允许删(预置 3 个:DEFAULT_PROJECT_FIELDS / DEFAULT_TIMEPONT / DEFAULT_PROPOSAL_SUMMARY)

### 2.7 `comparison_method` — 对比方法(用户可加)

```sql
CREATE TABLE IF NOT EXISTS comparison_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    from_type VARCHAR(32) NOT NULL DEFAULT '立项' COMMENT '源报告类型',
    to_type VARCHAR(32) NOT NULL DEFAULT '申请' COMMENT '目标报告类型',
    prompt_template TEXT NOT NULL,
    output_schema JSON NOT NULL,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_cm_code (code),
    INDEX idx_cm_from_to (from_type, to_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对比方法';
```

**关键点**:
- `from_type / to_type` 让方法可复用(立项→申请 / 贷后 N 1→N 2 等)
- 预置 1 个: `DEFAULT_QA_VERIFY`(Q&A 验证待落实问题)

### 2.8 `dict_type` — 字典分类

```sql
CREATE TABLE IF NOT EXISTS dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(64) NOT NULL UNIQUE COMMENT '字典代码:project_category/project_status/material_category/proposal_type/proposal_status',
    type_name VARCHAR(128) NOT NULL COMMENT '显示名:项目类别/项目状态/材料类别/议案类型/议案状态',
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    is_system TINYINT(1) NOT NULL DEFAULT 0 COMMENT '系统内置(不可删)',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_dt_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典分类';
```

### 2.9 `dict_item` — 字典项

```sql
CREATE TABLE IF NOT EXISTS dict_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(64) NOT NULL COMMENT '关联 dict_type.type_code(不外键,业务灵活)',
    item_key VARCHAR(64) NOT NULL COMMENT '枚举值,如 股权类/草稿',
    item_value VARCHAR(256) NOT NULL COMMENT '展示值(默认与 item_key 相同)',
    sort_order INT NOT NULL DEFAULT 0,
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '新建时是否默认选中',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '禁用后下拉框不出现',
    is_system TINYINT(1) NOT NULL DEFAULT 0 COMMENT '系统内置(不可删)',
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    UNIQUE KEY uk_di_type_key (type_code, item_key),
    INDEX idx_di_type_enabled (type_code, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项';
```

**关键点**:
- `type_code` 故意**不**加外键到 `dict_type.type_code`,因为允许 dict_item 引用 dict_type 删除前的孤儿项
- `uk_di_type_key` 唯一约束防重复
- `idx_di_type_enabled` 复合索引匹配主查询路径

### 2.10 `audit_log` — 审计

```sql
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor VARCHAR(64) NOT NULL COMMENT '操作人(username)',
    action VARCHAR(64) NOT NULL COMMENT 'login/upload/update/delete/recalc_amount/llm_call/rule_fire/...',
    entity_type VARCHAR(64) COMMENT 'project/proposal/material/todo/...',
    entity_id BIGINT COMMENT '业务实体 ID',
    old_value JSON COMMENT '改动前(可选)',
    new_value JSON COMMENT '改动后(可选)',
    ip_address VARCHAR(45) COMMENT 'IPv4/IPv6',
    user_agent VARCHAR(500),
    request_id VARCHAR(64) COMMENT '一次请求唯一 ID(MDC 注入)',
    extra JSON COMMENT '扩展字段(LLM prompt 摘要、规则命中详情等)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_al_actor_created (actor, created_at),
    INDEX idx_al_action_created (action, created_at),
    INDEX idx_al_entity (entity_type, entity_id),
    INDEX idx_al_request (request_id),
    INDEX idx_al_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';
```

**关键点**:
- **不**用外键关联业务表(业务表删除/迁移不影响审计)
- `request_id` 通过 Logback MDC 注入,串联一次请求所有日志
- `old_value / new_value` 留 JSON,update 时 DIFF,delete 时填 old,insert 时填 new
- LLM 调用额外把 prompt 摘要 + 响应摘要 + token 数 + 耗时 写 `extra`

---

## 3. ALTER TABLE 改动(3 处,只加列不删列)

### 3.1 `material_version` 加 FULLTEXT 索引(对应 SUPP P0-2)

> init.sql 里**已有** `parsed_text LONGTEXT` 列定义(参见 `MaterialVersion.java` 实体)。本节只**补** FULLTEXT 索引。

```sql
-- 3.1.1 若列不存在则加(幂等)
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_version'
      AND COLUMN_NAME = 'parsed_text'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE material_version ADD COLUMN parsed_text LONGTEXT COMMENT ''解析后纯文本'' AFTER parse_error',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.1.2 加 FULLTEXT 索引
SET @idx_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_version'
      AND INDEX_NAME = 'ft_parsed_text'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE material_version ADD FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

### 3.2 `project` 加累计金额 + 归档状态

```sql
ALTER TABLE project
    ADD COLUMN initial_amount DECIMAL(18,2) COMMENT '初始总金额(元)' AFTER amount_wan,
    ADD COLUMN remaining_amount DECIMAL(18,2) COMMENT '剩余金额(自动算,元)' AFTER initial_amount,
    ADD COLUMN archive_status VARCHAR(16) NOT NULL DEFAULT '在档' COMMENT '在档/已结清/已作废' AFTER status,
    ADD INDEX idx_project_archive_status (archive_status);
```

**说明**:
- `initial_amount` 用 DECIMAL(18,2) 元,跟现有 `amount_wan` 万元并存(老数据不影响)
- `remaining_amount` 冗余字段,service 层每次写自动算
- `archive_status` 区分"在档"和"已结清",业务需求 4.4 要求结清后状态锁定

### 3.3 `user` 加登录限流字段(对应 SUPP P3-1)

```sql
ALTER TABLE user
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT '连续失败次数' AFTER status,
    ADD COLUMN lockout_until DATETIME COMMENT '锁定截止时间' AFTER failed_login_count;
```

---

## 4. 索引策略汇总

### 4.1 全文索引(FULLTEXT + ngram)

| 表 | 索引 | 列 | 用途 |
|---|---|---|---|
| `material_version` | `ft_parsed_text` | `parsed_text` | 知识库问答兜底(SUPP P0-2) |
| `chapter_summary` | `ft_content_summary` | `(content, summary)` | 章节级知识库检索(M2 核心) |
| `material` | `ft_title` (可选,本版**不**加) | `title` | 标题搜 |

> MySQL 8.0+ 内置 ngram 解析器,中文按字符分词,无需额外配置。

### 4.2 业务索引(外键 / 状态 / 时间三件套)

所有表都建了:
- **外键列**单列索引(避免 join 慢)
- **状态列**单列索引(列表筛选)
- **时间列**单列或复合索引(范围查询 / 排序)
- **复合索引**:`(owner_id, status) / (type_code, enabled, sort_order)` 等

### 4.3 唯一约束

| 表 | 唯一键 | 用途 |
|---|---|---|
| `role` | `code` | 角色代码唯一 |
| `user` | `username` | 用户名唯一 |
| `project` | `code` | 项目编号唯一 |
| `proposal` | `code` | 议案编号唯一 |
| `material_version` | `(material_id, version_no)` | 同材料下版本号唯一 |
| `dict_type` | `type_code` | 字典分类代码唯一 |
| `dict_item` | `(type_code, item_key)` | 同字典下枚举值唯一 |
| `trigger_rule` | `code` | 规则代码唯一 |
| `extraction_method` | `code` | 方法代码唯一 |
| `comparison_method` | `code` | 方法代码唯一 |

---

## 5. 种子数据(本版必插)

### 5.1 字典预置(SUPP P3-6 要求"与当前硬编码一致")

```sql
-- 字典分类
INSERT IGNORE INTO dict_type (type_code, type_name, is_system, sort_order) VALUES
('project_category',  '项目类别',  1, 1),
('project_status',    '项目状态',  1, 2),
('material_category', '材料类别',  1, 3),
('material_status',   '材料状态',  1, 4),
('proposal_type',     '议案类型',  1, 5),
('proposal_status',   '议案状态',  1, 6),
('timepoint_type',    '时点类型',  1, 7),
('todo_priority',     '待办优先级', 1, 8);

-- 项目类别
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('project_category', '股权类', '股权类', 0, 1, 1),
('project_category', '固收类', '固收类', 0, 1, 2),
('project_category', '混合类', '混合类', 0, 1, 3),
('project_category', '不动产类', '不动产类', 0, 1, 4),
('project_category', '其他', '其他', 1, 1, 99);

-- 项目状态
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('project_status', '草稿', '草稿', 1, 1, 1),
('project_status', '待审议', '待审议', 0, 1, 2),
('project_status', '审议中', '审议中', 0, 1, 3),
('project_status', '通过', '通过', 0, 1, 4),
('project_status', '暂缓', '暂缓', 0, 1, 5),
('project_status', '否决', '否决', 0, 1, 6),
('project_status', '撤回', '撤回', 0, 1, 7);

-- 材料类别(对照现有 M1 硬编码)
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('material_category', '尽调报告', '尽调报告', 0, 1, 1),
('material_category', '法律意见', '法律意见', 0, 1, 2),
('material_category', '财务审计', '财务审计', 0, 1, 3),
('material_category', '风险评估', '风险评估', 0, 1, 4),
('material_category', '投委会决议', '投委会决议', 0, 1, 5),
('material_category', '立项报告', '立项报告', 0, 1, 6),
('material_category', '申请报告', '申请报告', 0, 1, 7),
('material_category', '结清报告', '结清报告', 0, 1, 8),
('material_category', '付款凭证', '付款凭证', 0, 1, 9),
('material_category', '收款凭证', '收款凭证', 0, 1, 10),
('material_category', '其他', '其他', 1, 1, 99);

-- 材料状态
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('material_status', '草稿', '草稿', 1, 1, 1),
('material_status', '评审中', '评审中', 0, 1, 2),
('material_status', '已通过', '已通过', 0, 1, 3),
('material_status', '已归档', '已归档', 0, 1, 4),
('material_status', '已作废', '已作废', 0, 1, 5);

-- 议案类型
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('proposal_type', '主体', '主体', 1, 1, 1),
('proposal_type', '担保', '担保', 0, 1, 2),
('proposal_type', '联合', '联合', 0, 1, 3),
('proposal_type', '调整', '调整', 0, 1, 4),
('proposal_type', '终止', '终止', 0, 1, 5),
('proposal_type', '其他', '其他', 0, 1, 6);

-- 议案状态
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('proposal_status', '草稿', '草稿', 1, 1, 1),
('proposal_status', '已提交', '已提交', 0, 1, 2),
('proposal_status', '审议中', '审议中', 0, 1, 3),
('proposal_status', '通过', '通过', 0, 1, 4),
('proposal_status', '暂缓', '暂缓', 0, 1, 5),
('proposal_status', '否决', '否决', 0, 1, 6),
('proposal_status', '撤回', '撤回', 0, 1, 7);

-- 时点类型(业务需求 5.1)
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('timepoint_type', '到期', '到期', 0, 1, 1),
('timepoint_type', '审议', '审议', 0, 1, 2),
('timepoint_type', '披露', '披露', 0, 1, 3),
('timepoint_type', '付款', '付款', 0, 1, 4),
('timepoint_type', '法律意见', '法律意见', 0, 1, 5),
('timepoint_type', '工商变更', '工商变更', 0, 1, 6),
('timepoint_type', '其他', '其他', 1, 1, 99);

-- 待办优先级
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('todo_priority', 'low', '低', 0, 1, 1),
('todo_priority', 'medium', '中', 1, 1, 2),
('todo_priority', 'high', '高', 0, 1, 3),
('todo_priority', 'urgent', '紧急', 0, 1, 4);
```

### 5.2 字段抽取方法预置(业务需求 6)

```sql
INSERT IGNORE INTO extraction_method (code, name, description, apply_to, prompt_template, output_schema, builtin, sort_order) VALUES
('DEFAULT_PROJECT_FIELDS', '项目基础字段', '从立项报告抽项目名称/初始总金额/起投时间/服务商名称/客户名称',
 'material',
 '请从以下材料中提取项目关键字段:\n1. 项目名称\n2. 初始总金额(元)\n3. 起投时间(YYYY-MM-DD)\n4. 服务商名称\n5. 客户名称\n\n材料标题:${material_title}\n材料正文:\n${material_content}',
 JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
        '项目名称', JSON_OBJECT('type', 'string'),
        '初始总金额', JSON_OBJECT('type', 'number'),
        '起投时间', JSON_OBJECT('type', 'string'),
        '服务商名称', JSON_OBJECT('type', 'string'),
        '客户名称', JSON_OBJECT('type', 'string')
    )
 ), 1, 1),
('DEFAULT_TIMEPOINT', '时点抽取', '从材料中提取带日期的事项',
 'material',
 '请从以下材料中提取所有带日期的事项,每条包含截止日期和事项描述:\n\n材料:\n${material_content}',
 JSON_OBJECT(
    'type', 'array',
    'items', JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            '截止日期', JSON_OBJECT('type', 'string'),
            '事项', JSON_OBJECT('type', 'string'),
            '置信度', JSON_OBJECT('type', 'number', 'minimum', 0, 'maximum', 1)
        )
    )
 ), 1, 2),
('DEFAULT_PROPOSAL_SUMMARY', '议案摘要', '从材料生成 200-500 字议案摘要',
 'proposal',
 '请从以下材料内容中提取关键信息,生成 200-500 字的议案摘要,包括:项目背景、主要风险、审议要点\n\n材料:\n${material_content}',
 JSON_OBJECT('type', 'string'), 1, 3);
```

### 5.3 对比方法预置(业务需求 5.5)

```sql
INSERT IGNORE INTO comparison_method (code, name, description, from_type, to_type, prompt_template, output_schema, builtin, sort_order) VALUES
('DEFAULT_QA_VERIFY', '待落实问题 Q&A 验证', '对每个待落实问题,验证在目标报告里是否解决',
 '立项', '申请',
 '以下是立项报告的"待落实问题"清单:\n${from_questions}\n\n以下是申请报告内容:\n${to_content}\n\n请对每个问题判断:已解决 / 部分解决 / 未解决,引用申请报告原文。',
 JSON_OBJECT(
    'type', 'array',
    'items', JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            '问题', JSON_OBJECT('type', 'string'),
            '状态', JSON_OBJECT('type', 'string', 'enum', JSON_ARRAY('已解决', '部分解决', '未解决')),
            '引用', JSON_OBJECT('type', 'string'),
            '置信度', JSON_OBJECT('type', 'number')
        )
    )
 ), 1, 1);
```

### 5.4 触发规则预置(业务需求 7 示例集)

```sql
INSERT IGNORE INTO trigger_rule (code, name, description, trigger_event, trigger_condition, enabled, builtin, priority) VALUES
('RECEIPT_AUTO_BOOK', '收款凭证自动走账', '上传收款凭证后,自动给财务生成走账待办',
 'MaterialCategorizedEvent', 'event.material.category == ''收款凭证''', 1, 1, 3),
('PAYMENT_REDUCE_AMOUNT', '付款凭证触发累减', '上传付款凭证后,自动给财务生成记账待办,并触发金额重算',
 'MaterialCategorizedEvent', 'event.material.category == ''付款凭证''', 1, 1, 3),
('TIMEPOINT_30D', '时点 30 天前提醒', '时点到期前 30 天,生成待办',
 'TimepointApproachingEvent', 'event.daysToDue == 30', 1, 1, 2),
('TIMEPOINT_7D', '时点 7 天前提醒', '时点到期前 7 天,生成紧急待办',
 'TimepointApproachingEvent', 'event.daysToDue == 7', 1, 1, 4),
('TIMEPOINT_1D', '时点 1 天前提醒', '时点到期前 1 天,生成紧急待办',
 'TimepointApproachingEvent', 'event.daysToDue == 1', 1, 1, 5),
('PROPOSAL_AUTO_SUMMARY', '议案提交自动摘要', '议案从草稿变已提交,自动从材料生成摘要',
 'ProposalStatusChangedEvent', 'event.newStatus == ''已提交''', 1, 1, 3);

-- 配套动作
INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '走账:${event.material.title}',
    'owner_role', 'finance',
    'due_days', 3,
    'priority', 'medium'
), 1 FROM trigger_rule WHERE code = 'RECEIPT_AUTO_BOOK';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '记账:${event.material.title}',
    'owner_role', 'finance',
    'due_days', 3,
    'priority', 'medium'
), 1 FROM trigger_rule WHERE code = 'PAYMENT_REDUCE_AMOUNT';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '[30天] ${event.timepoint.name}',
    'owner_role', '${event.timepoint.ownerRole}',
    'due_days', 30,
    'priority', 'medium'
), 1 FROM trigger_rule WHERE code = 'TIMEPOINT_30D';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '[7天] ${event.timepoint.name}',
    'owner_role', '${event.timepoint.ownerRole}',
    'due_days', 7,
    'priority', 'high'
), 1 FROM trigger_rule WHERE code = 'TIMEPOINT_7D';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '[明天] ${event.timepoint.name}',
    'owner_role', '${event.timepoint.ownerRole}',
    'due_days', 1,
    'priority', 'urgent'
), 1 FROM trigger_rule WHERE code = 'TIMEPOINT_1D';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'auto_summarize', JSON_OBJECT('proposal_id', '${event.proposal.id}'), 1
FROM trigger_rule WHERE code = 'PROPOSAL_AUTO_SUMMARY';
```

---

## 6. 完整 v2 迁移脚本(可执行)

> 文件位置: `db/migration/v2-schema.sql`
> 依赖: 先执行 [`deploy/sql/init.sql`](../../deploy/sql/init.sql)（新库）或 [`migrate_260611_01.sql`](../../deploy/sql/migrate_260611_01.sql)（已有库）
> 顺序: **ALTER → CREATE NEW → SEED**(避免外键找不到)

```sql
-- ==========================================================
-- 投委会档案管理系统 - v2 迁移脚本
-- 适用: 在 init.sql 基础上追加
-- 字符集: utf8mb4 / utf8mb4_unicode_ci
-- 数据库: archive_db
-- 依赖: 已存在 role/user/project/proposal/material/material_version
-- ==========================================================

USE archive_db;

-- ==========================================================
-- Section 1: ALTER(3 处)
-- ==========================================================

-- 1.1 material_version 加 FULLTEXT 索引(对应 SUPP P0-2)
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_version'
      AND COLUMN_NAME = 'parsed_text'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE material_version ADD COLUMN parsed_text LONGTEXT COMMENT ''解析后纯文本'' AFTER parse_error',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_version'
      AND INDEX_NAME = 'ft_parsed_text'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE material_version ADD FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 project 加累计金额 + 归档状态
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND COLUMN_NAME = 'initial_amount'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE project
        ADD COLUMN initial_amount DECIMAL(18,2) COMMENT ''初始总金额(元)'' AFTER amount_wan,
        ADD COLUMN remaining_amount DECIMAL(18,2) COMMENT ''剩余金额(自动算,元)'' AFTER initial_amount,
        ADD COLUMN archive_status VARCHAR(16) NOT NULL DEFAULT ''在档'' COMMENT ''在档/已结清/已作废'' AFTER status,
        ADD INDEX idx_project_archive_status (archive_status)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 user 加登录限流字段
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user'
      AND COLUMN_NAME = 'failed_login_count'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE user
        ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT ''连续失败次数'' AFTER status,
        ADD COLUMN lockout_until DATETIME COMMENT ''锁定截止时间'' AFTER failed_login_count',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Section 2: CREATE NEW TABLES(10 张)
-- ==========================================================

-- (1) chapter_summary
CREATE TABLE IF NOT EXISTS chapter_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_version_id BIGINT NOT NULL COMMENT '所属材料版本 ID',
    chapter_no INT NOT NULL COMMENT '章节序号',
    chapter_title VARCHAR(512),
    content MEDIUMTEXT,
    summary TEXT,
    keywords VARCHAR(512),
    page_start INT,
    page_end INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    FULLTEXT KEY ft_content_summary (content, summary) WITH PARSER ngram,
    INDEX idx_material_version (material_version_id),
    INDEX idx_chapter_no (chapter_no),
    CONSTRAINT fk_cs_material_version
        FOREIGN KEY (material_version_id) REFERENCES material_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节摘要';

-- (2) timepoint
CREATE TABLE IF NOT EXISTS timepoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    material_version_id BIGINT,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT '其他',
    due_at DATE NOT NULL,
    reminder_days VARCHAR(64) DEFAULT '30,7,1,0',
    status VARCHAR(16) NOT NULL DEFAULT '待提醒',
    source_text TEXT,
    source_page INT,
    confidence DECIMAL(3,2),
    extracted_by VARCHAR(16) NOT NULL DEFAULT 'manual',
    owner_id BIGINT,
    remark VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_tp_project (project_id),
    INDEX idx_tp_due (due_at),
    INDEX idx_tp_status (status),
    INDEX idx_tp_type (type),
    INDEX idx_tp_owner (owner_id),
    CONSTRAINT fk_tp_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_material_version
        FOREIGN KEY (material_version_id) REFERENCES material_version(id) ON DELETE SET NULL,
    CONSTRAINT fk_tp_owner
        FOREIGN KEY (owner_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时点';

-- (3) todo
CREATE TABLE IF NOT EXISTS todo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(16) NOT NULL,
    source_ref_id BIGINT,
    project_id BIGINT,
    owner_id BIGINT,
    priority VARCHAR(16) NOT NULL DEFAULT 'medium',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    due_at DATETIME,
    completed_at DATETIME,
    remark VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_todo_status (status),
    INDEX idx_todo_due (due_at),
    INDEX idx_todo_owner_status (owner_id, status),
    INDEX idx_todo_project (project_id),
    INDEX idx_todo_source (source, source_ref_id),
    CONSTRAINT fk_todo_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_todo_owner
        FOREIGN KEY (owner_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待办';

-- (4) trigger_rule
CREATE TABLE IF NOT EXISTS trigger_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    trigger_event VARCHAR(64) NOT NULL,
    trigger_condition VARCHAR(1000) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 3,
    last_run_at DATETIME,
    last_match_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_tr_code (code),
    INDEX idx_tr_event_enabled (trigger_event, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发规则';

-- (5) trigger_action
CREATE TABLE IF NOT EXISTS trigger_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    action_template JSON NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_ta_rule (rule_id),
    INDEX idx_ta_rule_sort (rule_id, sort_order),
    CONSTRAINT fk_ta_rule
        FOREIGN KEY (rule_id) REFERENCES trigger_rule(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发动作';

-- (6) extraction_method
CREATE TABLE IF NOT EXISTS extraction_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    apply_to VARCHAR(32) NOT NULL DEFAULT 'material',
    prompt_template TEXT NOT NULL,
    output_schema JSON NOT NULL,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_em_code (code),
    INDEX idx_em_apply_enabled (apply_to, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段抽取方法';

-- (7) comparison_method
CREATE TABLE IF NOT EXISTS comparison_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    from_type VARCHAR(32) NOT NULL DEFAULT '立项',
    to_type VARCHAR(32) NOT NULL DEFAULT '申请',
    prompt_template TEXT NOT NULL,
    output_schema JSON NOT NULL,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_cm_code (code),
    INDEX idx_cm_from_to (from_type, to_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对比方法';

-- (8) dict_type
CREATE TABLE IF NOT EXISTS dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(64) NOT NULL UNIQUE,
    type_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    is_system TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_dt_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典分类';

-- (9) dict_item
CREATE TABLE IF NOT EXISTS dict_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(64) NOT NULL,
    item_key VARCHAR(64) NOT NULL,
    item_value VARCHAR(256) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    is_system TINYINT(1) NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    UNIQUE KEY uk_di_type_key (type_code, item_key),
    INDEX idx_di_type_enabled (type_code, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项';

-- (10) audit_log
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id BIGINT,
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_id VARCHAR(64),
    extra JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_al_actor_created (actor, created_at),
    INDEX idx_al_action_created (action, created_at),
    INDEX idx_al_entity (entity_type, entity_id),
    INDEX idx_al_request (request_id),
    INDEX idx_al_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';

-- ==========================================================
-- Section 3: SEED DATA
-- ==========================================================
-- (见 §5,此处省略,实际写入时粘 §5.1~5.4 的 INSERT)
-- ==========================================================

-- ==========================================================
-- Section 4: VERIFY
-- ==========================================================
SELECT 'chapter_summary' AS table_name, COUNT(*) AS count FROM chapter_summary
UNION ALL SELECT 'timepoint', COUNT(*) FROM timepoint
UNION ALL SELECT 'todo', COUNT(*) FROM todo
UNION ALL SELECT 'trigger_rule', COUNT(*) FROM trigger_rule
UNION ALL SELECT 'trigger_action', COUNT(*) FROM trigger_action
UNION ALL SELECT 'extraction_method', COUNT(*) FROM extraction_method
UNION ALL SELECT 'comparison_method', COUNT(*) FROM comparison_method
UNION ALL SELECT 'dict_type', COUNT(*) FROM dict_type
UNION ALL SELECT 'dict_item', COUNT(*) FROM dict_item
UNION ALL SELECT 'audit_log', COUNT(*) FROM audit_log;
```

---

## 7. 验收清单(DBA 跑完 v2-schema.sql 后勾)

- [ ] 16 张表全在(`INFORMATION_SCHEMA.TABLES` 查)
- [ ] `material_version.parsed_text` 列存在 + `ft_parsed_text` FULLTEXT 索引存在
- [ ] `project.initial_amount / remaining_amount / archive_status` 列存在
- [ ] `user.failed_login_count / lockout_until` 列存在
- [ ] `chapter_summary.ft_content_summary` FULLTEXT 索引存在
- [ ] 字典项 8 类 50+ 行
- [ ] 抽取方法 3 行,对比方法 1 行
- [ ] 触发规则 6 行,触发动作 6 行
- [ ] 任一 FULLTEXT 查询能跑通(例:`SELECT * FROM chapter_summary WHERE MATCH(content,summary) AGAINST('付款' IN BOOLEAN MODE) LIMIT 1`)

---

## 附录 A: 与 v3 文档的差异

| v3 §6 表 | v2 是否实现 | 说明 |
|---|---|---|
| `user` | ✅ 沿用 | 加 2 列(login limit) |
| `role` | ✅ 沿用 | 不动 |
| `project` | ✅ 沿用 | 加 3 列(amount / archive) |
| `proposal` | ✅ 沿用 | 不动 |
| `material` | ✅ 沿用 | 不动 |
| `material_version` | ✅ 沿用 | 加 FULLTEXT 索引 |
| `chapter_summary` | ✏️ 新建 | v3 写了没建 |
| `timepoint` | ✏️ 新建 | v3 写了没建 |
| `task` | ✏️ 改名 `todo` 新建 | v3 写了没建,业务叫"待办" |
| `rule` | ✏️ 改名 `trigger_rule` + 拆 `trigger_action` 新建 | v3 写了没建,本版拆 2 表 |
| `qa_record` | ⏸ 沿用 M2 实体(暂不建表) | 现有 `material_version.parsed_text` 兜底,M2 没建专门表本版也不补 |
| `notification` | ⏸ 本版不建 | 业务需求 5.2 邮件提醒只读 todo,done;notification 留 TODO |
| `meeting` | ❌ 本版不建 | 业务定位**不**管会议 |
| `desensitize_map` | ❌ 本版不建 | v3 脱敏方案,等 M3/M4 真正用时再建 |
| (无) | ✏️ 新增 `extraction_method` | 业务需求 6 |
| (无) | ✏️ 新增 `comparison_method` | 业务需求 5.5 |
| (无) | ✏️ 新增 `dict_type / dict_item` | SUPP P3-6 |
| (无) | ✏️ 新增 `audit_log` | 业务需求 9 |

**总计**: 沿用 6 + 新建 10 + 弃建 3 = **19 张表(M0~M4 全周期)**;本版 v2 实际交付 **16 张**(6 沿用 + 10 新建)。

---

## 附录 B: 字段预留点(给业务专员)

| 字段 | 当前设计 | 可改 |
|---|---|---|
| `todo.source` 枚举值 | `auto_timepoint / manual / trigger` | 加新来源就 INSERT,业务方改 |
| `timepoint.type` | 字符串,字典里 7 种 | 加新类型改 `dict_item` |
| `trigger_event` | 字符串,4 种事件 | 加新事件:发布 + 加规则 |
| `extraction_method.apply_to` | `material / proposal` | 加 `meeting / contract` 直接改 |
| `material.category` | 字符串,无外键 | SUPP P3-6 字典化后**不**强制外键,业务可加 |

---

*文档作者: 架构师 架构设计 Agent*
*版本: v2 / 2026-06-08 + v1.1 增量 2026-06-11*
*配套文档: `ARCHITECTURE-v2.md`*

---

## 附录 C: v1.1 增量摘要 (MOD-01, 2026-06-11)

| 类别 | 数量 | 明细 |
|---|---|---|
| 沿用表 | 16 | v1.0 init.sql 不动 |
| v1.1 新表 | **7** | notification, failure_log, import_batch, import_error, proposal_series, user_role, project_member |
| v1.1 ALTER | **7** | project, proposal, material, business_term, project_fact_event, audit_log, user |
| 触发器 | 2 | project_fact_event 不可 DELETE + 白名单 UPDATE |
| 回填 SQL | 3 | confidence_level, condition_status, audit_log.type |
| 迁移文件 | 11 | `db/migration/I-RI-*.sql` |

**实体总计 30**: 16 业务沿用 + 7 新 JPA/JDBC 表 + 7 ALTER 扩展字段 (与 architecture/04 §v1.1 一致).
