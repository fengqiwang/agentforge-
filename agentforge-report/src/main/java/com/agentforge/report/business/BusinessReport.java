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
      /** 报告状态：{@link ReportStatus#GENERATING} / {@link ReportStatus#COMPLETED} / {@link ReportStatus#FAILED} */
      private String status;
      private Map<String, Object> dataResult;  // {"总体概况": {rows:[], count:10}, ...}
      private String aiAnalysis;
      /** 执行失败时的错误信息，仅 status=FAILED 时有值 */
      private String errorMessage;
      private LocalDateTime createdAt;
  }