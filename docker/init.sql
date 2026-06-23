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
