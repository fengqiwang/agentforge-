  package com.agentforge.report.business;

  import lombok.Data;
  import java.time.LocalDateTime;
  import java.util.Map;

  @Data
  public class BusinessReport {
      private Long id;
      private Long templateId;
      private String templateName;
      private String reportDate;
      private String status;              // GENERATING / COMPLETED / FAILED
      private Map<String, Object> dataResult;  // {"总体概况": {rows:[], count:10}, ...}
      private String aiAnalysis;
      private LocalDateTime createdAt;
  }