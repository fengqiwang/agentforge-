package com.agentforge.framework.config;

  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.data.redis.connection.RedisConnectionFactory;
  import org.springframework.data.redis.core.StringRedisTemplate;

  /**
   * Redis 配置
   * 作用：使用 StringRedisTemplate，Key/Value 都是 String（JSON 格式）
   *
   * 为什么不用 RedisTemplate<String, Object>？
   * - JDK 默认序列化可读性差、跨语言不兼容
   * - StringRedisTemplate + JSON 序列化更通用、可调试
   */
  @Configuration
  public class RedisConfig {

      @Bean
      public StringRedisTemplate stringRedisTemplate(
              RedisConnectionFactory connectionFactory) {
          return new StringRedisTemplate(connectionFactory);
      }
  }