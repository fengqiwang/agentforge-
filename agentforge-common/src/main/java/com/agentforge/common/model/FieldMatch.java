package com.agentforge.common.model;

import lombok.Builder;
import lombok.Data;

@Data
  @Builder
  public class FieldMatch {
      private String tableName;
      private String columnName;
      private String matchedKeyword;
      private String columnComment;
  }