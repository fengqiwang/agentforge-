package com.agentforge.framework.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 报表/分析生成 Micrometer 指标。
 *
 * <p>指标：
 * <ul>
 *   <li>{@code agentforge_report_generation_duration_seconds}（Timer，tag: intent, success）</li>
 *   <li>{@code agentforge_pipeline_total}（Counter，tag: intent, success）</li>
 *   <li>{@code agentforge_analysis_duration_seconds}（Timer，tag: dimension）— 流水分析维度耗时</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReportMetrics {

    private final MeterRegistry registry;

    /** 记录一次 Pipeline 执行（报表/工单/分析）。 */
    public void recordPipeline(long durationMs, String intent, boolean success) {
        Tags tags = Tags.of("intent", intent == null ? "unknown" : intent)
                .and("success", String.valueOf(success));
        Timer.builder("agentforge_report_generation_duration_seconds")
                .tags(tags).description("报表生成耗时").register(registry)
                .record(java.time.Duration.ofMillis(durationMs));
        registry.counter("agentforge_pipeline_total", tags).increment();
    }

    /** 记录一次流水分析维度执行。 */
    public void recordAnalysis(long durationMs, String dimension) {
        Timer.builder("agentforge_analysis_duration_seconds")
                .tag("dimension", dimension == null ? "unknown" : dimension)
                .description("流水分析维度耗时").register(registry)
                .record(java.time.Duration.ofMillis(durationMs));
    }
}
