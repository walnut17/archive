-- ==========================================================
-- I-5: project 表加 customer_name 字段 + FULLTEXT 索引
-- 用途: FindProjectTool 语义搜索 project.name + customer_name
-- ==========================================================

-- 1. 加 customer_name 字段
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND COLUMN_NAME = 'customer_name'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE project ADD COLUMN customer_name VARCHAR(256) COMMENT ''客户名称'' AFTER name',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 加 FULLTEXT 索引(MySQL 8.0+ 内置 ngram,中文友好)
SET @idx_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND INDEX_NAME = 'ft_name_cust'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE project ADD FULLTEXT INDEX ft_name_cust (name, customer_name) WITH PARSER ngram',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 验证
SELECT 'I-find-project-fulltext OK' AS migration_status,
       COUNT(*) AS idx_exists
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'project'
  AND INDEX_NAME = 'ft_name_cust';
