package com.agentforge.common.model.log;

  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  import java.time.LocalDateTime;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public class SqlLogRecord {

      private Long id;
      private String sessionId;
      private String sqlText;
      private String tablesUsed;      // JSON 数组，如 ["syb_transuminfor","syb_merchant"]
      private Integer resultCount;     // 返回行数
      private Integer durationMs;      // 执行耗时
      private Boolean isValid;         // 是否通过安全校验
      private String level;            // TEMPLATE / FEW_SHOT / LLM
      private LocalDateTime createdAt;
  }