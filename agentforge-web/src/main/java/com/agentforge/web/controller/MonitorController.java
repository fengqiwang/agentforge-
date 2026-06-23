package com.agentforge.web.controller;

import com.agentforge.framework.cache.ReportCacheService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Week 11 监控辅助接口：缓存命中率、关键指标摘要。
 * 主要指标明细见 /actuator/prometheus 与 /actuator/metrics。
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final ReportCacheService reportCacheService;
    private final MeterRegistry meterRegistry;

    /** 缓存命中统计。 */
    @GetMapping("/cache-stats")
    public Map<String, Object> cacheStats() {
        long hits = reportCacheService.hits();
        long misses = reportCacheService.misses();
        long total = hits + misses;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hits", hits);
        m.put("misses", misses);
        m.put("total", total);
        m.put("hitRate", total == 0 ? 0 : (double) hits / total);
        return m;
    }

    /** 关键指标摘要（计数/计时快照）。 */
    @GetMapping("/metrics-summary")
    public Map<String, Object> metricsSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            if (name == null || !name.startsWith("agentforge_")) continue;
            if (meter instanceof Timer t) {
                m.put(name + "_count", t.count());
                m.put(name + "_mean_ms", Math.round(t.mean(TimeUnit.MILLISECONDS) * 100) / 100.0);
            } else if (meter instanceof Counter c) {
                m.put(name + "_count", Math.round(c.count()));
            }
        }
        return m;
    }
}
