package com.agentforge.common.model;

import lombok.Builder;
import lombok.Data;

@Data
  @Builder
  public class TableRelation {
      private String leftTable;
      private String leftColumn;
      private String rightTable;
      private String rightColumn;
      private String relationName;

      public static TableRelation of(String leftTable, String leftCol,
                                      String rightTable, String rightCol,
                                      String name) {
          return TableRelation.builder()
                  .leftTable(leftTable)
                  .leftColumn(leftCol)
                  .rightTable(rightTable)
                  .rightColumn(rightCol)
                  .relationName(name)
                  .build();
      }
  }