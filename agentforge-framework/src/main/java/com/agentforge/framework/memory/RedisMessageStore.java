package com.agentforge.framework.memory;

  import com.agentforge.common.model.conversation.ChatMessage;
  import com.fasterxml.jackson.core.JsonProcessingException;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.data.redis.core.StringRedisTemplate;
  import org.springframework.stereotype.Repository;

  import java.time.Duration;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.concurrent.TimeUnit;

  @Slf4j
  @Repository
  @RequiredArgsConstructor
  public class RedisMessageStore {

      private static final String KEY_PREFIX = "agentforge:session:";
      private static final String KEY_SUFFIX = ":messages";
      private static final long TTL_HOURS = 24;
      /** Redis 中最多保留的消息数（超过则裁剪头部旧消息） */
      private static final int MAX_REDIS_MESSAGES = 50;

      private final StringRedisTemplate redisTemplate;
      private final ObjectMapper objectMapper;

      /**
       * 追加一条消息到 Redis List，并裁剪到 MAX_REDIS_MESSAGES 条
       * 作用：每条消息序列化为 JSON，push 到 List 尾部，超出上限则移除最旧消息
       */
      public void append(String sessionId, ChatMessage message) {
          String key = KEY_PREFIX + sessionId + KEY_SUFFIX;
          try {
              String json = objectMapper.writeValueAsString(message);
              redisTemplate.opsForList().rightPush(key, json);
              // 裁剪：保留最近 50 条，删除头部旧消息
              redisTemplate.opsForList().trim(key, -MAX_REDIS_MESSAGES, -1);
              redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
          } catch (JsonProcessingException e) {
              log.error("Redis消息序列化失败：{}", e.getMessage());
          }
      }

      /**
       * 读取会话的全部消息
       */
      public List<ChatMessage> getMessages(String sessionId) {
          String key = KEY_PREFIX + sessionId + KEY_SUFFIX;
          List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
          if (jsonList == null || jsonList.isEmpty()) {
              return new ArrayList<>();
          }

          List<ChatMessage> messages = new ArrayList<>();
          for (String json : jsonList) {
              try {
                  messages.add(objectMapper.readValue(json, ChatMessage.class));
              } catch (JsonProcessingException e) {
                  log.warn("Redis消息反序列化失败：{}", e.getMessage());
              }
          }
          return messages;
      }

      /**
       * 读取最近 N 条消息
       * 作用：恢复内存时只取最近的，避免加载全部历史
       */
      public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
          String key = KEY_PREFIX + sessionId + KEY_SUFFIX;
          Long size = redisTemplate.opsForList().size(key);
          if (size == null || size == 0) {
              return new ArrayList<>();
          }

          long start = Math.max(0, size - limit);
          List<String> jsonList = redisTemplate.opsForList().range(key, start, size - 1);
          if (jsonList == null) {
              return new ArrayList<>();
          }

          List<ChatMessage> messages = new ArrayList<>();
          for (String json : jsonList) {
              try {
                  messages.add(objectMapper.readValue(json, ChatMessage.class));
              } catch (JsonProcessingException e) {
                  log.warn("Redis消息反序列化失败：{}", e.getMessage());
              }
          }
          return messages;
      }

      /**
       * 删除会话缓存
       */
      public void delete(String sessionId) {
          String key = KEY_PREFIX + sessionId + KEY_SUFFIX;
          redisTemplate.delete(key);
      }
  }