package com.agentforge.report.schema;

import com.agentforge.common.model.TableRelation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
  public class TableRelationGraph {

      private final JdbcTemplate jdbc;

      /**
       * 预定义的核心表关联关系（对齐 synthesis Claude.md 真实归属模型）
       * 收银宝：transuminfor→merchant(cusid)；merchantattribute 是归属桥；organtbstart→sys_dept.dept_id
       * 收付通：tlt_transuminfor→tlt_merchantattribute(cusid)；归属与商户合一
       */
      private static final List<TableRelation> CORE_RELATIONS = List.of(
              // 收银宝交易 ↔ 商户
              TableRelation.of("syb_transuminfor", "cusid", "syb_merchant", "cusid", "商户号"),
              TableRelation.of("syb_transuminfor", "cusid", "syb_merchant_rub", "cusid", "商户号"),
              // 收银宝商户 ↔ 归属（一对多桥）
              TableRelation.of("syb_merchant", "cusid", "syb_merchantattribute", "cusid", "商户号"),
              TableRelation.of("syb_merchant_rub", "cusid", "syb_merchantattribute", "cusid", "商户号"),
              TableRelation.of("syb_transuminfor", "cusid", "syb_merchantattribute", "cusid", "商户号"),
              // 归属 → 部门/员工（三向）
              TableRelation.of("syb_merchantattribute", "organtbstart", "sys_dept", "dept_id", "拓展部门"),
              TableRelation.of("syb_merchantattribute", "organtbend", "sys_dept", "dept_id", "维护部门"),
              TableRelation.of("syb_merchantattribute", "allocationdept", "sys_dept", "dept_id", "三方分润部门"),
              TableRelation.of("syb_merchantattribute", "interno", "sys_user", "user_id", "拓展人"),
              TableRelation.of("syb_merchantattribute", "internoend", "sys_user", "user_id", "维护人"),
              // 收付通交易 ↔ 商户归属（合一表）
              TableRelation.of("tlt_transuminfor", "cusid", "tlt_merchantattribute", "cusid", "商户号"),
              TableRelation.of("tlt_merchantattribute", "organtbstart", "sys_dept", "dept_id", "拓展部门"),
              TableRelation.of("tlt_merchantattribute", "organtbend", "sys_dept", "dept_id", "维护部门"),
              // 商户标签
              TableRelation.of("syb_merchant", "cusid", "syb_merchant_tag", "cusid", "商户号"),
              // 部门 ↔ 员工
              TableRelation.of("sys_user", "dept_id", "sys_dept", "dept_id", "部门"),
              // 工单 ↔ 商户
              TableRelation.of("jxallinpay_busi_order", "cusid", "syb_merchant", "cusid", "商户号")
      );

    public TableRelationGraph(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
       * 根据涉及的表名，发现JOIN路径
       * @param tableNames Schema检索到的相关表
       * @return 需要的JOIN关系
       */
      public List<TableRelation> discoverJoins(List<String> tableNames) {
          return CORE_RELATIONS.stream()
                  .filter(r -> tableNames.contains(r.getLeftTable())
                            && tableNames.contains(r.getRightTable()))
                  .toList();
      }

      /**
       * 生成JOIN提示文本，注入到SQL生成Prompt中
       */
      public String formatJoinHints(List<String> tableNames) {
          List<TableRelation> joins = discoverJoins(tableNames);
          if (joins.isEmpty()) return "";

          StringBuilder sb = new StringBuilder("## 表关联关系：\n");
          for (TableRelation j : joins) {
              sb.append(String.format("- %s.%s = %s.%s (%s)\n",
                      j.getLeftTable(), j.getLeftColumn(),
                      j.getRightTable(), j.getRightColumn(),
                      j.getRelationName()));
          }
          return sb.toString();
      }

    /**
     * 补全关联表：检索到的表如果通过外键关联到其他核心表，补进来
     * 解决"南昌市上个月交易总额"没召回syb_merchant的问题
     */
    public List<String> expandRelatedTables(List<String> retrievedTables) {
        Set<String> expanded = new LinkedHashSet<>(retrievedTables);

        for (TableRelation rel : CORE_RELATIONS) {
            // 如果左表在结果中，把右表也加进来（反之亦然）
            if (expanded.contains(rel.getLeftTable())) {
                expanded.add(rel.getRightTable());
            }
            if (expanded.contains(rel.getRightTable())) {
                expanded.add(rel.getLeftTable());
            }
        }
        return new ArrayList<>(expanded);
    }
  }