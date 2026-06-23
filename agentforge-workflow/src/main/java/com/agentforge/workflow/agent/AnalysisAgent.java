package com.agentforge.workflow.agent;

  import com.agentforge.report.sql.ISqlExecutionService;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.ArrayList;
  import java.util.LinkedHashMap;
  import java.util.List;
  import java.util.Map;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class AnalysisAgent implements Agent {

      private final ISqlExecutionService executionService;

      /** 商户总数查询：收银宝(正常) + 睡眠商户(syb_merchant_rub) */
      private static final String SQL_TOTAL_MERCHANTS =
              "SELECT COUNT(DISTINCT cusid) AS total_merchants FROM (" +
              "SELECT cusid FROM syb_merchant UNION SELECT cusid FROM syb_merchant_rub) tmp";

      /** 活跃商户查询：收银宝 + 收付通近30天有交易的商户（排除特定 transtype） */
      private static final String SQL_ACTIVE_MERCHANTS =
              "SELECT COUNT(DISTINCT cusid) AS active_merchants FROM (" +
              "SELECT DISTINCT cusid FROM syb_transuminfor " +
              "WHERE settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '%Y%m%d') " +
              "UNION SELECT DISTINCT cusid FROM tlt_transuminfor " +
              "WHERE settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '%Y%m%d') " +
              "AND transtype NOT IN ('结算-T+0代收付款','结算-代收付款','结算-代付失败退款','提现')) tmp";

      /** 流失商户查询：总商户 - 近30天有交易的商户 */
      private static final String SQL_CHURN_MERCHANTS =
              "SELECT COUNT(DISTINCT cusid) AS churn_merchants FROM (" +
              "SELECT cusid FROM syb_merchant UNION SELECT cusid FROM syb_merchant_rub) m " +
              "WHERE NOT EXISTS (SELECT 1 FROM syb_transuminfor t WHERE t.cusid = m.cusid " +
              "AND t.settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 30 DAY), '%Y%m%d'))";

      private static final String[] ANALYSIS_STEP_NAMES =
              {"total_merchants", "active_merchants", "churn_merchants"};

      @Override
      public String getName() { return "AnalysisAgent"; }

      /**
       * 商户分析 Agent — ANALYSIS 意图专用（多步 SQL 串行执行）
       *
       * 职责：
       *  - 执行多步 SQL：总数 → 活跃数 → 流失数
       *  - 合并每步结果为单行，作为 FormatterAgent 的输入
       *
       * 输入（从 ctx 读）：
       *  - userQuestion
       *
       * 输出（写到 ctx）：
       *  - generatedSql, validatedSql   （多个 SQL 用 " ; " 拼接，仅供展示）
       *  - resultRows                   合并后的单行结果
       *
       * 容错：
       *  - 每步独立执行，单步失败不影响其他步（只 log warn）
       *  - 整体不受 sql-executor 熔断保护（多步执行让熔断判定复杂化，暂时禁用）
       *
       * 覆盖验收：PRD 2.6 商户分析（E-07 补充）
       */
      @Override
      public void execute(AgentContext ctx) {
          // 多步分析：总商户数 → 30天内有交易 → 流失商户
          List<Map<String, Object>> allSteps = new ArrayList<>();
          Map<String, Object> stepResults = new LinkedHashMap<>();
          List<String> executedSqls = new ArrayList<>();

          String[] sqls = {SQL_TOTAL_MERCHANTS, SQL_ACTIVE_MERCHANTS, SQL_CHURN_MERCHANTS};

          for (int i = 0; i < sqls.length; i++) {
              String sql = sqls[i];
              log.info("[AnalysisAgent] step{}: {}", i, sql);
              executedSqls.add(sql);
              ISqlExecutionService.ExecutionResult r = executionService.execute(sql, ctx.getSessionId());
              if (r.isSuccess() && r.getRows() != null && !r.getRows().isEmpty()) {
                  Object value = r.getRows().get(0).values().iterator().next();
                  stepResults.put(ANALYSIS_STEP_NAMES[i], value);
                  allSteps.add(r.getRows().get(0));
              }
          }

          // 把所有结果合并成一行，作为最终 resultRows（让 FormatterAgent 能渲染）
          Map<String, Object> combined = new LinkedHashMap<>(stepResults);
          ctx.setResultRows(List.of(combined));
          ctx.setResultCount(1);
          ctx.setGeneratedSql(String.join(" ; ", executedSqls));
          ctx.setValidatedSql(String.join(" ; ", executedSqls));
          ctx.getExecutedAgents().add(getName());
          log.info("[AnalysisAgent] 完成: {}", stepResults);
      }
  }