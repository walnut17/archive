-- MOD-01 / RI-34: RBAC 5 角色 + 多对多关联表
USE archive_db;

-- 对齐 v1.0 角色码到 v1.1 (保留 id, 零回归)
UPDATE role SET code = 'pm', name = '项目经理', description = '负责项目的项目经理,可创建项目、编辑材料、触发规则'
WHERE code = 'project_owner';

UPDATE role SET code = 'user', name = '普通用户', description = 'v1.0 兼容普通用户,基础查询、提交流转'
WHERE code = 'employee';

INSERT IGNORE INTO role (code, name, description, permissions) VALUES
('legal', '法务', '法务审查权限', JSON_ARRAY('project:read', 'legal:review')),
('secretary', '投委会秘书', '议案登记与秘书事务', JSON_ARRAY('project:read', 'proposal:write', 'task:write'));

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色多对多';

CREATE TABLE IF NOT EXISTS project_member (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_in_project VARCHAR(64) NOT NULL DEFAULT 'member' COMMENT 'member/owner/legal',
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员';
