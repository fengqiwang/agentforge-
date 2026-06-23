  package com.agentforge.common.model;

  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public class FewShotEntry {
      private String question;
      private String sql;
      private String explanation;
      private String tablesUsed; // 逗号分隔，如 "syb_transuminfor,syb_merchant"
  }