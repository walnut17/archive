-- ==========================================================
-- 投委会档案管理系统 - 数据库初始化脚本 (整合所有迁移)
-- 版本: Plan I + v1.1 MOD-01 (2026-06-11)
-- 数据库: archive_db
-- 字符集: utf8mb4 / utf8mb4_unicode_ci
-- MySQL: 8.0+ (需要 FULLTEXT + ngram parser)
--
-- 用途:
--   1. 全新部署: 直接跑这个脚本建库
--   2. 重建: DROP 旧表, 重新跑这个脚本
--
-- 包含迁移:
--   - M0~M2: 基础表 + FULLTEXT 索引 (material_version.parsed_text)
--   - Plan A~F: proposal/material/material_version 完善
--   - Plan G: llm_call_log 表
--   - Plan I: customer_name 字段 + project FULLTEXT 索引 + spring_ai_chat_memory 表
--   - v1.1 MOD-01: 13 个 I-RI-*.sql 增量 (7 表 ALTER + 7 新表 + 触发器)
--
-- 注意: 这是 DROP + CREATE 一键重建, 会丢失所有业务数据!
--       生产环境慎用, 请先备份。
-- ==========================================================

-- 1. 创建数据库(若不存在)
CREATE DATABASE IF NOT EXISTS archive_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE archive_db;

-- 1.5 删 Flyway 历史表(从头重建, 避免 Flyway 启动报错)
-- 生产没用 Flyway, 手工跑 init.sql 即可
DROP TABLE IF EXISTS flyway_schema_history;

-- ==========================================================
-- 2. 角色表
-- ==========================================================
DROP TABLE IF EXISTS role;
CREATE TABLE role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色代码:admin/pm/legal/committee/secretary/user',
    name VARCHAR(128) NOT NULL COMMENT '显示名称',
    description VARCHAR(512) COMMENT '描述',
    permissions JSON COMMENT '权限位(JSON 数组)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

-- ==========================================================
-- 3. 用户表
-- ==========================================================
DROP TABLE IF EXISTS user;
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录用户名',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密后的密码',
    email VARCHAR(128) COMMENT '邮箱',
    role_id BIGINT COMMENT '角色 ID',
    department VARCHAR(128) COMMENT '部门',
    status VARCHAR(16) NOT NULL DEFAULT '在岗' COMMENT '在岗/停用',
    last_login_at DATETIME COMMENT '最后登录时间',
    deleted_at DATETIME COMMENT '软删时间',
    deleted_by BIGINT COMMENT '软删操作者',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    sensitive_view_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许查看脱敏原始值',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_role_id (role_id),
    INDEX idx_status (status),
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- ==========================================================
-- 4. 初始数据
-- ==========================================================

-- 6 个预置角色 (v1.1 D-1: admin/pm/legal/committee/secretary + legacy user)
INSERT INTO role (code, name, description, permissions) VALUES
('admin', '系统管理员', '系统所有权限,包括用户管理、角色配置、规则编辑', JSON_ARRAY('*')),
('committee', '投委会委员', '审议、查看所有项目、查询知识库、查阅议案', JSON_ARRAY('project:read', 'proposal:read', 'qa:ask', 'task:view')),
('pm', '项目经理', '负责项目的项目经理,可创建项目、编辑材料、触发规则', JSON_ARRAY('project:write', 'material:upload', 'task:write')),
('user', '普通用户', 'v1.0 兼容普通用户,基础查询、提交流转', JSON_ARRAY('project:read', 'qa:ask')),
('legal', '法务', '法务审查权限', JSON_ARRAY('project:read', 'legal:review')),
('secretary', '投委会秘书', '议案登记与秘书事务', JSON_ARRAY('project:read', 'proposal:write', 'task:write'));

-- 1 个预置 admin 账号(密码: admin123)
-- BCrypt 哈希(强度 10):$2a$10$wjN3YFZDlu.ThmfrRe0XvOA9A1AW2TybgeKAddA/TTxTEhEGvg/Ve
-- 这是 BCrypt 编码后的 "admin123"
INSERT INTO user (username, display_name, password_hash, email, role_id, department, status, sensitive_view_enabled) VALUES
('admin', '系统管理员', '$2a$10$wjN3YFZDlu.ThmfrRe0XvOA9A1AW2TybgeKAddA/TTxTEhEGvg/Ve', 'admin@example.com', 1, '信息技术部', '在岗', 1);

-- ==========================================================
-- 5. 项目表(M1-1) - Plan I 加 customer_name 字段 + FULLTEXT 索引
-- ==========================================================
DROP TABLE IF EXISTS project;
CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '项目编号,如 PRJ-2026-001',
    name VARCHAR(256) NOT NULL COMMENT '项目名称',
    customer_name VARCHAR(256) COMMENT '客户名称 (Plan I-5: FindProjectTool 语义搜索用)',
    category VARCHAR(64) COMMENT '业务类别:股权类/固收类/混合类/其他',
    owner_id BIGINT COMMENT '项目经理 ID(关联 user.id)',
    amount_wan BIGINT COMMENT '投资金额(万元)',
    summary VARCHAR(2000) COMMENT '摘要',
    status VARCHAR(32) NOT NULL DEFAULT '草稿' COMMENT '草稿/待审议/审议中/通过/暂缓/否决/撤回',
    scheduled_meeting_at DATE COMMENT '投委会审议日期',
    remark VARCHAR(2000) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64) COMMENT '创建人',
    updated_by VARCHAR(64) COMMENT '更新人',
    deleted_at DATETIME COMMENT '软删时间',
    deleted_by BIGINT COMMENT '软删操作者',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    archive_status VARCHAR(32) DEFAULT NULL COMMENT '归档状态',
    INDEX idx_code (code),
    INDEX idx_status (status),
    INDEX idx_owner_id (owner_id),
    INDEX idx_created_at (created_at),
    INDEX idx_deleted_at (deleted_at),
    -- Plan I-5: 项目语义搜索 FULLTEXT 索引 (FindProjectTool 用)
    FULLTEXT INDEX ft_name_cust (name, customer_name) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目';

-- ==========================================================
-- 6. 议案表(M1-1)
-- ==========================================================
DROP TABLE IF EXISTS proposal;
CREATE TABLE proposal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '议案编号',
    title VARCHAR(256) NOT NULL COMMENT '议案标题',
    project_id BIGINT NOT NULL COMMENT '所属项目 ID',
    type VARCHAR(32) COMMENT '主体/担保/联合/调整/终止/其他',
    summary VARCHAR(2000) COMMENT '摘要',
    status VARCHAR(32) NOT NULL DEFAULT '草稿' COMMENT '草稿/已提交/审议中/通过/暂缓/否决/撤回',
    reviewed_at DATE COMMENT '审议日期',
    decision VARCHAR(2000) COMMENT '审议结论',
    condition_text TEXT COMMENT '附条件通过的条件描述',
    condition_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/MET/UNMET',
    condition_met_at DATETIME COMMENT '条件满足时间',
    remark VARCHAR(2000) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    reserved_at DATETIME COMMENT '编号预留时间',
    released_at DATETIME COMMENT '编号释放时间',
    deleted_at DATETIME COMMENT '软删时间',
    deleted_by BIGINT COMMENT '软删操作者',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    INDEX idx_code (code),
    INDEX idx_project_id (project_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='议案';

-- ==========================================================
-- 7. 材料表(M1-1)
-- ==========================================================
DROP TABLE IF EXISTS material;
CREATE TABLE material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    proposal_id BIGINT NOT NULL COMMENT '所属议案 ID',
    title VARCHAR(256) NOT NULL COMMENT '材料标题',
    category VARCHAR(64) COMMENT '尽调报告/法律意见/财务审计/风险评估/投委会决议/其他',
    current_version_id BIGINT COMMENT '当前生效版本 ID',
    status VARCHAR(32) NOT NULL DEFAULT '草稿' COMMENT '草稿/评审中/已通过/已归档/已作废',
    description VARCHAR(1000) COMMENT '说明',
    tags VARCHAR(500) COMMENT '标签(逗号分隔)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    deleted_at DATETIME COMMENT '软删时间',
    deleted_by BIGINT COMMENT '软删操作者',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    archived_at DATETIME COMMENT '归档时间',
    INDEX idx_proposal_id (proposal_id),
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料';

-- ==========================================================
-- 8. 材料版本表(M1-1) - 含 FULLTEXT 索引(M2 知识库问答)
-- ==========================================================
DROP TABLE IF EXISTS material_version;
CREATE TABLE material_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_id BIGINT NOT NULL COMMENT '所属材料 ID',
    version_no INT NOT NULL COMMENT '版本号(从 1 开始)',
    original_filename VARCHAR(256) NOT NULL COMMENT '原始文件名',
    storage_path VARCHAR(1024) NOT NULL COMMENT '存储路径(相对 file-root)',
    parsed_text_path VARCHAR(1024) COMMENT 'Tika 解析后文本路径(相对 parsed-root)',
    file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
    mime_type VARCHAR(128) COMMENT 'MIME 类型',
    sha256 VARCHAR(64) COMMENT 'SHA-256 校验和',
    parse_status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
    parsed_at DATETIME COMMENT '解析完成时间',
    parse_error VARCHAR(2000) COMMENT '解析错误信息',
    parsed_text LONGTEXT COMMENT '解析后的纯文本内容(M2 知识库问答 FULLTEXT 索引字段)',
    uploaded_by VARCHAR(64) COMMENT '上传人',
    change_note VARCHAR(1000) COMMENT '版本说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_material_id (material_id),
    INDEX idx_sha256 (sha256),
    INDEX idx_parse_status (parse_status),
    INDEX idx_uploaded_by (uploaded_by),
    UNIQUE KEY uk_material_version (material_id, version_no),
    FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料版本';

-- ==========================================================
-- 9. Plan G: LLM 调用日志表 (埋点统计)
-- ==========================================================
DROP TABLE IF EXISTS llm_call_log;
CREATE TABLE llm_call_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) COMMENT '调用人',
    scenario VARCHAR(32) NOT NULL COMMENT '场景:QA/RERANK/EXTRACT/SUMMARIZE 等',
    model VARCHAR(64) NOT NULL COMMENT '模型名',
    duration_ms INT NOT NULL COMMENT '耗时(毫秒)',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCESS / FAILED',
    error_message VARCHAR(500) COMMENT '错误信息',
    prompt_tokens INT COMMENT '提示 token 数(本期不统计, 留 NULL)',
    completion_tokens INT COMMENT '完成 token 数(本期不统计, 留 NULL)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_scenario (scenario),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 调用日志';

-- ==========================================================
-- 10. Plan I-13: 多轮对话记忆表 (Spring AI 1.1)
-- Spring AI 1.1 公开 API: JdbcChatMemoryRepository 默认表名是 spring_ai_chat_memory
-- 字段名必须严格匹配 Spring AI 1.1 源码 SQL 模板
-- ==========================================================
DROP TABLE IF EXISTS spring_ai_chat_memory;
CREATE TABLE spring_ai_chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话 ID',
    content TEXT NOT NULL COMMENT '消息内容',
    type VARCHAR(16) NOT NULL COMMENT 'user / assistant / system / tool',
    `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_timestamp (conversation_id, `timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 多轮对话记忆 (Spring AI 1.1 MessageChatMemoryAdvisor)';

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

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    type VARCHAR(32) COMMENT 'WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM',
    entity_type VARCHAR(64),
    entity_subtype VARCHAR(32) COMMENT '实体子类型',
    entity_id BIGINT,
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_id VARCHAR(64),
    extra JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME COMMENT '软删时间',
    deleted_by BIGINT COMMENT '软删操作者',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    INDEX idx_al_actor_created (actor, created_at),
    INDEX idx_al_action_created (action, created_at),
    INDEX idx_al_entity (entity_type, entity_id),
    INDEX idx_al_request (request_id),
    INDEX idx_al_created (created_at),
    INDEX idx_type (type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';

-- ==========================================================
-- 11. v1.1 MOD-01: 新增表 + 事实流 + RBAC 关联
-- ==========================================================
DROP TABLE IF EXISTS import_error;
DROP TABLE IF EXISTS import_batch;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS failure_log;
DROP TABLE IF EXISTS project_member;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS project_fact_event;
DROP TABLE IF EXISTS project_fact;
DROP TABLE IF EXISTS business_term;
DROP TABLE IF EXISTS proposal_series;

CREATE TABLE proposal_series (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL COMMENT '系列编码, 如 tx/xc',
    prefix VARCHAR(32) COMMENT '编号前缀',
    current_seq INT NOT NULL DEFAULT 0 COMMENT '当前自增序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='议案编号系列';

CREATE TABLE project_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL COMMENT 'mortgage/guarantor/settlement/milestone/risk/decision/transaction',
    fact_value TEXT,
    confidence DECIMAL(3,2) COMMENT 'LLM 置信度 0-1',
    confidence_level VARCHAR(16) COMMENT 'CONFIRMED/AI_INFERRED/PENDING_REVIEW',
    evidence_material_id BIGINT COMMENT '证据材料 ID',
    evidence_snippet TEXT COMMENT '证据原文摘录',
    status VARCHAR(32) DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pf_project_type (project_id, fact_type),
    CONSTRAINT fk_pf_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目关键事实';

CREATE TABLE project_fact_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'INSERT/UPDATE/DELETE/ROLLBACK',
    fact_value TEXT,
    evidence TEXT,
    confidence DECIMAL(3,2),
    confidence_level VARCHAR(16) COMMENT 'CONFIRMED/AI_INFERRED/PENDING_REVIEW',
    owner_id BIGINT COMMENT '责任人 user_id',
    due_date DATE COMMENT '跟进截止日',
    resolved_at DATETIME COMMENT '处置完成时间',
    resolution_note TEXT COMMENT '处置备注',
    created_by BIGINT COMMENT '操作者 user_id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME COMMENT '软删时间(逻辑标记, 仍不可物理删)',
    deleted_by BIGINT COMMENT '软删操作者',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    INDEX idx_pfe_project_type (project_id, fact_type),
    INDEX idx_owner_due (owner_id, due_date),
    CONSTRAINT fk_pfe_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键事实事件流(INSERT-only)';

CREATE TABLE business_term (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    english_name VARCHAR(128) COMMENT '英文名称(可选)',
    aliases VARCHAR(512),
    category VARCHAR(64),
    definition TEXT,
    standard_definition TEXT,
    source_url VARCHAR(512),
    data_mapping JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'draft' COMMENT 'draft/pending_review/active/deprecated',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME COMMENT '软删时间',
    deleted_by BIGINT COMMENT '软删操作者',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    INDEX idx_bt_name (name),
    INDEX idx_bt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务术语字典';

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色多对多';

CREATE TABLE project_member (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_in_project VARCHAR(64) NOT NULL DEFAULT 'member' COMMENT 'member/owner/legal',
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员';

CREATE TABLE failure_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    path VARCHAR(512) NOT NULL COMMENT '失败路径/端点',
    failure_type VARCHAR(64) NOT NULL COMMENT '失败类型枚举',
    error_msg TEXT COMMENT '错误信息',
    stack_trace TEXT COMMENT '堆栈',
    resolved TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已解决',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    resolved_at DATETIME COMMENT '解决时间',
    INDEX idx_path_resolved (path, resolved, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失败兜底日志';

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL COMMENT 'TODO/FACT/PROPOSAL/SYSTEM',
    title VARCHAR(256) NOT NULL,
    content TEXT,
    link VARCHAR(512) COMMENT '跳转链接',
    `read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, `read`, created_at),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知';

CREATE TABLE import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(64) NOT NULL COMMENT '导入类型: project/proposal/...',
    total INT NOT NULL DEFAULT 0,
    success INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    created_by BIGINT COMMENT '操作者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_created (type, created_at),
    CONSTRAINT fk_ib_user FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据导入批次';

CREATE TABLE import_error (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    row_num INT NOT NULL COMMENT 'Excel 行号',
    column_name VARCHAR(128) COMMENT '列名',
    error_msg VARCHAR(1000) NOT NULL,
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_ie_batch FOREIGN KEY (batch_id) REFERENCES import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入错误明细';

DROP TRIGGER IF EXISTS trg_fact_event_immutable;
DROP TRIGGER IF EXISTS trg_fact_event_no_delete;

DELIMITER $$
CREATE TRIGGER trg_fact_event_immutable
BEFORE UPDATE ON project_fact_event
FOR EACH ROW
BEGIN
  IF (NEW.event_type <> OLD.event_type
      OR NEW.fact_value <> OLD.fact_value
      OR (NEW.evidence <> OLD.evidence OR (NEW.evidence IS NULL) <> (OLD.evidence IS NULL))
      OR (NEW.confidence <> OLD.confidence OR (NEW.confidence IS NULL) <> (OLD.confidence IS NULL))
      OR NEW.project_id <> OLD.project_id
      OR NEW.fact_type <> OLD.fact_type
      OR NEW.created_at <> OLD.created_at
      OR (NEW.created_by <> OLD.created_by OR (NEW.created_by IS NULL) <> (OLD.created_by IS NULL))
      OR (NEW.confidence_level <> OLD.confidence_level OR (NEW.confidence_level IS NULL) <> (OLD.confidence_level IS NULL))) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'project_fact_event is INSERT-only (only owner_id/due_date/resolved_at/resolution_note may be updated)';
  END IF;
END$$

CREATE TRIGGER trg_fact_event_no_delete
BEFORE DELETE ON project_fact_event
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'project_fact_event is INSERT-only (DELETE forbidden)';
END$$
DELIMITER ;

-- ==========================================================
-- 12. 迁移检查
-- ==========================================================
SELECT '角色表 role' AS table_name, COUNT(*) AS count FROM role
UNION ALL
SELECT '用户表 user', COUNT(*) FROM user
UNION ALL
SELECT '项目表 project', COUNT(*) FROM project
UNION ALL
SELECT '议案表 proposal', COUNT(*) FROM proposal
UNION ALL
SELECT '材料表 material', COUNT(*) FROM material
UNION ALL
SELECT '材料版本表 material_version', COUNT(*) FROM material_version
UNION ALL
SELECT '章节摘要表 chapter_summary', COUNT(*) FROM chapter_summary
UNION ALL
SELECT '时点表 timepoint', COUNT(*) FROM timepoint
UNION ALL
SELECT '待办表 todo', COUNT(*) FROM todo
UNION ALL
SELECT '触发规则表 trigger_rule', COUNT(*) FROM trigger_rule
UNION ALL
SELECT '触发动作表 trigger_action', COUNT(*) FROM trigger_action
UNION ALL
SELECT '抽取方法表 extraction_method', COUNT(*) FROM extraction_method
UNION ALL
SELECT '对比方法表 comparison_method', COUNT(*) FROM comparison_method
UNION ALL
SELECT '字典类型表 dict_type', COUNT(*) FROM dict_type
UNION ALL
SELECT '字典项表 dict_item', COUNT(*) FROM dict_item
UNION ALL
SELECT '审计日志表 audit_log', COUNT(*) FROM audit_log
UNION ALL
SELECT 'LLM调用日志表 llm_call_log', COUNT(*) FROM llm_call_log
UNION ALL
SELECT '对话记忆表 spring_ai_chat_memory', COUNT(*) FROM spring_ai_chat_memory
UNION ALL
SELECT '关键事实表 project_fact', COUNT(*) FROM project_fact
UNION ALL
SELECT '事实事件表 project_fact_event', COUNT(*) FROM project_fact_event
UNION ALL
SELECT '业务术语表 business_term', COUNT(*) FROM business_term
UNION ALL
SELECT '通知表 notification', COUNT(*) FROM notification
UNION ALL
SELECT '失败日志表 failure_log', COUNT(*) FROM failure_log
UNION ALL
SELECT '用户角色表 user_role', COUNT(*) FROM user_role
UNION ALL
SELECT '项目成员表 project_member', COUNT(*) FROM project_member
UNION ALL
SELECT '导入批次表 import_batch', COUNT(*) FROM import_batch
UNION ALL
SELECT '导入错误表 import_error', COUNT(*) FROM import_error
UNION ALL
SELECT '议案系列表 proposal_series', COUNT(*) FROM proposal_series;

-- FULLTEXT 索引验证
SELECT 'project.ft_name_cust 索引' AS index_name,
       IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                 WHERE table_schema='archive_db' AND table_name='project' AND index_name='ft_name_cust'),
          '✓ 已创建', '✗ 缺失') AS status
UNION ALL
SELECT 'material_version.ft_parsed_text 索引',
       IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                 WHERE table_schema='archive_db' AND table_name='material_version' AND index_name='ft_parsed_text'),
          '✓ 已创建', '✗ 缺失');