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
  public class AgentLogRecord {

      private Long id;
      private String sessionId;
      private String agentName;        // Agent 名称：SqlGenerator / SqlFixer / ChartRecommender 等
      private String input;            // 输入（截断）
      private String output;           // 输出（截断）
      private Integer durationMs;      // 执行耗时
      private Integer tokenUsed;       // 消耗 token 数
      private String status;           // success / failed / timeout
      private String errorMsg;         // 错误信息
      private LocalDateTime createdAt;
  }