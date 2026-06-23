CREATE TABLE IF NOT EXISTS af_code_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    repo_url VARCHAR(500),
    pr_number INT,
    commit_id VARCHAR(64),
    review_result JSON,
    comment_posted BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_repo_pr (repo_url(200), pr_number)
) COMMENT '代码审查记录';
