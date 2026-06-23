-- 对账批次主表
CREATE TABLE af_reconciliation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '对账批次名称',
    source_file VARCHAR(500) COMMENT '上传文件名',
    match_strategy VARCHAR(20) DEFAULT 'FUZZY' COMMENT '匹配策略：EXACT/FUZZY',
    total_internal INT DEFAULT 0 COMMENT '我方总笔数',
    total_external INT DEFAULT 0 COMMENT '第三方总笔数',
    matched_count INT DEFAULT 0 COMMENT '匹配成功数',
    amount_diff_count INT DEFAULT 0 COMMENT '金额差异数',
    only_internal_count INT DEFAULT 0 COMMENT '仅我方有',
    only_external_count INT DEFAULT 0 COMMENT '仅第三方有',
    diff_amount DECIMAL(15,2) DEFAULT 0 COMMENT '差异金额',
    status VARCHAR(20) DEFAULT 'PROCESSING' COMMENT 'PROCESSING/COMPLETED/FAILED',
    created_by VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) COMMENT '对账批次记录';

-- 对账差异明细表
CREATE TABLE af_reconciliation_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reconciliation_id BIGINT NOT NULL COMMENT '对账批次ID',
    match_type VARCHAR(20) NOT NULL COMMENT 'MATCHED/AMOUNT_DIFF/ONLY_INTERNAL/ONLY_EXTERNAL/DUPLICATE',
    internal_tranno VARCHAR(50) COMMENT '我方流水号',
    internal_cusid VARCHAR(20) COMMENT '我方商户号',
    internal_amount DECIMAL(12,2) COMMENT '我方金额',
    internal_date VARCHAR(20) COMMENT '我方日期',
    external_tranno VARCHAR(50) COMMENT '第三方流水号',
    external_cusid VARCHAR(20) COMMENT '第三方商户号',
    external_amount DECIMAL(12,2) COMMENT '第三方金额',
    external_date VARCHAR(20) COMMENT '第三方日期',
    diff_amount DECIMAL(12,2) COMMENT '差异金额',
    INDEX idx_recon (reconciliation_id),
    INDEX idx_type (match_type)
) COMMENT '对账差异明细';
