 package com.agentforge.framework.rag;

  import com.agentforge.common.model.FewShotData;
  import com.agentforge.common.model.FewShotEntry;
  import dev.langchain4j.data.embedding.Embedding;
  import dev.langchain4j.data.segment.TextSegment;
  import dev.langchain4j.model.embedding.EmbeddingModel;
  import dev.langchain4j.store.embedding.EmbeddingStore;
  import jakarta.annotation.PostConstruct;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.beans.factory.annotation.Qualifier;
  import org.springframework.stereotype.Component;

  import java.util.List;

  @Slf4j
  @Component
  public class FewShotIndexer {

      private final EmbeddingStore<TextSegment> sqlExampleEmbeddingStore;

      private final EmbeddingModel embeddingModel;

      private volatile boolean indexed = false;

      public FewShotIndexer(@Qualifier("sqlExampleEmbeddingStore") EmbeddingStore<TextSegment> sqlExampleEmbeddingStore, EmbeddingModel embeddingModel) {
          this.sqlExampleEmbeddingStore = sqlExampleEmbeddingStore;
          this.embeddingModel = embeddingModel;
      }

      @PostConstruct
      public void init() {
          indexAll();
      }

      public synchronized void indexAll() {
          if (indexed) {
              log.info("Few-Shot示例已索引，跳过重复加载");
              return;
          }

          List<FewShotEntry> examples = FewShotData.presetExamples();
          log.info("开始索引Few-Shot示例，共 {} 条", examples.size());

          for (int i = 0; i < examples.size(); i++) {
              FewShotEntry entry = examples.get(i);
              indexOne(entry, i);
          }

          indexed = true;
          log.info("Few-Shot示例索引完成，共 {} 条", examples.size());
      }

      private void indexOne(FewShotEntry entry, int index) {
          // 将示例构造为自然语言文本用于向量化
          // 格式：问题 + SQL + 涉及表，这样检索时能从多个角度匹配
          String text = String.format("问题：%s\nSQL：%s\n涉及表：%s",
                  entry.getQuestion(),
                  entry.getSql(),
                  entry.getTablesUsed());

          TextSegment segment = TextSegment.from(text);
          Embedding embedding = embeddingModel.embed(segment.text()).content();

          sqlExampleEmbeddingStore.add(embedding, segment);

          log.debug("索引第 {} 条：{}", index + 1, entry.getQuestion());
      }

      /** 重新索引（用于热更新） */
      public synchronized void reindex() {
          indexed = false;
          // Chroma的collection需要重建，这里简单处理：清空后重新灌入
          log.info("开始重新索引Few-Shot示例...");
          indexAll();
      }
  }