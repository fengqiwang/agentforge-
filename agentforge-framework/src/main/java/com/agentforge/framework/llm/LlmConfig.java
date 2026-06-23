package com.agentforge.framework.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agentforge.llm")
public class LlmConfig {
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String streamingModel;
    private String embeddingModel;

    private Double temperature = 0.7;
    private Double topP;
    private Integer maxTokens;
    private Integer timeoutSeconds = 60;

    /** Embedding 独立配置（智谱） */
    private String embeddingBaseUrl;
    private String embeddingApiKey;
}
