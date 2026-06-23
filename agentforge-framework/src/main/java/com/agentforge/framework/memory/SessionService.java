 package com.agentforge.framework.memory;

  import com.agentforge.common.model.conversation.ChatSession;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.jdbc.support.GeneratedKeyHolder;
  import org.springframework.jdbc.support.KeyHolder;
  import org.springframework.stereotype.Service;

  import java.sql.PreparedStatement;
  import java.sql.Statement;
  import java.time.LocalDateTime;
  import java.util.List;
  import java.util.Map;
  import java.util.UUID;

  @Slf4j
  @Service
  @RequiredArgsConstructor
  public class SessionService {

      private final JdbcTemplate jdbcTemplate;
      private final ChatMemoryManager chatMemoryManager;

      /**
       * 创建新会话
       */
      public ChatSession create(Long userId, String title) {
          String sessionId = UUID.randomUUID().toString().replace("-", "");
          KeyHolder keyHolder = new GeneratedKeyHolder();

          jdbcTemplate.update(conn -> {
              PreparedStatement ps = conn.prepareStatement(
                      "INSERT INTO af_conversation (session_id, user_id, title, status) " +
                      "VALUES (?, ?, ?, 1)",
                      Statement.RETURN_GENERATED_KEYS
              );
              ps.setString(1, sessionId);
              ps.setLong(2, userId != null ? userId : 0L);
              ps.setString(3, title);
              return ps;
          }, keyHolder);

          return ChatSession.builder()
                  .id(keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null)
                  .sessionId(sessionId)
                  .userId(userId)
                  .title(title)
                  .status(1)
                  .createdAt(LocalDateTime.now())
                  .updatedAt(LocalDateTime.now())
                  .build();
      }

      /**
       * 查询用户的所有会话
       */
      public List<ChatSession> listByUser(Long userId) {
          return jdbcTemplate.query(
                  "SELECT id, session_id, user_id, title, status, created_at, updated_at " +
                  "FROM af_conversation WHERE user_id = ? AND status = 1 " +
                  "ORDER BY updated_at DESC",
                  (rs, rowNum) -> ChatSession.builder()
                          .id(rs.getLong("id"))
                          .sessionId(rs.getString("session_id"))
                          .userId(rs.getLong("user_id"))
                          .title(rs.getString("title"))
                          .status(rs.getInt("status"))
                          .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                          .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                          .build(),
                  userId
          );
      }

      /**
       * 获取会话详情
       */
      public ChatSession getBySessionId(String sessionId) {
          List<ChatSession> list = jdbcTemplate.query(
                  "SELECT id, session_id, user_id, title, status, created_at, updated_at " +
                  "FROM af_conversation WHERE session_id = ?",
                  (rs, rowNum) -> ChatSession.builder()
                          .id(rs.getLong("id"))
                          .sessionId(rs.getString("session_id"))
                          .userId(rs.getLong("user_id"))
                          .title(rs.getString("title"))
                          .status(rs.getInt("status"))
                          .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                          .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                          .build(),
                  sessionId
          );
          return list.isEmpty() ? null : list.get(0);
      }

      /**
       * 更新会话标题（首次对话后用 LLM 生成摘要标题）
       */
      public void updateTitle(String sessionId, String title) {
          jdbcTemplate.update(
                  "UPDATE af_conversation SET title = ?, updated_at = NOW() " +
                  "WHERE session_id = ?",
                  title, sessionId
          );
      }

      /**
       * 更新会话活跃时间
       */
      public void touch(String sessionId) {
          jdbcTemplate.update(
                  "UPDATE af_conversation SET updated_at = NOW() WHERE session_id = ?",
                  sessionId
          );
      }

      /**
       * 归档会话（逻辑删除）
       */
      public void archive(String sessionId) {
          jdbcTemplate.update(
                  "UPDATE af_conversation SET status = 2, updated_at = NOW() " +
                  "WHERE session_id = ?",
                  sessionId
          );
          chatMemoryManager.clear(sessionId);
      }

      /**
       * 删除会话（物理删除）
       */
      public void delete(String sessionId) {
          jdbcTemplate.update(
                  "DELETE FROM af_conversation WHERE session_id = ?", sessionId);
          chatMemoryManager.clear(sessionId);
      }
  }