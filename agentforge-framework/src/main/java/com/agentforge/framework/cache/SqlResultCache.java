package com.agentforge.framework.cache;

  import com.fasterxml.jackson.core.type.TypeReference;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.data.redis.core.StringRedisTemplate;
  import org.springframework.stereotype.Component;

  import java.nio.charset.StandardCharsets;
  import java.security.MessageDigest;
  import java.util.List;
  import java.util.Map;
  import java.util.concurrent.TimeUnit;

  /**
   * SQL 结果缓存
   * 作用：相同 SQL 短时间内不重复执行，直接从 Redis 取结果
   *
   * Key：agentforge:sqlcache:{md5(sql)}
   * Value：JSON 序列化的 List<Map<String, Object>>
   * TTL：5 分钟
   */
  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class SqlResultCache {

      private static final String KEY_PREFIX = "agentforge:sqlcache:";
      private static final long TTL_MINUTES = 5;

      private final StringRedisTemplate redisTemplate;
      private final ObjectMapper objectMapper;

      /**
       * 获取缓存
       * @param sql SQL 语句
       * @return 缓存的结果，null 表示未命中
       */
      @SuppressWarnings("unchecked")
      public List<Map<String, Object>> get(String sql) {
          String key = KEY_PREFIX + md5(sql);
          String json = redisTemplate.opsForValue().get(key);
          if (json == null) {
              return null;
          }

          try {
              List<Map<String, Object>> result = objectMapper.readValue(json,
                      new TypeReference<List<Map<String, Object>>>() {});
              log.debug("SQL缓存命中：key={}", key);
              return result;
          } catch (Exception e) {
              log.warn("缓存反序列化失败，清除脏数据：{}", e.getMessage());
              redisTemplate.delete(key);
              return null;
          }
      }

      /**
       * 写入缓存
       * @param sql SQL 语句
       * @param rows 查询结果
       */
      public void put(String sql, List<Map<String, Object>> rows) {
          try {
              String key = KEY_PREFIX + md5(sql);
              String json = objectMapper.writeValueAsString(rows);
              redisTemplate.opsForValue().set(key, json, TTL_MINUTES, TimeUnit.MINUTES);
              log.debug("SQL结果已缓存：key={}, 行数={}", key, rows.size());
          } catch (Exception e) {
              log.warn("缓存写入失败（不影响业务）：{}", e.getMessage());
          }
      }

      /**
       * 清除指定 SQL 的缓存
       */
      public void evict(String sql) {
          String key = KEY_PREFIX + md5(sql);
          redisTemplate.delete(key);
      }

      /**
       * 清除所有 SQL 缓存
       */
      public void evictAll() {
          var keys = redisTemplate.keys(KEY_PREFIX + "*");
          if (keys != null && !keys.isEmpty()) {
              redisTemplate.delete(keys);
              log.info("清除所有SQL缓存：{} 条", keys.size());
          }
      }

      private String md5(String input) {
          try {
              MessageDigest md = MessageDigest.getInstance("MD5");
              byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
              StringBuilder sb = new StringBuilder();
              for (byte b : digest) {
                  sb.append(String.format("%02x", b));
              }
              return sb.toString();
          } catch (Exception e) {
              // MD5 一定存在，不会到这里
              return String.valueOf(input.hashCode());
          }
      }
  }