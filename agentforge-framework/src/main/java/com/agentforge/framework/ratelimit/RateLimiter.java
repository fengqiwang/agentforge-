package com.agentforge.framework.ratelimit;

  import lombok.extern.slf4j.Slf4j;
  import org.springframework.data.redis.core.StringRedisTemplate;
  import org.springframework.stereotype.Component;

  import java.time.Instant;
  import java.util.concurrent.TimeUnit;

  /**
   * API 限流器
   * 作用：滑动窗口限流，每用户每分钟最多 maxRequests 次请求
   *
   * 实现原理：
   * - Redis List 存储每次请求的时间戳
   * - 每次请求时，先清除窗口外的旧时间戳，再检查剩余数量
   * - 超过限制则拒绝
   */
  @Slf4j
  @Component
  public class RateLimiter {

      private final StringRedisTemplate redisTemplate;
      private final int maxRequests;
      private final int windowSeconds;

      private static final String KEY_PREFIX = "agentforge:ratelimit:";

      public RateLimiter(StringRedisTemplate redisTemplate,
                         @org.springframework.beans.factory.annotation.Value("${agentforge.ratelimit.max-requests:100}") int maxRequests,
                         @org.springframework.beans.factory.annotation.Value("${agentforge.ratelimit.window-seconds:60}") int windowSeconds) {
          this.redisTemplate = redisTemplate;
          this.maxRequests = maxRequests;
          this.windowSeconds = windowSeconds;
      }

      /**
       * 检查是否允许请求
       * @param userId 用户ID（或 IP）
       * @return true=允许 false=限流
       */
      public boolean allowRequest(String userId) {
          return allowRequest(userId, maxRequests, windowSeconds);
      }

      /**
       * 通用限流检查
       * @param userId 标识（用户ID / IP / sessionId）
       * @param maxRequests 窗口内最大请求数
       * @param windowSeconds 窗口大小（秒）
       */
      public boolean allowRequest(String userId, int maxRequests, int windowSeconds) {
          String key = KEY_PREFIX + userId;
          long now = Instant.now().toEpochMilli();
          long windowStart = now - windowSeconds * 1000L;

          // 1. 清除窗口外的旧记录
          redisTemplate.opsForList().remove(key, 0, String.valueOf(windowStart));

          // 2. 检查当前窗口内的请求数
          Long count = redisTemplate.opsForList().size(key);
          if (count != null && count >= maxRequests) {
              log.warn("API限流触发：userId={}, count={}, max={}", userId, count, maxRequests);
              return false;
          }

          // 3. 记录本次请求
          redisTemplate.opsForList().rightPush(key, String.valueOf(now));
          redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

          return true;
      }

      /**
       * 获取剩余可用次数
       */
      public int remainingRequests(String userId) {
          String key = KEY_PREFIX + userId;
          long now = Instant.now().toEpochMilli();
          long windowStart = now - windowSeconds * 1000L;

          redisTemplate.opsForList().remove(key, 0, String.valueOf(windowStart));
          Long count = redisTemplate.opsForList().size(key);
          return maxRequests - (count != null ? count.intValue() : 0);
      }
  }