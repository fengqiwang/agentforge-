package com.agentforge.framework.rag;

  import dev.langchain4j.data.embedding.Embedding;
  import dev.langchain4j.data.segment.TextSegment;
  import dev.langchain4j.model.embedding.EmbeddingModel;
  import dev.langchain4j.store.embedding.EmbeddingMatch;
  import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
  import dev.langchain4j.store.embedding.EmbeddingStore;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.beans.factory.annotation.Qualifier;
  import org.springframework.stereotype.Component;

  import java.util.Comparator;
  import java.util.List;
  import java.util.Objects;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;
  import java.util.stream.Collectors;

  @Slf4j
  @Component
  public class FewShotRetriever {

      private final EmbeddingStore<TextSegment> sqlExampleEmbeddingStore;

      private final EmbeddingModel embeddingModel;

      private static final double MIN_SCORE = 0.6;

      public FewShotRetriever(@Qualifier("sqlExampleEmbeddingStore") EmbeddingStore<TextSegment> sqlExampleEmbeddingStore, EmbeddingModel embeddingModel) {
          this.sqlExampleEmbeddingStore = sqlExampleEmbeddingStore;
          this.embeddingModel = embeddingModel;
      }

      /**
       * 检索与用户问题最相似的Few-Shot示例
       *
       * @param question 用户问题
       * @param topK     返回数量
       * @return 匹配的Few-Shot条目列表（按相似度降序）
       */
      public List<FewShotMatch> retrieve(String question, int topK) {
          Embedding questionEmbedding = embeddingModel.embed(question).content();

          List<EmbeddingMatch<TextSegment>> matches =
                  sqlExampleEmbeddingStore.search(EmbeddingSearchRequest
                          .builder()
                          .queryEmbedding(questionEmbedding)
                          .maxResults(topK)
                          .minScore(MIN_SCORE).build()).matches();

          List<FewShotMatch> results = matches.stream()
                  .map(this::parseMatch)
                  .filter(Objects::nonNull)
                  .sorted(Comparator.comparingDouble(FewShotMatch::getScore).reversed())
                  .collect(Collectors.toList());

          log.info("Few-Shot检索：问题='{}'，命中 {} 条（top {}）", question, results.size(), topK);
          results.forEach(r -> log.debug("  [{}] score={:.3f} SQL={}",
                  r.getQuestion(), r.getScore(), r.getSql().substring(0, Math.min(50, r.getSql().length()))));

          return results;
      }

      private FewShotMatch parseMatch(EmbeddingMatch<TextSegment> match) {
          String text = match.embedded().text();
          return FewShotMatch.builder()
                  .score(match.score())
                  .question(extractField(text, "问题："))
                  .sql(extractField(text, "SQL："))
                  .tablesUsed(extractField(text, "涉及表："))
                  .build();
      }

      private String extractField(String text, String prefix) {
          int start = text.indexOf(prefix);
          if (start < 0) return "";
          start += prefix.length();

          // 找到下一个字段的位置
          int end = text.length();
          for (String nextPrefix : List.of("\nSQL：", "\n涉及表：", "\n问题：")) {
              int next = text.indexOf(nextPrefix, start);
              if (next > start && next < end) {
                  end = next;
              }
          }
          return text.substring(start, end).trim();
      }
      @lombok.Data
      @lombok.Builder
      @lombok.NoArgsConstructor
      @lombok.AllArgsConstructor
      public static class FewShotMatch {
          private double score;
          private String question;
          private String sql;
          private String tablesUsed;
      }
  }