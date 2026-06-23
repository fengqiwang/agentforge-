CREATE TABLE IF NOT EXISTS af_schema_index (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    table_name VARCHAR(100) NOT NULL,
    column_count INT,
    table_comment VARCHAR(500),
    index_status TINYINT DEFAULT 0 COMMENT '0待索引 1已索引',
    embedding_id VARCHAR(100),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table (table_name)
) COMMENT 'Schema索引元数据';
