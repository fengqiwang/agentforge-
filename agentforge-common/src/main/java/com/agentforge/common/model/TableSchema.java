package com.agentforge.common.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
  @Builder
  public class TableSchema {
      private String tableName;
      private String tableComment;
      @Builder.Default
      private List<ColumnSchema> columns = new ArrayList<>();

      public TableSchema addColumn(ColumnSchema col) {
          this.columns.add(col);
          return this;
      }

      /**
       * 生成向量化用的描述文档
       */
      public String toDescription() {
          StringBuilder sb = new StringBuilder();
          sb.append("表名: ").append(tableName);
          if (tableComment != null && !tableComment.isEmpty()) {
              sb.append("（").append(tableComment).append("）");
          }
          sb.append("\n字段:\n");
          for (ColumnSchema col : columns) {
              sb.append("  - ").append(col.getColumnName());
              sb.append("(").append(col.getColumnType()).append(")");
              if (col.getColumnComment() != null && !col.getColumnComment().isEmpty()) {
                  sb.append(" — ").append(col.getColumnComment());
              }
              if (col.isKey()) sb.append(" [主键]");
              sb.append("\n");
          }
          return sb.toString();
      }
  }