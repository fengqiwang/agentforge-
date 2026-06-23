package com.agentforge.framework.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class EmbeddingConfig {

    @Value("${agentforge.llm.embedding-base-url:${agentforge.llm.base-url}}")
    private String embeddingBaseUrl;

    @Value("${agentforge.llm.embedding-api-key:${agentforge.llm.api-key}}")
    private String embeddingApiKey;

    @Value("${agentforge.llm.embedding-model:embedding-3}")
    private String embeddingModelName;

    @Value("${agentforge.chromadb.url:http://localhost:8000}")
    private String chromadbUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .modelName(embeddingModelName)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> schemaEmbeddingStore() {
        return newStore("schema_index", Duration.ofSeconds(60), true);
    }

    @Bean("sqlExampleEmbeddingStore")
    public EmbeddingStore<?> sqlExampleEmbeddingStore() {
        return newStore("sql_examples", Duration.ofDays(60), false);
    }

    private ChromaEmbeddingStore newStore(String collection, Duration timeout, boolean log) {
        var builder = ChromaEmbeddingStore.builder()
                .baseUrl(chromadbUrl).collectionName(collection).timeout(timeout);
        if (log) builder.logRequests(true).logResponses(true);
        return builder.build();
    }
}
