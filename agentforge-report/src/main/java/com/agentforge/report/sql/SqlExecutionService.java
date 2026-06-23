 package com.agentforge.report.sql;

  import com.agentforge.common.model.SqlGenerationResult;
  import com.agentforge.common.model.log.QueryLogService;
  import com.agentforge.framework.cache.SqlResultCache;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  import java.util.*;

  @Slf4j
  @Service
  @RequiredArgsConstructor
  public class SqlExecutionService {

      private final JdbcTemplate jdbcTemplate;
      private final SqlFixer sqlFixer;
      private final SqlResultCache sqlResultCache;
      private final QueryLogService queryLogService;

      private static final int QUERY_TIMEOUT_SECONDS = 30;
      private static final int MAX_FIX_RETRIES = 3;

      /**
       * 生成 + 执行 + 自动修复（最多3次）
       */
      public Map<String, Object> generateAndExecute(String question, SqlGenerationResult generated) {
          Map<String, Object> result = new LinkedHashMap<>();
          result.put("question", question);
          result.put("level", generated.getLevel());
          result.put("confidence", generated.getConfidence());
          result.put("explanation", generated.getExplanation());

          if (!generated.isMatched() || generated.getSql() == null || generated.getSql().isBlank()) {
              result.put("success", false);
              result.put("error", "SQL生成失败");
              return result;
          }

          String sql = generated.getSql();
          result.put("originalSql", sql);

          // 第一次执行
          ExecutionResult execResult = execute(sql);
          if (execResult.isSuccess()) {
              result.put("success", true);
              result.put("sql", sql);
              result.put("rows", execResult.getRows());
              result.put("rowCount", execResult.getRows().size());
              result.put("fixed", false);
              return result;
          }

          // 执行失败 → 尝试修复（最多3次）
          log.info("SQL执行失败，开始自动修复：{}", execResult.getError());
          String currentSql = sql;
          String currentError = execResult.getError();

          for (int i = 1; i <= MAX_FIX_RETRIES; i++) {
              SqlFixer.SqlFixResult fixResult = sqlFixer.fix(currentSql, currentError);
              if (!fixResult.isSuccess()) {
                  log.info("第{}次修复失败：{}", i, fixResult.getFixDescription());
                  break;
              }

              String fixedSql = fixResult.getFixedSql();
              ExecutionResult retryResult = execute(fixedSql);

              if (retryResult.isSuccess()) {
                  result.put("success", true);
                  result.put("sql", fixedSql);
                  result.put("rows", retryResult.getRows());
                  result.put("rowCount", retryResult.getRows().size());
                  result.put("fixed", true);
                  result.put("fixLevel", fixResult.getLevel());
                  result.put("fixDescription", fixResult.getFixDescription());
                  result.put("fixRetries", i);
                  return result;
              }

              // 修复后仍失败，用新的错误信息继续修复
              currentSql = fixedSql;
              currentError = retryResult.getError();
              log.info("第{}次修复后仍执行失败：{}", i, currentError);
          }

          // 全部重试失败
          result.put("success", false);
          result.put("sql", currentSql);
          result.put("error", currentError);
          result.put("fixAttempted", true);
          return result;
      }

      /**
       * 执行SQL（只读事务 + 超时控制 + Redis缓存 + 执行日志）
       */
      @Transactional(readOnly = true, timeout = QUERY_TIMEOUT_SECONDS)
      public ExecutionResult execute(String sql) {
          // 1. 查缓存
          List<Map<String, Object>> cached = sqlResultCache.get(sql);
          if (cached != null) {
              return ExecutionResult.success(cached);
          }

          // 2. 执行
          long start = System.currentTimeMillis();
          try {
              List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
              long duration = System.currentTimeMillis() - start;

              // 3. 缓存
              if (!rows.isEmpty()) {
                  sqlResultCache.put(sql, rows);
              }

              // 4. 记录日志（异步）
              queryLogService.log(null, sql, rows.size(), duration, true);

              return ExecutionResult.success(rows);
          } catch (Exception e) {
              long duration = System.currentTimeMillis() - start;
              String msg = truncateError(e.getMessage());

              // 记录失败日志
              queryLogService.log(null, sql, 0, duration, false);

              log.warn("SQL执行失败：{}", msg);
              return ExecutionResult.fail(msg);
          }
      }
      /**
       * 带会话ID的执行（记录日志时关联会话）
       */
      public ExecutionResult execute(String sql, String sessionId) {
          // 和上面逻辑一样，只是日志调用时传入 sessionId
          long start = System.currentTimeMillis();
          try {
              List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
              long duration = System.currentTimeMillis() - start;

              if (!rows.isEmpty()) {
                  sqlResultCache.put(sql, rows);
              }

              queryLogService.log(sessionId, sql, rows.size(), duration, true);
              return ExecutionResult.success(rows);
          } catch (Exception e) {
              long duration = System.currentTimeMillis() - start;
              queryLogService.log(sessionId, sql, 0, duration, false);
              return ExecutionResult.fail(truncateError(e.getMessage()));
          }
      }

      /**
       * 精简错误信息，保留头部（错误类型）和尾部（位置信息）
       */
      private String truncateError(String msg) {
          if (msg == null) return "Unknown error";
          if (msg.length() <= 300) return msg;
          // 保留前200字符（错误类型）和后100字符（位置信息）
          return msg.substring(0, 200) + "...[truncated]..." + msg.substring(msg.length() - 100);
      }

      @lombok.Data
      @lombok.Builder
      @lombok.AllArgsConstructor
      @lombok.NoArgsConstructor
      public static class ExecutionResult {
          private boolean success;
          private List<Map<String, Object>> rows;
          private String error;

          public static ExecutionResult success(List<Map<String, Object>> rows) {
              return ExecutionResult.builder().success(true).rows(rows).build();
          }

          public static ExecutionResult fail(String error) {
              return ExecutionResult.builder().success(false).error(error).build();
          }
      }
  }