package com.agentforge.common.model;

import java.util.List;

/**
 * Few-shot SQL 示例库（LLM 生成 SQL 的核心学习源）。
 *
 * <p>所有示例对齐 synthesis 真实业务 schema（见 D:\workSpace\synthesis\Claude.md）：
 * <ul>
 *   <li>交易汇总表 {@code syb_transuminfor}(收银宝) / {@code tlt_transuminfor}(收付通)，settledate 为 int(yyyyMMdd)</li>
 *   <li>商户 {@code syb_merchant}(regdate varchar yyyy-MM-dd, state, city, custype, one) + {@code syb_merchant_rub}(睡眠)</li>
 *   <li>归属必须走 {@code syb_merchantattribute}(organtbstart/interno/organtbend/startrate/endrate/allocationdept)</li>
 *   <li>部门 {@code sys_dept}(dept_id/dept_name/status/del_flag)、员工 {@code sys_user}(user_id/nick_name/dept_id)</li>
 *   <li>收付通商户+归属合一在 {@code tlt_merchantattribute}(indate varchar(8) yyyyMMdd, status)</li>
 *   <li>工单 {@code jxallinpay_busi_order}、费率 {@code busi_rate}、标签 {@code syb_merchant_tag}(tag_pid=27 客户层级)</li>
 * </ul>
 *
 * <p>关键业务规则：收银宝+收付通交易需 UNION；收付通排除 4 类 transtype；三向归属利润 UNION ALL。
 */
public class FewShotData {

    public static List<FewShotEntry> presetExamples() {
        return List.of(

            // ========== 收银宝交易汇总 syb_transuminfor（settledate 为 int yyyyMMdd）==========
            FewShotEntry.builder()
                .question("收银宝上个月的交易总额和笔数")
                .sql("SELECT SUM(tranamt) AS total_amount, SUM(amount) AS total_count FROM syb_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date}")
                .explanation("syb_transuminfor 是收银宝按日汇总表，tranamt=交易金额，amount=笔数，settledate 是 int 型 yyyyMMdd，用 BETWEEN 整数比较")
                .tablesUsed("syb_transuminfor")
                .build(),

            FewShotEntry.builder()
                .question("收银宝各交易类型的金额分布")
                .sql("SELECT transtype, SUM(tranamt) AS total_amount, SUM(amount) AS cnt FROM syb_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date} GROUP BY transtype ORDER BY total_amount DESC")
                .explanation("按 transtype 分组，字段名是 transtype 不是 trantype")
                .tablesUsed("syb_transuminfor")
                .build(),

            FewShotEntry.builder()
                .question("收银宝按月的交易额趋势")
                .sql("SELECT settledate DIV 100 AS ym, SUM(tranamt) AS total_amount FROM syb_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date} GROUP BY settledate DIV 100 ORDER BY ym")
                .explanation("取月份用 settledate DIV 100（int 整除），比 DATE_FORMAT(STR_TO_DATE) 快一个数量级")
                .tablesUsed("syb_transuminfor")
                .build(),

            FewShotEntry.builder()
                .question("收银宝通联净收益（公司口径）")
                .sql("SELECT SUM(oldallinpayfee) AS company_income FROM syb_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date}")
                .explanation("oldallinpayfee=通联收益(公司)，allinpayfee=通联收益(部门)")
                .tablesUsed("syb_transuminfor")
                .build(),

            // ========== 收付通交易 tlt_transuminfor ==========
            FewShotEntry.builder()
                .question("收付通上个月的交易总额（排除代收付款/提现类）")
                .sql("SELECT SUM(tranamt) AS total_amount FROM tlt_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date} AND transtype NOT IN ('结算-T+0代收付款','结算-代收付款','结算-代付失败退款','提现')")
                .explanation("收付通交易需排除 4 类 transtype，否则金额重复计算")
                .tablesUsed("tlt_transuminfor")
                .build(),

            // ========== 收银宝+收付通 UNION 总交易 ==========
            FewShotEntry.builder()
                .question("全部交易（收银宝+收付通）总金额")
                .sql("SELECT SUM(amount_total) AS total_amount FROM (SELECT SUM(tranamt) AS amount_total FROM syb_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date} UNION ALL SELECT SUM(tranamt) FROM tlt_transuminfor WHERE settledate BETWEEN {start_date} AND {end_date} AND transtype NOT IN ('结算-T+0代收付款','结算-代收付款','结算-代付失败退款','提现')) tmp")
                .explanation("真实业务交易含收银宝和收付通两条线，需 UNION ALL 合并，收付通侧排除 4 类 transtype")
                .tablesUsed("syb_transuminfor,tlt_transuminfor")
                .build(),

            // ========== 商户 syb_merchant + syb_merchant_rub（regdate varchar yyyy-MM-dd）==========
            FewShotEntry.builder()
                .question("本月新增了多少商户（收银宝，含睡眠）")
                .sql("SELECT COUNT(DISTINCT cusid) AS new_count FROM (SELECT cusid FROM syb_merchant WHERE regdate BETWEEN '{start_date}' AND '{end_date}' UNION SELECT cusid FROM syb_merchant_rub WHERE regdate BETWEEN '{start_date}' AND '{end_date}') tmp")
                .explanation("新增商户=正常(syb_merchant)+睡眠(syb_merchant_rub)，按 regdate(varchar yyyy-MM-dd) 筛选；两表可能有重复 cusid 用 UNION 去重")
                .tablesUsed("syb_merchant,syb_merchant_rub")
                .build(),

            FewShotEntry.builder()
                .question("存量正常商户有多少（收银宝）")
                .sql("SELECT COUNT(*) AS exist_count FROM syb_merchant WHERE state = '正常'")
                .explanation("存量正常商户按 syb_merchant.state='正常' 筛选")
                .tablesUsed("syb_merchant")
                .build(),

            FewShotEntry.builder()
                .question("各城市的商户数量分布")
                .sql("SELECT city, COUNT(*) AS merchant_count FROM syb_merchant GROUP BY city ORDER BY merchant_count DESC LIMIT 20")
                .explanation("syb_merchant.city 存在，按城市分组")
                .tablesUsed("syb_merchant")
                .build(),

            FewShotEntry.builder()
                .question("各商户类型的数量统计")
                .sql("SELECT custype, COUNT(*) AS merchant_count FROM syb_merchant GROUP BY custype ORDER BY merchant_count DESC LIMIT 20")
                .explanation("syb_merchant.custype 是商户类型字段")
                .tablesUsed("syb_merchant")
                .build(),

            FewShotEntry.builder()
                .question("邮政商户有多少")
                .sql("SELECT COUNT(*) AS post_count FROM syb_merchant WHERE one = '20051300000023X'")
                .explanation("邮政商户标识 syb_merchant.one='20051300000023X'，非邮政为 IS NULL 或不等该值")
                .tablesUsed("syb_merchant")
                .build(),

            // ========== 归属分析 syb_merchantattribute（三向归属，禁止直接用商户表）==========
            FewShotEntry.builder()
                .question("某部门名下有多少商户（收银宝，拓展方）")
                .sql("SELECT COUNT(DISTINCT s.cusid) AS merchant_count FROM syb_merchantattribute s WHERE s.organtbstart = {dept_id}")
                .explanation("归属信息不在 syb_merchant 表，必须通过 syb_merchantattribute 获取；organtbstart=拓展部门，同一 cusid 可能多条需 DISTINCT")
                .tablesUsed("syb_merchantattribute")
                .build(),

            FewShotEntry.builder()
                .question("各部门的收银宝交易金额（拓展方口径）")
                .sql("SELECT d.dept_name, SUM(t.tranamt) AS total_amount FROM (SELECT DISTINCT cusid, organtbstart FROM syb_merchantattribute) m INNER JOIN syb_transuminfor t ON t.cusid = m.cusid INNER JOIN sys_dept d ON d.dept_id = m.organtbstart WHERE t.settledate BETWEEN {start_date} AND {end_date} GROUP BY d.dept_name ORDER BY total_amount DESC")
                .explanation("商户归属一对多需 DISTINCT cusid；关联 sys_dept 取 dept_name（不是 name）")
                .tablesUsed("syb_merchantattribute,syb_transuminfor,sys_dept")
                .build(),

            // ========== 归属城市查询（归属≠商户city，归属=部门所在地）==========
            FewShotEntry.builder()
                .question("收银宝商户中归属为南昌市的商户去年的交易金额汇总")
                .sql("SELECT SUM(t.tranamt) AS total_amount FROM (SELECT DISTINCT cusid, organtbstart FROM syb_merchantattribute) a INNER JOIN syb_transuminfor t ON t.cusid = a.cusid INNER JOIN sys_dept d ON d.dept_id = a.organtbstart WHERE d.dept_name LIKE '%南昌市%' AND t.settledate BETWEEN {start_date} AND {end_date}")
                .explanation("\"归属为XX市\"不是商户的城市(city)，而是商户归属部门所在地。必须 JOIN syb_merchantattribute 获取 organtbstart，再 JOIN sys_dept 按 dept_name 筛选。DISTINCT cusid 去重（同一商户可能多条归属记录）")
                .tablesUsed("syb_merchantattribute,syb_transuminfor,sys_dept")
                .build(),

            FewShotEntry.builder()
                .question("归属为南昌市的收银宝商户有多少")
                .sql("SELECT COUNT(DISTINCT a.cusid) AS merchant_count FROM syb_merchantattribute a INNER JOIN sys_dept d ON d.dept_id = a.organtbstart WHERE d.dept_name LIKE '%南昌市%'")
                .explanation("按归属部门所在地统计商户数，通过 syb_merchantattribute→sys_dept 关联链，DISTINCT cusid（一个商户可能多条归属）")
                .tablesUsed("syb_merchantattribute,sys_dept")
                .build(),

            FewShotEntry.builder()
                .question("收银宝商户中归属为南昌市的商户的上个月交易笔数")
                .sql("SELECT SUM(t.amount) AS total_count FROM (SELECT DISTINCT cusid, organtbstart FROM syb_merchantattribute) a INNER JOIN syb_transuminfor t ON t.cusid = a.cusid INNER JOIN sys_dept d ON d.dept_id = a.organtbstart WHERE d.dept_name LIKE '%南昌市%' AND t.settledate BETWEEN {start_date} AND {end_date}")
                .explanation("用户问归属+城市时，始终走 syb_merchantattribute→sys_dept 关联链，不要用 syb_merchant.city")
                .tablesUsed("syb_merchantattribute,syb_transuminfor,sys_dept")
                .build(),

            FewShotEntry.builder()
                .question("各部门的通联收益（三向归属：拓展+维护+三方分润）")
                .sql("SELECT dept_id, SUM(profit) AS total_profit FROM (SELECT organtbstart AS dept_id, SUM(allinpayfee * startrate) AS profit FROM syb_transuminfor s INNER JOIN syb_merchantattribute m ON s.cusid = m.cusid WHERE s.settledate BETWEEN {start_date} AND {end_date} GROUP BY organtbstart UNION ALL SELECT organtbend AS dept_id, SUM(allinpayfee * endrate) AS profit FROM syb_transuminfor s INNER JOIN syb_merchantattribute m ON s.cusid = m.cusid WHERE s.settledate BETWEEN {start_date} AND {end_date} GROUP BY organtbend UNION ALL SELECT allocationdept AS dept_id, SUM(allinpayfee * allocationrate) AS profit FROM syb_transuminfor s INNER JOIN syb_merchantattribute m ON s.cusid = m.cusid WHERE s.settledate BETWEEN {start_date} AND {end_date} GROUP BY allocationdept) tmp GROUP BY dept_id")
                .explanation("利润三向归属 UNION ALL：拓展方 allinpayfee*startrate + 维护方 allinpayfee*endrate + 三方 allinpayfee*allocationrate")
                .tablesUsed("syb_transuminfor,syb_merchantattribute")
                .build(),

            // ========== 部门/员工 ==========
            FewShotEntry.builder()
                .question("各部门有多少员工")
                .sql("SELECT d.dept_name, COUNT(u.user_id) AS user_count FROM sys_dept d LEFT JOIN sys_user u ON u.dept_id = d.dept_id WHERE d.status = '0' AND d.del_flag = '0' GROUP BY d.dept_id, d.dept_name ORDER BY user_count DESC")
                .explanation("sys_dept 主键 dept_id、名称 dept_name、status='0'正常、del_flag='0'存在；sys_user 用 nick_name 不是 name")
                .tablesUsed("sys_dept,sys_user")
                .build(),

            // ========== 商户+交易关联（地理/画像）==========
            FewShotEntry.builder()
                .question("交易额前10的商户名称和城市")
                .sql("SELECT m.cusname, m.city, SUM(t.tranamt) AS total_amount FROM syb_transuminfor t INNER JOIN syb_merchant m ON t.cusid = m.cusid WHERE t.settledate BETWEEN {start_date} AND {end_date} GROUP BY m.cusname, m.city ORDER BY total_amount DESC LIMIT 10")
                .explanation("交易表 cusid 关联商户表取名称/城市（city 在商户表）")
                .tablesUsed("syb_transuminfor,syb_merchant")
                .build(),

            FewShotEntry.builder()
                .question("流失商户有哪些（上月有交易本月无）")
                .sql("SELECT DISTINCT cusid FROM syb_transuminfor WHERE settledate BETWEEN {last_start} AND {last_end} AND cusid NOT IN (SELECT DISTINCT cusid FROM syb_transuminfor WHERE settledate BETWEEN {this_start} AND {this_end}) LIMIT 100")
                .explanation("用 NOT IN 子查询找出流失商户")
                .tablesUsed("syb_transuminfor")
                .build(),

            // ========== 商户标签 syb_merchant_tag ==========
            FewShotEntry.builder()
                .question("各客户（客户层级）名下有多少商户")
                .sql("SELECT tag_name AS customer, COUNT(*) AS merchant_count FROM syb_merchant_tag WHERE tag_pid = 27 GROUP BY tag_name ORDER BY merchant_count DESC")
                .explanation("客户层级 tag_pid=27，用 cusid 关联各商户表获取商户所属客户")
                .tablesUsed("syb_merchant_tag")
                .build(),

            // ========== 工单 jxallinpay_busi_order ==========
            FewShotEntry.builder()
                .question("各状态的处理中工单数量")
                .sql("SELECT status, COUNT(*) AS cnt FROM jxallinpay_busi_order GROUP BY status ORDER BY cnt DESC")
                .explanation("工单表 jxallinpay_busi_order，status 为工单状态")
                .tablesUsed("jxallinpay_busi_order")
                .build(),

            FewShotEntry.builder()
                .question("各城市的工单数量")
                .sql("SELECT city, COUNT(*) AS cnt FROM jxallinpay_busi_order GROUP BY city ORDER BY cnt DESC LIMIT 20")
                .explanation("工单表含 city 字段")
                .tablesUsed("jxallinpay_busi_order")
                .build(),

            FewShotEntry.builder()
                .question("各业务类型的工单数量")
                .sql("SELECT busi_type, COUNT(*) AS cnt FROM jxallinpay_busi_order GROUP BY busi_type ORDER BY cnt DESC")
                .explanation("工单业务类型字段 busi_type")
                .tablesUsed("jxallinpay_busi_order")
                .build(),

            // ========== 费率 busi_rate ==========
            FewShotEntry.builder()
                .question("各产品的费率列表")
                .sql("SELECT order_id, product_type, rate, status FROM busi_rate ORDER BY create_time DESC LIMIT 50")
                .explanation("费率表 busi_rate，含 order_id/product_type/rate/status/create_time")
                .tablesUsed("busi_rate")
                .build()
        );
    }
}
