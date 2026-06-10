-- ==========================================================
-- 投委会档案管理系统 - 数据库初始化脚本 (整合所有迁移)
-- 版本: Plan I 完工 (2026-06-10)
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
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色代码:admin/committee/project_owner/employee',
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

-- 4 个预置角色
INSERT INTO role (code, name, description, permissions) VALUES
('admin', '系统管理员', '系统所有权限,包括用户管理、角色配置、规则编辑', JSON_ARRAY('*')),
('committee', '投委会委员', '审议、查看所有项目、查询知识库、查阅议案', JSON_ARRAY('project:read', 'proposal:read', 'qa:ask', 'task:view')),
('project_owner', '项目经理', '负责项目的项目经理,可创建项目、编辑材料、触发规则', JSON_ARRAY('project:write', 'material:upload', 'task:write')),
('employee', '普通员工', '基础查询、提交流转', JSON_ARRAY('project:read', 'qa:ask'));

-- 1 个预置 admin 账号(密码: admin123)
-- BCrypt 哈希(强度 10):$2a$10$wjN3YFZDlu.ThmfrRe0XvOA9A1AW2TybgeKAddA/TTxTEhEGvg/Ve
-- 这是 BCrypt 编码后的 "admin123"
INSERT INTO user (username, display_name, password_hash, email, role_id, department, status) VALUES
('admin', '系统管理员', '$2a$10$wjN3YFZDlu.ThmfrRe0XvOA9A1AW2TybgeKAddA/TTxTEhEGvg/Ve', 'admin@example.com', 1, '信息技术部', '在岗');

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
    INDEX idx_code (code),
    INDEX idx_status (status),
    INDEX idx_owner_id (owner_id),
    INDEX idx_created_at (created_at),
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
    remark VARCHAR(2000) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
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

-- ==========================================================
-- 11. 迁移检查
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
SELECT 'LLM调用日志表 llm_call_log', COUNT(*) FROM llm_call_log
UNION ALL
SELECT '对话记忆表 spring_ai_chat_memory', COUNT(*) FROM spring_ai_chat_memory;

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