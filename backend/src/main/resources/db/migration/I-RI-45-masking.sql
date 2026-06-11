-- MOD-01 / RI-45: 脱敏视图开关
USE archive_db;

ALTER TABLE user
    ADD COLUMN sensitive_view_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许查看脱敏原始值' AFTER version;

UPDATE user SET sensitive_view_enabled = 1 WHERE username = 'admin' AND sensitive_view_enabled = 0;
