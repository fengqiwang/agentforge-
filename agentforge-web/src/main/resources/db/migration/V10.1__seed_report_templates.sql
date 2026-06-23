-- 业务报告模板（对齐 synthesis Claude.md：settledate int、regdate varchar、transtype、city 在商户表）
-- 注意：flyway 当前禁用，DB 内若已有旧模板需先 DELETE FROM af_report_template 再执行本文件
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
