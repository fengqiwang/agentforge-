package com.agentforge.framework.memory;

  import com.agentforge.common.model.conversation.ChatMessage;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.jdbc.support.GeneratedKeyHolder;
  import org.springframework.jdbc.support.KeyHolder;
  import org.springframework.scheduling.annotation.Async;
  import org.springframework.stereotype.Repository;

  import java.sql.PreparedStatement;
  import java.sql.Statement;
  import java.time.LocalDateTime;
  import java.util.List;
  import java.util.Map;

  @Slf4j
  @Repository
  @RequiredArgsConstructor
  public class MysqlMessageStore {

      private final JdbcTemplate jdbcTemplate;

      /**
       * 异步保存消息
       * 作用：@Async 避免阻塞主流程，消息写入不阻塞用户响应
       */
      @Async
      public void saveAsync(ChatMessage message) {
          KeyHolder keyHolder = new GeneratedKeyHolder();
          jdbcTemplate.update(conn -> {
              PreparedStatement ps = conn.prepareStatement(
                      "INSERT INTO af_message (session_id, role, content, metadata, token_count, created_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?)",
                      Statement.RETURN_GENERATED_KEYS
              );
              ps.setString(1, message.getSessionId());
              ps.setString(2, message.getRole());
              ps.setString(3, message.getContent());
              ps.setString(4, message.getMetadata());
              if (message.getTokenCount() != null) {
                  ps.setInt(5, message.getTokenCount());
              } else {
                  ps.setNull(5, java.sql.Types.INTEGER);
              }
              ps.setObject(6, message.getCreatedAt() != null
                      ? message.getCreatedAt() : LocalDateTime.now());
              return ps;
          }, keyHolder);

          log.debug("消息已持久化：session={}, role={}, id={}",
                  message.getSessionId(), message.getRole(),
                  keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null);
      }

      /**
       * 查询会话的历史消息（按时间升序）
       */
      public List<ChatMessage> getMessages(String sessionId) {
          return jdbcTemplate.query(
                  "SELECT id, session_id, role, content, metadata, token_count, created_at " +
                  "FROM af_message WHERE session_id = ? ORDER BY created_at ASC",
                  (rs, rowNum) -> ChatMessage.builder()
                          .id(rs.getLong("id"))
                          .sessionId(rs.getString("session_id"))
                          .role(rs.getString("role"))
                          .content(rs.getString("content"))
                          .metadata(rs.getString("metadata"))
                          .tokenCount(rs.getObject("token_count") != null
                                  ? rs.getInt("token_count") : null)
                          .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                          .build(),
                  sessionId
          );
      }

      /**
       * 查询最近 N 条消息
       */
      public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
          return jdbcTemplate.query(
                  "SELECT id, session_id, role, content, metadata, token_count, created_at " +
                  "FROM af_message WHERE session_id = ? " +
                  "ORDER BY created_at DESC LIMIT ?",
                  (rs, rowNum) -> ChatMessage.builder()
                          .id(rs.getLong("id"))
                          .sessionId(rs.getString("session_id"))
                          .role(rs.getString("role"))
                          .content(rs.getString("content"))
                          .metadata(rs.getString("metadata"))
                          .tokenCount(rs.getObject("token_count") != null
                                  ? rs.getInt("token_count") : null)
                          .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                          .build(),
                  sessionId, limit
          );
      }

      /**
       * 删除会话的所有消息
       */
      public void deleteBySessionId(String sessionId) {
          jdbcTemplate.update("DELETE FROM af_message WHERE session_id = ?", sessionId);
      }
  }