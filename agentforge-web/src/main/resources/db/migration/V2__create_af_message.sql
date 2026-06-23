CREATE TABLE IF NOT EXISTS af_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant/system/tool',
    content TEXT,
    metadata JSON COMMENT '额外信息',
    token_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) COMMENT '对话消息表';
