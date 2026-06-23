package com.agentforge.framework.llm;

import com.agentforge.framework.metrics.LlmMetrics;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.time.Duration;

@Slf4j
@Component
public class LlmClientFactory {

    private final LlmConfig llmConfig;
    private final LlmMetrics llmMetrics;

    public LlmClientFactory(LlmConfig llmConfig, LlmMetrics llmMetrics) {
        this.llmConfig = llmConfig;
        this.llmMetrics = llmMetrics;
    }

    @Bean
    public ChatModel chatModel() {
        log.info("Creating ChatModel: baseUrl={}, model={}", llmConfig.getBaseUrl(), llmConfig.getChatModel());
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .apiKey(llmConfig.getApiKey())
                .modelName(llmConfig.getChatModel())
                .temperature(llmConfig.getTemperature())
                .timeout(Duration.ofSeconds(llmConfig.getTimeoutSeconds()));
        if (llmConfig.getTopP() != null) {
            builder.topP(llmConfig.getTopP());
        }
        if (llmConfig.getMaxTokens() != null) {
            builder.maxTokens(llmConfig.getMaxTokens());
        }
        // 用动态 Proxy 包装，统一记录所有 LLM 调用耗时/成败（不依赖具体接口方法）
        ChatModel real = builder.build();
        return (ChatModel) Proxy.newProxyInstance(
                ChatModel.class.getClassLoader(),
                new Class<?>[]{ChatModel.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(real, args);
                    }
                    long start = System.nanoTime();
                    try {
                        Object result = method.invoke(real, args);
                        llmMetrics.recordCall((System.nanoTime() - start) / 1_000_000, true, llmConfig.getChatModel());
                        return result;
                    } catch (Exception e) {
                        llmMetrics.recordCall((System.nanoTime() - start) / 1_000_000, false, llmConfig.getChatModel());
                        throw e;
                    }
                });
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        log.info("Creating StreamingChatModel: baseUrl={}, model={}", llmConfig.getBaseUrl(), llmConfig.getStreamingModel());
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .apiKey(llmConfig.getApiKey())
                .modelName(llmConfig.getStreamingModel())
                .temperature(llmConfig.getTemperature())
                .timeout(Duration.ofSeconds(llmConfig.getTimeoutSeconds()));
        if (llmConfig.getTopP() != null) {
            builder.topP(llmConfig.getTopP());
        }
        if (llmConfig.getMaxTokens() != null) {
            builder.maxTokens(llmConfig.getMaxTokens());
        }
        // Week 11：流式模型也走 Proxy 记录调用次数（流式调用立即返回，耗时含注册回调的开销）
        StreamingChatModel real = builder.build();
        return (StreamingChatModel) Proxy.newProxyInstance(
                StreamingChatModel.class.getClassLoader(),
                new Class<?>[]{StreamingChatModel.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(real, args);
                    }
                    long start = System.nanoTime();
                    try {
                        Object result = method.invoke(real, args);
                        llmMetrics.recordCall((System.nanoTime() - start) / 1_000_000, true, llmConfig.getStreamingModel());
                        return result;
                    } catch (Exception e) {
                        llmMetrics.recordCall((System.nanoTime() - start) / 1_000_000, false, llmConfig.getStreamingModel());
                        throw e;
                    }
                });
    }
}
