-- ==========================================================
-- 投委会档案管理系统 - 数据库初始化脚本
-- 数据库:archive_db
-- 字符集:utf8mb4 / 排序规则:utf8mb4_unicode_ci
-- ==========================================================

-- 1. 创建数据库(若不存在)
CREATE DATABASE IF NOT EXISTS archive_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE archive_db;

-- 2. 角色表
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

-- 3. 用户表
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
-- 初始数据
-- ==========================================================

-- 4 个预置角色
INSERT INTO role (code, name, description, permissions) VALUES
('admin', '系统管理员', '系统所有权限,包括用户管理、角色配置、规则编辑', JSON_ARRAY('*')),
('committee', '投委会委员', '审议、查看所有项目、查询知识库、查阅议案', JSON_ARRAY('project:read', 'proposal:read', 'qa:ask', 'task:view')),
('project_owner', '项目经理', '负责项目的项目经理,可创建项目、编辑材料、触发规则', JSON_ARRAY('project:write', 'material:upload', 'task:write')),
('employee', '普通员工', '基础查询、提交流转', JSON_ARRAY('project:read', 'qa:ask'));

-- 1 个预置 admin 账号(密码: admin123)
-- BCrypt 哈希(强度 10):$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- 这是 BCrypt 编码后的 "admin123"
INSERT INTO user (username, display_name, password_hash, email, role_id, department, status) VALUES
('admin', '系统管理员', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@example.com', 1, '信息技术部', '在岗');

-- ==========================================================
-- 验证
-- ==========================================================
SELECT 'roles' AS table_name, COUNT(*) AS count FROM role
UNION ALL
SELECT 'users', COUNT(*) FROM user;
