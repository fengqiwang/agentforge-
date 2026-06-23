package com.agentforge.framework.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 高频报表/分析结果缓存。
 *
 * <p>缓存维度结果、报表查询结果等可复用数据，TTL 5 分钟。
 * key = "agentforge:cache:" + 业务前缀 + 内容哈希。
 *
 * <p>命中统计：{@link #hits}/{@link #misses}，供监控/测试观察命中率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "agentforge:cache:";

    public final AtomicLong hits = new AtomicLong();
    public final AtomicLong misses = new AtomicLong();

    /** 读缓存，反序列化为指定类型；未命中返回 null。 */
    public <T> T get(String prefix, String key, Class<T> type) {
        try {
            String raw = redis.opsForValue().get(PREFIX + prefix + ":" + key);
            if (raw == null) {
                misses.incrementAndGet();
                return null;
            }
            hits.incrementAndGet();
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("缓存读取失败 prefix={} key={}: {}", prefix, key, e.getMessage());
            return null;
        }
    }

    /** 写缓存。 */
    public void put(String prefix, String key, Object value) {
        try {
            redis.opsForValue().set(PREFIX + prefix + ":" + key,
                    objectMapper.writeValueAsString(value), TTL);
        } catch (Exception e) {
            log.warn("缓存写入失败 prefix={} key={}: {}", prefix, key, e.getMessage());
        }
    }

    /** 失效指定前缀下某 key。 */
    public void evict(String prefix, String key) {
        redis.delete(PREFIX + prefix + ":" + key);
    }

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
}
