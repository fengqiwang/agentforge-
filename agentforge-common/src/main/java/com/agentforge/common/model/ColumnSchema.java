package com.agentforge.common.model;

import lombok.Builder;
import lombok.Data;

@Data
  @Builder
  public class ColumnSchema {
      private String columnName;
      private String columnType;
      private String columnComment;
      private boolean nullable;
      private boolean isKey;
  }