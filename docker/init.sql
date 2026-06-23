-- ============================================================
-- AgentForge 业务表 DDL（第三方一键部署用）
-- MySQL 容器首次启动时自动执行
-- ============================================================

-- 1. 收银宝按日交易汇总表
CREATE TABLE IF NOT EXISTS syb_transuminfor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settledate INT NOT NULL COMMENT '交易日期 yyyyMMdd',
    cusid VARCHAR(15) NOT NULL COMMENT '商户号',
    shopid VARCHAR(20) DEFAULT NULL COMMENT '门店编号',
    cusname VARCHAR(100) DEFAULT NULL COMMENT '商户名称',
    termid VARCHAR(20) DEFAULT NULL COMMENT '终端号',
    transtatus VARCHAR(30) DEFAULT NULL COMMENT '交易状态',
    producttype VARCHAR(20) DEFAULT NULL COMMENT '产品名称',
    transtype VARCHAR(20) DEFAULT NULL COMMENT '交易类型',
    tranamt DECIMAL(12,2) DEFAULT 0.00 COMMENT '交易金额',
    amount INT DEFAULT 0 COMMENT '交易笔数',
    allinpayfee DECIMAL(12,4) DEFAULT 0.0000 COMMENT '通联收益(部门)',
    oldallinpayfee DECIMAL(12,4) DEFAULT 0.0000 COMMENT '通联收益(公司)',
    respcode VARCHAR(10) DEFAULT NULL COMMENT '应答码',
    city VARCHAR(100) DEFAULT NULL COMMENT '城市',
    PRIMARY KEY (id),
    INDEX idx_settledate (settledate),
    INDEX idx_cusid (cusid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收银宝按日交易汇总表';

-- 2. 收付通按日交易汇总表
CREATE TABLE IF NOT EXISTS tlt_transuminfor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settledate INT NOT NULL COMMENT '交易日期 yyyyMMdd',
    cusid VARCHAR(15) NOT NULL COMMENT '商户号',
    cusname VARCHAR(100) DEFAULT NULL COMMENT '商户名称',
    termid VARCHAR(8) DEFAULT NULL COMMENT '终端号',
    transtype VARCHAR(40) DEFAULT NULL COMMENT '交易类型',
    tranamt DECIMAL(12,2) DEFAULT 0.00 COMMENT '交易金额',
    PRIMARY KEY (id),
    INDEX idx_settledate (settledate),
    INDEX idx_cusid (cusid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收付通按日交易汇总表';

-- 3. 收银宝商户信息
CREATE TABLE IF NOT EXISTS syb_merchant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    regdate VARCHAR(10) DEFAULT NULL COMMENT '注册日期 yyyy-MM-dd',
    revdate VARCHAR(10) DEFAULT NULL COMMENT '变更日期',
    cusid VARCHAR(15) NOT NULL COMMENT '商户号',
    cusname VARCHAR(100) DEFAULT NULL COMMENT '商户名称',
    shortname VARCHAR(100) DEFAULT NULL COMMENT '商户简称',
    belongbranch VARCHAR(30) DEFAULT NULL COMMENT '所属分公司',
    belongorgid VARCHAR(100) DEFAULT NULL COMMENT '拓展机构',
    maintaincity VARCHAR(100) DEFAULT NULL COMMENT '维护机构',
    facusid VARCHAR(15) DEFAULT NULL COMMENT '父商户号',
    province VARCHAR(100) DEFAULT NULL COMMENT '经营所在省',
    city VARCHAR(100) DEFAULT NULL COMMENT '经营所在市',
    address VARCHAR(255) DEFAULT NULL COMMENT '注册地址',
    mcc VARCHAR(8) DEFAULT NULL COMMENT 'mcc大类',
    custype VARCHAR(32) DEFAULT NULL COMMENT '商户类型',
    state VARCHAR(10) DEFAULT NULL COMMENT '商户状态',
    expandper VARCHAR(32) DEFAULT NULL COMMENT '拓展人',
    expandtype VARCHAR(20) DEFAULT NULL COMMENT '拓展商户类型',
    one VARCHAR(50) DEFAULT NULL COMMENT '1级小伙伴机构号',
    code VARCHAR(20) DEFAULT NULL COMMENT '8位机构码',
    createtime DATETIME DEFAULT NULL COMMENT '录入时间',
    updatetime TIMESTAMP NULL DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cusid (cusid),
    INDEX idx_state (state),
    INDEX idx_city (city),
    INDEX idx_regdate (regdate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收银宝商户信息';

-- 4. 收银宝睡眠商户信息
CREATE TABLE IF NOT EXISTS syb_merchant_rub (
    id INT NOT NULL AUTO_INCREMENT,
    regdate VARCHAR(10) DEFAULT NULL COMMENT '注册日期',
    revdate VARCHAR(10) DEFAULT NULL COMMENT '变更日期',
    cusid VARCHAR(15) NOT NULL COMMENT '商户号',
    cusname VARCHAR(100) DEFAULT NULL COMMENT '商户名称',
    province VARCHAR(100) DEFAULT NULL COMMENT '经营所在省',
    city VARCHAR(100) DEFAULT NULL COMMENT '经营所在市',
    state VARCHAR(10) DEFAULT NULL COMMENT '商户状态',
    custype VARCHAR(32) DEFAULT NULL COMMENT '商户类型',
    expandper VARCHAR(32) DEFAULT NULL COMMENT '拓展人',
    one VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cusid (cusid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收银宝睡眠商户信息';

-- 5. 收银宝商户归属绑定表
CREATE TABLE IF NOT EXISTS syb_merchantattribute (
    id BIGINT NOT NULL AUTO_INCREMENT,
    indate VARCHAR(20) DEFAULT NULL COMMENT '商户或终端的注册日期',
    cusid VARCHAR(15) NOT NULL COMMENT '商户号',
    shopid VARCHAR(20) DEFAULT NULL COMMENT '门店编号',
    termid VARCHAR(8) DEFAULT NULL COMMENT '终端号',
    organtbstart BIGINT DEFAULT NULL COMMENT '拓展部门编号',
    interno BIGINT DEFAULT NULL COMMENT '拓展人编号',
    organtbend BIGINT DEFAULT NULL COMMENT '维护部门编号',
    internoend BIGINT DEFAULT NULL COMMENT '维护人编号',
    startrate DECIMAL(6,4) DEFAULT 0.0000 COMMENT '拓展方分润比例',
    endrate DECIMAL(6,4) DEFAULT 0.0000 COMMENT '维护方分润比例',
    allocationdept BIGINT DEFAULT NULL COMMENT '三方分润部门',
    allocationrate DECIMAL(6,4) DEFAULT 0.0000 COMMENT '三方分润比例',
    PRIMARY KEY (id),
    INDEX idx_cusid (cusid),
    INDEX idx_organtbstart (organtbstart)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收银宝商户归属绑定表';

-- 6. 收付通商户归属绑定表
CREATE TABLE IF NOT EXISTS tlt_merchantattribute (
    id BIGINT NOT NULL AUTO_INCREMENT,
    indate VARCHAR(8) DEFAULT NULL COMMENT '商户注册日期 yyyyMMdd',
    cusid VARCHAR(15) NOT NULL COMMENT '商户号',
    cusname VARCHAR(100) DEFAULT NULL COMMENT '商户名称',
    termid VARCHAR(8) DEFAULT NULL COMMENT '终端号',
    fmcc VARCHAR(30) DEFAULT NULL COMMENT '一级行业',
    smcc VARCHAR(30) DEFAULT NULL COMMENT '二级行业',
    organtbstart BIGINT DEFAULT NULL COMMENT '拓展部门编号',
    interno BIGINT DEFAULT NULL COMMENT '拓展人',
    organtbend BIGINT DEFAULT NULL COMMENT '维护部门编号',
    internoend BIGINT DEFAULT NULL COMMENT '维护人编号',
    startrate DECIMAL(6,4) DEFAULT 0.0000,
    endrate DECIMAL(6,4) DEFAULT 0.0000,
    status VARCHAR(10) DEFAULT NULL COMMENT '状态',
    PRIMARY KEY (id),
    INDEX idx_cusid (cusid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收付通商户归属绑定表';

-- 7. 部门表
CREATE TABLE IF NOT EXISTS sys_dept (
    dept_id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT DEFAULT NULL COMMENT '父部门id',
    ancestors VARCHAR(50) DEFAULT NULL COMMENT '祖级列表',
    dept_name VARCHAR(50) DEFAULT NULL COMMENT '部门名称',
    order_num INT DEFAULT NULL COMMENT '显示顺序',
    leader VARCHAR(20) DEFAULT NULL COMMENT '负责人',
    phone VARCHAR(11) DEFAULT NULL COMMENT '联系电话',
    email VARCHAR(50) DEFAULT NULL COMMENT '邮箱',
    status CHAR(1) DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    address VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    PRIMARY KEY (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 8. 用户信息表
CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    dept_id BIGINT DEFAULT NULL COMMENT '部门ID',
    user_name VARCHAR(30) DEFAULT NULL COMMENT '用户账号',
    nick_name VARCHAR(30) DEFAULT NULL COMMENT '用户昵称',
    user_type VARCHAR(2) DEFAULT '00' COMMENT '用户类型',
    email VARCHAR(50) DEFAULT NULL COMMENT '用户邮箱',
    phonenumber VARCHAR(12) DEFAULT NULL COMMENT '手机号码',
    sex CHAR(1) DEFAULT '0' COMMENT '用户性别',
    avatar VARCHAR(100) DEFAULT NULL COMMENT '头像地址',
    password VARCHAR(100) DEFAULT NULL COMMENT '密码',
    status CHAR(1) DEFAULT '0' COMMENT '帐号状态',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    login_ip VARCHAR(128) DEFAULT NULL COMMENT '最后登录IP',
    login_date DATETIME DEFAULT NULL COMMENT '最后登录时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 9. 商户标签表
CREATE TABLE IF NOT EXISTS syb_merchant_tag (
    cusid VARCHAR(20) NOT NULL COMMENT '商户号',
    tag_id INT NOT NULL COMMENT '标签id',
    tag_name VARCHAR(50) DEFAULT NULL COMMENT '标签名称',
    tag_pid INT DEFAULT NULL COMMENT '一级标签id',
    tag_date VARCHAR(20) DEFAULT NULL COMMENT '创建日期',
    PRIMARY KEY (cusid, tag_id),
    INDEX idx_tag_pid (tag_pid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户标签表';

-- 10. 业务工单表
CREATE TABLE IF NOT EXISTS jxallinpay_busi_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    adduserid VARCHAR(20) DEFAULT NULL COMMENT '提交人id',
    addusername VARCHAR(20) DEFAULT NULL COMMENT '提交人名字',
    audittype TINYINT UNSIGNED DEFAULT 1 COMMENT '审核类型',
    exigency TINYINT UNSIGNED DEFAULT 1 COMMENT '紧急程度',
    handleuserid INT DEFAULT NULL COMMENT '处理人id',
    handleusername VARCHAR(20) DEFAULT NULL COMMENT '处理人名字',
    city VARCHAR(20) DEFAULT NULL COMMENT '商户所在市',
    cusid VARCHAR(20) DEFAULT NULL COMMENT '商户号',
    cusname VARCHAR(50) DEFAULT NULL COMMENT '商户名称',
    busi_type VARCHAR(50) DEFAULT NULL COMMENT '业务类型',
    remark VARCHAR(2000) DEFAULT NULL COMMENT '备注',
    opinion VARCHAR(255) DEFAULT NULL COMMENT '处理意见',
    status TINYINT UNSIGNED DEFAULT 1 COMMENT '状态：1未处理 2已处理 3已退回 4已撤回',
    createtime DATETIME DEFAULT NULL COMMENT '提交时间',
    updatetime DATETIME DEFAULT NULL COMMENT '处理时间',
    PRIMARY KEY (id),
    INDEX idx_status (status),
    INDEX idx_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务工单表';

-- 11. 费率表
CREATE TABLE IF NOT EXISTS busi_rate (
    order_id INT NOT NULL AUTO_INCREMENT,
    product_type VARCHAR(20) DEFAULT NULL COMMENT '产品类型',
    rate VARCHAR(10) DEFAULT NULL COMMENT '变更后的扣率',
    note VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    add_user_id INT DEFAULT NULL COMMENT '创建人用户id',
    add_nickname VARCHAR(50) DEFAULT NULL COMMENT '创建人姓名',
    dept_id INT DEFAULT NULL COMMENT '创建人所在部门',
    dept_name VARCHAR(50) DEFAULT NULL COMMENT '创建人部门名称',
    status CHAR(1) DEFAULT '0' COMMENT '状态',
    sx_type CHAR(1) DEFAULT NULL COMMENT '生效时间类型',
    zd_date DATE DEFAULT NULL COMMENT '指定日期',
    PRIMARY KEY (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费率表';

-- ============================================================
-- 以下为 AgentForge 应用自身运行所需的表（af_* 前缀）
-- 正常部署由 Flyway 管理；Docker 一键部署时禁用 Flyway，由此脚本创建
-- ============================================================

-- 12. 对话会话表
CREATE TABLE IF NOT EXISTS af_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(200),
    status TINYINT DEFAULT 1 COMMENT '1活跃 2归档',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 13. 对话消息表
CREATE TABLE IF NOT EXISTS af_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant/system/tool',
    content TEXT,
    metadata JSON COMMENT '额外信息',
    token_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';

-- 14. Agent执行日志
CREATE TABLE IF NOT EXISTS af_agent_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64),
    agent_name VARCHAR(50) NOT NULL,
    input_text TEXT,
    output_text TEXT,
    duration_ms INT,
    token_used INT,
    status VARCHAR(20) COMMENT 'success/failed/timeout',
    error_msg TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_agent (agent_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent执行日志';

-- 15. SQL执行记录
CREATE TABLE IF NOT EXISTS af_sql_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64),
    sql_text TEXT NOT NULL,
    tables_used JSON,
    result_count INT,
    duration_ms INT,
    is_valid BOOLEAN,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL执行记录';

-- 16. Schema索引元数据
CREATE TABLE IF NOT EXISTS af_schema_index (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    table_name VARCHAR(100) NOT NULL,
    column_count INT,
    table_comment VARCHAR(500),
    index_status TINYINT DEFAULT 0 COMMENT '0待索引 1已索引',
    embedding_id VARCHAR(100),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Schema索引元数据';

-- 17. 代码审查记录
CREATE TABLE IF NOT EXISTS af_code_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    repo_url VARCHAR(500),
    pr_number INT,
    commit_id VARCHAR(64),
    review_result JSON,
    comment_posted BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_repo_pr (repo_url(200), pr_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码审查记录';

-- 18. SQL模板库
CREATE TABLE IF NOT EXISTS af_sql_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    pattern VARCHAR(200) NOT NULL,
    keywords JSON NOT NULL,
    template_sql TEXT NOT NULL,
    params JSON DEFAULT NULL,
    category VARCHAR(50) NOT NULL COMMENT 'simple/aggregate/groupby/join/complex',
    example_question VARCHAR(500) DEFAULT NULL COMMENT '示例问题',
    tables_used JSON DEFAULT NULL COMMENT '涉及的表',
    description VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
    enabled TINYINT(1) DEFAULT 1,
    hit_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category (category),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL模板库';

-- 19. 对账批次记录
CREATE TABLE IF NOT EXISTS af_reconciliation (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账批次记录';

-- 20. 对账差异明细
CREATE TABLE IF NOT EXISTS af_reconciliation_detail (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账差异明细';

-- 21. 业务报告模板
CREATE TABLE IF NOT EXISTS af_report_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) COMMENT '模板描述',
    queries JSON NOT NULL COMMENT '查询列表 [{"name":"...","sql":"..."}]',
    schedule VARCHAR(50) COMMENT 'cron表达式',
    enabled TINYINT(1) DEFAULT 1,
    created_by VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务报告模板';

-- 22. 业务报告（执行结果）
CREATE TABLE IF NOT EXISTS af_business_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL COMMENT '模板ID',
    template_name VARCHAR(100) COMMENT '模板名称',
    report_date VARCHAR(20) COMMENT '报告日期',
    status VARCHAR(20) DEFAULT 'GENERATING' COMMENT 'GENERATING/COMPLETED/FAILED',
    data_result JSON COMMENT '查询结果数据',
    ai_analysis TEXT COMMENT 'AI分析结果',
    error_message TEXT COMMENT '失败原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_template (template_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务报告';

-- 23. 报表配置（动态报表）
CREATE TABLE IF NOT EXISTS af_report_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) COMMENT '报表名称',
    description VARCHAR(500) COMMENT '报表描述',
    sql_text TEXT COMMENT '报表SQL',
    chart_config JSON COMMENT '图表配置',
    filter_config JSON COMMENT '筛选配置',
    column_config JSON COMMENT '列配置',
    created_by VARCHAR(50),
    share_token VARCHAR(64) COMMENT '分享令牌',
    status INT DEFAULT 1 COMMENT '1正常 0删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_token (share_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表配置';

-- 24. 分析任务
CREATE TABLE IF NOT EXISTS af_analysis_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '分析任务名称',
    dimensions JSON COMMENT '分析维度["time","geo","merchant"]',
    date_range_start VARCHAR(20) COMMENT '开始日期 yyyyMMdd',
    date_range_end VARCHAR(20) COMMENT '结束日期 yyyyMMdd',
    status VARCHAR(20) DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED',
    result JSON COMMENT '分析结果',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析任务';

-- 25. 市场洞察
CREATE TABLE IF NOT EXISTS af_market_insight (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_type VARCHAR(10) NOT NULL COMMENT 'WEEKLY/MONTHLY',
    period_start VARCHAR(20) NOT NULL,
    period_end VARCHAR(20) NOT NULL,
    analysis_task_id BIGINT COMMENT '关联的分析任务',
    trends JSON COMMENT '趋势发现',
    anomalies JSON COMMENT '异常告警',
    opportunities JSON COMMENT '机会点',
    summary TEXT COMMENT 'AI总结',
    status VARCHAR(20) DEFAULT 'GENERATED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period (period_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='市场洞察';

-- ============================================================
-- Flyway 种子数据（对齐生产环境的模板/SQL示例）
-- ============================================================

-- 业务报告模板种子
INSERT INTO af_report_template (name, description, queries, schedule, enabled, created_by) VALUES
(
    '月度交易报告',
    '每月初自动生成上月交易分析报告',
    '[{"name":"总体概况","sql":"SELECT COUNT(*) AS total_trans, SUM(tranamt) AS total_amount, AVG(tranamt) AS avg_amount, COUNT(DISTINCT cusid) AS merchant_count FROM syb_transuminfor WHERE settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), ''%Y%m%d'') AND settledate < DATE_FORMAT(CURDATE(), ''%Y%m%d'')"},{"name":"按城市统计","sql":"SELECT m.city, COUNT(*) AS trans_count, SUM(t.tranamt) AS amount FROM syb_transuminfor t INNER JOIN syb_merchant m ON t.cusid = m.cusid WHERE t.settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), ''%Y%m%d'') GROUP BY m.city ORDER BY amount DESC LIMIT 10"},{"name":"按交易类型","sql":"SELECT transtype, COUNT(*) AS cnt, SUM(tranamt) AS total FROM syb_transuminfor WHERE settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), ''%Y%m%d'') GROUP BY transtype ORDER BY total DESC"}]',
    '0 0 2 1 * ?',
    1,
    'system'
),
(
    '商户增长报告',
    '每周统计新入网商户和活跃商户（收银宝含睡眠）',
    '[{"name":"新增商户","sql":"SELECT COUNT(DISTINCT cusid) AS new_merchants FROM (SELECT cusid FROM syb_merchant WHERE regdate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 7 DAY), ''%Y-%m-%d'') UNION SELECT cusid FROM syb_merchant_rub WHERE regdate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 7 DAY), ''%Y-%m-%d'')) tmp"},{"name":"活跃商户","sql":"SELECT COUNT(DISTINCT cusid) AS active_merchants FROM syb_transuminfor WHERE settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 7 DAY), ''%Y%m%d'')"},{"name":"商户类型分布","sql":"SELECT custype, COUNT(*) AS cnt FROM syb_merchant GROUP BY custype ORDER BY cnt DESC"}]',
    '0 0 9 ? * MON',
    1,
    'system'
),
(
    '城市分析报告',
    '按城市维度分析交易分布和趋势',
    '[{"name":"交易额TOP10城市","sql":"SELECT m.city, SUM(t.tranamt) AS total_amount, COUNT(*) AS trans_count, COUNT(DISTINCT t.cusid) AS merchant_count FROM syb_transuminfor t INNER JOIN syb_merchant m ON t.cusid = m.cusid GROUP BY m.city ORDER BY total_amount DESC LIMIT 10"},{"name":"城市日交易趋势","sql":"SELECT m.city, t.settledate, SUM(t.tranamt) AS daily_amount FROM syb_transuminfor t INNER JOIN syb_merchant m ON t.cusid = m.cusid WHERE t.settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 30 DAY), ''%Y%m%d'') GROUP BY m.city, t.settledate ORDER BY t.settledate DESC, daily_amount DESC LIMIT 200"}]',
    '0 0 8 * * ?',
    1,
    'system'
);

-- SQL模板种子数据
INSERT INTO af_sql_template (name, pattern, keywords, template_sql, params, category, example_question, tables_used, description, enabled, hit_count) VALUES
('收银宝交易总额', '总(交易|金额).*(收银宝|交易)', '["交易总额","收银宝","tranamt"]', 'SELECT SUM(tranamt) AS total_amount, SUM(amount) AS total_count FROM syb_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date}', '["start_date","end_date"]', 'aggregate', '收银宝上个月交易总额', '["syb_transuminfor"]', '收银宝按日汇总表，settledate 为 int(yyyyMMdd)', 1, 0),
('收付通交易总额', '收付通.*交易|tlt', '["收付通","tlt","交易"]', 'SELECT SUM(tranamt) AS total_amount FROM tlt_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date} AND transtype NOT IN (''结算-T+0代收付款'',''结算-代收付款'',''结算-代付失败退款'',''提现'')', '["start_date","end_date"]', 'aggregate', '收付通上月交易总额', '["tlt_transuminfor"]', '收付通需排除 4 类 transtype', 1, 0),
('部门交易额', '(部门).*(交易|金额|交易额)', '["部门","organtbstart","dept_name"]', 'SELECT d.dept_name, SUM(t.tranamt) AS total_amount FROM (SELECT DISTINCT cusid, organtbstart FROM syb_merchantattribute) m INNER JOIN syb_transuminfor t ON t.cusid = m.cusid INNER JOIN sys_dept d ON d.dept_id = m.organtbstart WHERE t.settledate BETWEEN {start_date} AND {end_date} GROUP BY d.dept_name ORDER BY total_amount DESC', '["start_date","end_date"]', 'join', '各部门的收银宝交易金额', '["syb_merchantattribute","syb_transuminfor","sys_dept"]', '归属必须走 syb_merchantattribute，DISTINCT cusid 去重', 1, 0),
('城市商户分布', '(城市|地区).*商户.*(分布|数量|排名)', '["城市","city","merchant"]', 'SELECT city, COUNT(*) AS merchant_count FROM syb_merchant GROUP BY city ORDER BY merchant_count DESC LIMIT 20', '[]', 'groupby', '各城市的商户数量分布', '["syb_merchant"]', 'city 在商户表', 1, 0);
