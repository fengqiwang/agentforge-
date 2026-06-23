package com.agentforge.framework.rag;

import com.agentforge.common.model.TableMatch;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SchemaRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> schemaEmbeddingStore;

    public SchemaRetriever(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> schemaEmbeddingStore) {
        this.embeddingModel = embeddingModel;
        this.schemaEmbeddingStore = schemaEmbeddingStore;
    }

    /**
     * 根据用户问题检索相关表
     * @param question 用户问题（如"上个月交易总额"）
     * @param topK 返回数量
     * @return 相关表列表（按相似度排序）
     */
    public List<TableMatch> retrieve(String question, int topK) {
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        List<EmbeddingMatch<TextSegment>> matches = schemaEmbeddingStore.search(
                EmbeddingSearchRequest
                        .builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(topK)
                        .minScore(0.5).build()
        ).matches();

        return matches.stream()
                .map(match -> TableMatch.builder()
                        .tableName(match.embedded().metadata().getString("table_name"))
                        .tableComment(match.embedded().metadata().getString("table_comment"))
                        .score(match.score())
                        .description(match.embedded().text())
                        .build())
                .toList();
    }

    public List<TableMatch> retrieve(String question) {
        return retrieve(question, 10);
    }
}
