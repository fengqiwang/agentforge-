package com.agentforge.workflow.agent;

  import com.agentforge.report.sql.SqlExecutionService;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
  import io.github.resilience4j.retry.annotation.Retry;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.List;
  import java.util.Map;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class SqlExecutorAgent implements Agent {

      private final SqlExecutionService executionService;

      @Override
      public String getName() { return "SqlExecutorAgent"; }

      /**
       * SQL 执行 Agent — Pipeline 第 5 站
       *
       * 职责：
       *  - 在只读事务 + 30s 超时下执行 SQL
       *  - 命中 Redis 缓存直接返回
       *  - 异步写查询日志
       *
       * 输入（从 ctx 读）：
       *  - validatedSql   （优先）或 generatedSql
       *  - sessionId
       *
       * 输出（写到 ctx）：
       *  - queryResult     ExecutionResult(success, rows, error)
       *  - resultRows      List<Map<String,Object>>
       *  - resultCount     行数
       *
       * 容错（覆盖 P2-08 超时降级）：
       *  - @Retry(name="sql-executor")        DB 异常重试 3 次（仅对连接异常，不重试 SQL 错误）
       *  - @CircuitBreaker(name="sql-executor") DB 持续不可用时熔断
       *  - 超时由 @Transactional(timeout=30) 兜底（在 delegate 上）
       *  - SQL 错误（语法/列名）不会重试（exceptionPredicate 过滤）
       *
       * 覆盖验收：P2-08（超时降级，DB 卡 30s 自动断）
       */
      @Override
      @Retry(name = "sql-executor", fallbackMethod = "fallback")
      @CircuitBreaker(name = "sql-executor")
      public void execute(AgentContext ctx) {
          String sql = ctx.getValidatedSql() != null ? ctx.getValidatedSql() : ctx.getGeneratedSql();
          if (sql == null || sql.isBlank()) {
              ctx.setErrorMessage("无 SQL 可执行");
              return;
          }

          log.info("[SqlExecutorAgent] 执行 SQL: {}", sql);
          try {
              SqlExecutionService.ExecutionResult result = executionService.execute(sql, ctx.getSessionId());
              ctx.setQueryResult(result);
              if (result.isSuccess() && result.getRows() != null) {
                  ctx.setResultRows(result.getRows());
                  ctx.setResultCount(result.getRows().size());
                  log.info("[SqlExecutorAgent] 成功 rows={}", result.getRows().size());
              } else {
                  ctx.setErrorMessage(result.getError());
                  log.warn("[SqlExecutorAgent] 失败: {}", result.getError());
              }
          } catch (Exception e) {
              log.error("[SqlExecutorAgent] 异常", e);
              ctx.setErrorMessage("执行异常: " + e.getMessage());
          }
          ctx.getExecutedAgents().add(getName());
      }
  }