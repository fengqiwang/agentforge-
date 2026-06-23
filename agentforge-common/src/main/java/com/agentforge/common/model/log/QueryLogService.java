package com.agentforge.common.model.log;

  import com.agentforge.common.model.log.SqlLogRecord;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.scheduling.annotation.Async;
  import org.springframework.stereotype.Service;

  import java.time.LocalDateTime;

  /**
   * SQL 执行日志服务
   * 作用：异步记录每条 SQL 的执行情况，用于审计和性能分析
   *
   * 异步写入：@Async 保证不阻塞主流程
   * 截断策略：SQL 文本不截断（需要完整回看），输入/输出截断到 2000 字符
   */
  @Slf4j
  @Service
  @RequiredArgsConstructor
  public class QueryLogService {

      private final JdbcTemplate jdbcTemplate;
      private final ObjectMapper objectMapper;

      /**
       * 异步记录 SQL 执行日志
       *
       * @param sessionId  会话ID
       * @param sqlText    执行的 SQL
       * @param tablesUsed 涉及的表（List → JSON）
       * @param resultCount 返回行数
       * @param durationMs 执行耗时（毫秒）
       * @param isValid    是否通过安全校验
       * @param level      生成级别 TEMPLATE/FEW_SHOT/LLM
       */
      @Async
      public void log(String sessionId, String sqlText, java.util.List<String> tablesUsed,
                       int resultCount, long durationMs, boolean isValid, String level) {
          try {
              String tablesJson = tablesUsed != null
                      ? objectMapper.writeValueAsString(tablesUsed) : null;

              jdbcTemplate.update(
                      "INSERT INTO af_sql_log " +
                      "(session_id, sql_text, tables_used, result_count, duration_ms, is_valid, created_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?)",
                      sessionId, sqlText, tablesJson, resultCount,
                      (int) durationMs, isValid, LocalDateTime.now()
              );

              log.debug("SQL日志已记录：session={}, 耗时={}ms, 行数={}", sessionId, durationMs, resultCount);
          } catch (Exception e) {
              log.warn("SQL日志写入失败（不影响业务）：{}", e.getMessage());
          }
      }

      /**
       * 简化版：只记录核心字段
       */
      @Async
      public void log(String sessionId, String sqlText, int resultCount,
                       long durationMs, boolean isValid) {
          log(sessionId, sqlText, null, resultCount, durationMs, isValid, null);
      }
  }