-- MOD-01 / RI-35: 审计日志 5 类 type 分类
USE archive_db;

ALTER TABLE audit_log
    ADD COLUMN type VARCHAR(32) COMMENT 'WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM' AFTER action,
    ADD COLUMN entity_subtype VARCHAR(32) COMMENT '实体子类型' AFTER entity_type;

UPDATE audit_log SET type = CASE
    WHEN action IN ('LOGIN', 'LOGOUT') THEN 'LOGIN'
    WHEN action LIKE 'EXPORT%' THEN 'EXPORT'
    WHEN action LIKE 'LLM%' THEN 'LLM'
    WHEN action LIKE 'SENSITIVE%' THEN 'SENSITIVE_VIEW'
    ELSE 'WRITE'
END WHERE type IS NULL;

CREATE INDEX idx_type ON audit_log(type, created_at);
