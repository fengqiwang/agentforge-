package com.agentforge.common.model;

import lombok.Builder;
import lombok.Data;

@Data
  @Builder
  public class TableMatch {
      private String tableName;
      private String tableComment;
      private double score;
      private String description;
  }