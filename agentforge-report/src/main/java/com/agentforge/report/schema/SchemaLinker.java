package com.agentforge.report.schema;

import com.agentforge.common.model.FieldMapping;
import com.agentforge.common.model.FieldMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
@Slf4j
public class SchemaLinker {

    private final JdbcTemplate jdbc;

    /**
     * 核心业务表的关键词→字段映射（对齐 synthesis Claude.md 真实 schema）
     */
    private static final Map<String, List<FieldMapping>> FIELD_MAPPINGS = Map.ofEntries(
            java.util.Map.entry("syb_transuminfor", List.of(
                    fm("tranamt", "交易金额", List.of("交易额", "交易金额", "金额", "总额", "合计")),
                    fm("amount", "交易笔数", List.of("笔数", "交易数", "交易量")),
                    fm("settledate", "结算日期", List.of("日期", "结算日", "哪天", "几号")),
                    fm("cusid", "商户号", List.of("商户号")),
                    fm("transtype", "交易类型", List.of("交易类型")),
                    fm("producttype", "产品类型", List.of("产品")),
                    fm("tranfee", "手续费", List.of("手续费", "费率")),
                    fm("allinpayfee", "通联收益(部门)", List.of("收益", "收入")),
                    fm("oldallinpayfee", "通联收益(公司)", List.of("净收益", "公司收益"))
            )),
            java.util.Map.entry("tlt_transuminfor", List.of(
                    fm("tranamt", "交易金额", List.of("收付通交易额", "交易金额", "金额")),
                    fm("amount", "交易笔数", List.of("笔数")),
                    fm("settledate", "结算日期", List.of("日期", "结算日")),
                    fm("cusid", "商户号", List.of("商户号")),
                    fm("transtype", "交易类型", List.of("交易类型")),
                    fm("allinpayfee", "通联收益", List.of("收付通收益", "收益"))
            )),
            java.util.Map.entry("syb_merchant", List.of(
                    fm("cusid", "商户号", List.of("商户号")),
                    fm("cusname", "商户名称", List.of("商户名", "商户名称")),
                    fm("state", "商户状态", List.of("状态", "停用", "正常", "冻结", "注销")),
                    fm("city", "经营所在市", List.of("城市", "市", "地区")),
                    fm("province", "经营所在省", List.of("省", "省份")),
                    fm("custype", "商户类型", List.of("商户类型")),
                    fm("regdate", "注册日期", List.of("注册", "新增商户")),
                    fm("one", "邮政标识", List.of("邮政"))
            )),
            java.util.Map.entry("syb_merchant_rub", List.of(
                    fm("cusid", "商户号", List.of("睡眠商户", "商户号")),
                    fm("regdate", "注册日期", List.of("注册", "新增商户")),
                    fm("state", "商户状态", List.of("状态")),
                    fm("city", "经营所在市", List.of("城市"))
            )),
            java.util.Map.entry("syb_merchantattribute", List.of(
                    fm("cusid", "商户号", List.of("商户号")),
                    fm("organtbstart", "拓展部门", List.of("拓展部门", "拓展方", "部门", "归属", "归属于", "归属为", "归属部门", "归属方")),
                    fm("organtbend", "维护部门", List.of("维护部门", "维护方", "归属维护", "维护归属")),
                    fm("interno", "拓展人", List.of("拓展人")),
                    fm("startrate", "拓展分润比例", List.of("拓展比例", "分润")),
                    fm("endrate", "维护分润比例", List.of("维护比例", "分润")),
                    fm("allocationdept", "三方分润部门", List.of("三方分润", "分润部门")),
                    fm("allocationrate", "三方分润比例", List.of("三方比例"))
            )),
            java.util.Map.entry("tlt_merchantattribute", List.of(
                    fm("cusid", "商户号", List.of("收付通商户", "商户号")),
                    fm("indate", "注册日期", List.of("收付通注册", "注册")),
                    fm("organtbstart", "拓展部门", List.of("拓展部门", "部门")),
                    fm("organtbend", "维护部门", List.of("维护部门")),
                    fm("status", "状态", List.of("状态")),
                    fm("type", "条线", List.of("条线"))
            )),
            java.util.Map.entry("syb_merchant_tag", List.of(
                    fm("cusid", "商户号", List.of("商户号")),
                    fm("tag_name", "标签/客户名", List.of("客户", "标签")),
                    fm("tag_pid", "标签层级", List.of("客户层级"))
            )),
            java.util.Map.entry("jxallinpay_busi_order", List.of(
                    fm("status", "工单状态", List.of("工单状态", "未处理", "已处理", "已退回")),
                    fm("busi_type", "业务类型", List.of("工单类型", "业务类型")),
                    fm("handleusername", "处理人名字", List.of("处理人")),
                    fm("createtime", "提交时间", List.of("提交", "创建时间")),
                    fm("exigency", "紧急程度", List.of("紧急", "加急")),
                    fm("cusid", "商户号", List.of("商户号")),
                    fm("cusname", "商户名称", List.of("商户名")),
                    fm("city", "城市", List.of("城市"))
            )),
            java.util.Map.entry("busi_rate", List.of(
                    fm("product_type", "产品类型", List.of("产品")),
                    fm("rate", "费率", List.of("费率", "费")),
                    fm("status", "状态", List.of("状态")),
                    fm("dept_name", "部门", List.of("部门"))
            )),
            java.util.Map.entry("sys_dept", List.of(
                    fm("dept_name", "部门名称", List.of("部门", "归属", "归属于", "归属为", "归属部门", "归属方", "分公司", "分部")),
                    fm("dept_id", "部门ID", List.of("部门ID")),
                    fm("status", "部门状态", List.of("部门状态")),
                    fm("parent_id", "上级部门", List.of("上级部门", "父部门"))
            )),
            java.util.Map.entry("sys_user", List.of(
                    fm("user_name", "用户账号", List.of("用户", "账号")),
                    fm("nick_name", "用户昵称", List.of("昵称", "姓名", "员工")),
                    fm("dept_id", "部门ID", List.of("部门")),
                    fm("phonenumber", "手机号", List.of("手机", "电话"))
            ))
    );

    public SchemaLinker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static FieldMapping fm(String columnName, String comment, List<String> keywords) {
        return new FieldMapping(columnName, comment, keywords);
    }

    public List<FieldMatch> linkFields(String question, List<String> tableNames) {
        List<FieldMatch> matches = new ArrayList<>();

        for (String tableName : tableNames) {
            List<FieldMapping> mappings = FIELD_MAPPINGS.get(tableName);
            if (mappings == null) continue;

            for (FieldMapping fm : mappings) {
                for (String keyword : fm.getKeywords()) {
                    if (question.contains(keyword)) {
                        matches.add(FieldMatch.builder()
                                .tableName(tableName)
                                .columnName(fm.getColumnName())
                                .matchedKeyword(keyword)
                                .columnComment(fm.getColumnComment())
                                .build());
                        break;
                    }
                }
            }
        }
        return matches;
    }
}