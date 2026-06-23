package com.agentforge.common.model.log;

  import com.agentforge.common.model.log.AgentLogRecord;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.scheduling.annotation.Async;
  import org.springframework.stereotype.Service;

  import java.time.LocalDateTime;

  /**
   * Agent 执行日志服务
   * 作用：异步记录每个 Agent 的执行情况，用于链路追踪和性能分析
   *
   * 截断策略：
   * - input：最多 2000 字符
   * - output：最多 2000 字符
   * - errorMsg：最多 500 字符
   */
  @Slf4j
  @Service
  @RequiredArgsConstructor
  public class AgentLogService {

      private final JdbcTemplate jdbcTemplate;

      private static final int MAX_INPUT_LENGTH = 2000;
      private static final int MAX_ERROR_LENGTH = 500;

      /**
       * 异步记录 Agent 执行日志
       */
      @Async
      public void log(String sessionId, String agentName, String input,
                       String output, long durationMs, Integer tokenUsed,
                       String status, String errorMsg) {
          try {
              jdbcTemplate.update(
                      "INSERT INTO af_agent_log " +
                      "(session_id, agent_name, input, output, duration_ms, " +
                      "token_used, status, error_msg, created_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                      sessionId, agentName,
                      truncate(input, MAX_INPUT_LENGTH),
                      truncate(output, MAX_INPUT_LENGTH),
                      (int) durationMs, tokenUsed, status,
                      truncate(errorMsg, MAX_ERROR_LENGTH),
                      LocalDateTime.now()
              );

              log.debug("Agent日志已记录：session={}, agent={}, status={}, 耗时={}ms",
                      sessionId, agentName, status, durationMs);
          } catch (Exception e) {
              log.warn("Agent日志写入失败（不影响业务）：{}", e.getMessage());
          }
      }

      /**
       * 记录成功
       */
      @Async
      public void logSuccess(String sessionId, String agentName,
                              String input, String output,
                              long durationMs, Integer tokenUsed) {
          log(sessionId, agentName, input, output, durationMs,
                  tokenUsed, "success", null);
      }

      /**
       * 记录失败
       */
      @Async
      public void logFailure(String sessionId, String agentName,
                              String input, long durationMs, String errorMsg) {
          log(sessionId, agentName, input, null, durationMs,
                  null, "failed", errorMsg);
      }

      private String truncate(String text, int maxLen) {
          if (text == null) return null;
          if (text.length() <= maxLen) return text;
          return text.substring(0, maxLen) + "...[truncated]";
      }
  }