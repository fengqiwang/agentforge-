package com.agentforge.framework.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LLM 调用 Micrometer 指标。
 *
 * <p>指标：
 * <ul>
 *   <li>{@code agentforge_llm_call_duration_seconds}（Timer，tag: success, model）— 调用耗时</li>
 *   <li>{@code agentforge_llm_call_total}（Counter，tag: success, model）— 调用次数</li>
 * </ul>
 *
 * <p>通过 {@code MetricsChatModelProxy} 包装 ChatModel，所有 LLM 调用统一在此记录。
 */
@Component
@RequiredArgsConstructor
public class LlmMetrics {

    private final MeterRegistry registry;

    /** 记录一次 LLM 调用。durationMs 耗时毫秒；success 是否成功；model 模型名。 */
    public void recordCall(long durationMs, boolean success, String model) {
        Tags tags = Tags.of("success", String.valueOf(success)).and("model", model == null ? "unknown" : model);
        Timer.builder("agentforge_llm_call_duration_seconds")
                .tags(tags)
                .description("LLM 调用耗时")
                .register(registry)
                .record(java.time.Duration.ofMillis(durationMs));
        registry.counter("agentforge_llm_call_total", tags).increment();
    }
}
