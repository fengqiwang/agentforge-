package com.agentforge.framework.rag;

import com.agentforge.common.model.TableSchema;
import com.agentforge.framework.rag.schema.SchemaReader;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SchemaIndexer {

      private final SchemaReader schemaReader;
      private final EmbeddingModel embeddingModel;
      private final EmbeddingStore<TextSegment> schemaEmbeddingStore;
      private final JdbcTemplate jdbc;

    public SchemaIndexer(SchemaReader schemaReader, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> schemaEmbeddingStore, JdbcTemplate jdbc) {
        this.schemaReader = schemaReader;
        this.embeddingModel = embeddingModel;
        this.schemaEmbeddingStore = schemaEmbeddingStore;
        this.jdbc = jdbc;
    }

    /**
       * 索引核心业务表（8张）
       * 启动后手动调用或通过管理API触发
       */
      public void indexCoreTables() {
          List<TableSchema> tables = schemaReader.readCoreTables();
          log.info("开始索引 {} 张核心业务表...", tables.size());

          for (TableSchema table : tables) {
              indexTable(table);
          }
          log.info("核心业务表索引完成");
      }

      /**
       * 全量索引synthesis库所有表
       */
      public void indexAll() {
          List<TableSchema> tables = schemaReader.readAll();
          log.info("开始索引 {} 张表...", tables.size());

          for (TableSchema table : tables) {
              indexTable(table);
          }
          log.info("全量索引完成");
      }

      /**
       * 按表名重新索引单张核心表。
       */
      public void reindexTable(String tableName) {
          TableSchema target = schemaReader.readCoreTables().stream()
                  .filter(t -> tableName.equals(t.getTableName()))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("表不存在: " + tableName));
          indexTable(target);
      }

      /**
       * 单表索引
       */
      public void indexTable(TableSchema table) {
          String description = table.toDescription();
          log.debug("索引表 {}: {}", table.getTableName(),
                  description.substring(0, Math.min(100, description.length())));

          TextSegment segment = TextSegment.from(
                  description,
                  Metadata.from("table_name", table.getTableName())
                          .put("column_count", table.getColumns().size())
                          .put("table_comment", table.getTableComment() != null ? table.getTableComment() : "")
          );

          Embedding embedding = embeddingModel.embed(segment).content();
          schemaEmbeddingStore.add(embedding, segment);

          // 更新af_schema_index表状态
          jdbc.update("""
              INSERT INTO af_schema_index (table_name, column_count, table_comment, index_status)
              VALUES (?, ?, ?, 1)
              ON DUPLICATE KEY UPDATE column_count=?, table_comment=?, index_status=1, updated_at=NOW()
              """,
                  table.getTableName(),
                  table.getColumns().size(),
                  table.getTableComment(),
                  table.getColumns().size(),
                  table.getTableComment()
          );

          log.info("索引完成: {} ({}个字段)", table.getTableName(), table.getColumns().size());
      }
  }