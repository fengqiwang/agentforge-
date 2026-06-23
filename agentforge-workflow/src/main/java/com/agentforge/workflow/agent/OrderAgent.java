package com.agentforge.workflow.agent;

  import com.agentforge.report.sql.SqlExecutionService;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.LinkedHashMap;
  import java.util.Map;
  import java.util.regex.Pattern;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class OrderAgent implements Agent {

      private final SqlExecutionService executionService;

      @Override
      public String getName() { return "OrderAgent"; }

      /**
       * 工单 Agent — ORDER 意图专用（独立分支，不走 Report 链）
       *
       * 职责：
       *  - 根据问题关键词路由到工单查询模板
       *  - 直接执行 SQL（不经过 Generator/Validator，因为是预定义安全 SQL）
       *  - 把结果写入 ctx，前端 FormatterAgent 渲染
       *
       * 输入（从 ctx 读）：
       *  - userQuestion
       *
       * 输出（写到 ctx）：
       *  - generatedSql, validatedSql
       *  - resultRows, resultCount
       *
       * 容错：
       *  - 与 SqlExecutorAgent 共享 sql-executor 熔断器
       *  - SQL 是硬编码白名单内的，无需 @Retry（执行失败一般是数据问题，重试无用）
       *
       * 覆盖验收：PRD 2.6 工单处理（E-07 补充）
       */
      @Override
      public void execute(AgentContext ctx) {
          String q = ctx.getUserQuestion();
          String sql = pickTemplate(q);
          log.info("[OrderAgent] 选用 SQL: {}", sql);

          SqlExecutionService.ExecutionResult result = executionService.execute(sql, ctx.getSessionId());
          ctx.setGeneratedSql(sql);
          ctx.setValidatedSql(sql);
          ctx.setQueryResult(result);
          if (result.isSuccess() && result.getRows() != null) {
              ctx.setResultRows(result.getRows());
              ctx.setResultCount(result.getRows().size());
          } else {
              ctx.setErrorMessage(result.getError());
          }
          ctx.getExecutedAgents().add(getName());
      }

      /** 简单关键词路由到对应 SQL 模板（表名 jxallinpay_busi_order 无下划线） */
      private String pickTemplate(String question) {
          if (question == null) question = "";
          String q = question.toLowerCase();

          // 状态分布（status 是 tinyint，需要 CASE 翻译）
          if (q.contains("状态") && (q.contains("分布") || q.contains("统计") || q.contains("分类"))) {
              return "SELECT CASE status " +
                     "WHEN 1 THEN '未处理' WHEN 2 THEN '已处理' " +
                     "WHEN 3 THEN '审核退回' WHEN 4 THEN '已撤销' END AS status_name, " +
                     "COUNT(*) AS cnt " +
                     "FROM jxallinpay_busi_order " +
                     "WHERE createtime >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                     "GROUP BY status ORDER BY cnt DESC";
          }
          // 城市分布
          if (q.contains("城市") || q.contains("地区")) {
              return "SELECT city, COUNT(*) AS cnt " +
                     "FROM jxallinpay_busi_order " +
                     "WHERE createtime >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                     "AND city IS NOT NULL AND city != '' " +
                     "GROUP BY city ORDER BY cnt DESC LIMIT 20";
          }
          // 业务类型分布
          if (q.contains("类型") || q.contains("业务")) {
              return "SELECT busi_type, COUNT(*) AS cnt " +
                     "FROM jxallinpay_busi_order " +
                     "WHERE createtime >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                     "GROUP BY busi_type ORDER BY cnt DESC";
          }
          // 默认：最近工单列表（真实列名）
          return "SELECT id, cusid, cusname, busi_type, city, status, " +
                 "CASE status WHEN 1 THEN '未处理' WHEN 2 THEN '已处理' " +
                 "WHEN 3 THEN '审核退回' WHEN 4 THEN '已撤销' END AS status_name, " +
                 "createtime " +
                 "FROM jxallinpay_busi_order " +
                 "ORDER BY createtime DESC LIMIT 50";
      }
  }