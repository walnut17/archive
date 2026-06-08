-- ==========================================================
-- M2 知识库问答 — MySQL FULLTEXT 索引迁移
-- 在 material_version 表加 parsed_text 字段 + FULLTEXT 索引
-- ==========================================================

-- 1. 加 parsed_text 字段(已有 parsed_text_path,再加 parsed_text 内容本身,
--    避免每次检索要从文件读)
ALTER TABLE material_version
    ADD COLUMN parsed_text LONGTEXT NULL COMMENT '解析后的纯文本内容(M2 知识库问答 FULLTEXT 索引字段)';

-- 2. 加 FULLTEXT 索引(MySQL 8.0+ 内置 ngram,中文友好)
ALTER TABLE material_version
    ADD FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram;

-- 3. 验证索引
SHOW INDEX FROM material_version WHERE Key_name = 'ft_parsed_text';
