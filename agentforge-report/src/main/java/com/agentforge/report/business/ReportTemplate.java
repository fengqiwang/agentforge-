package com.agentforge.report.business;

  import lombok.Data;
  import java.time.LocalDateTime;
  import java.util.List;

  @Data
  public class ReportTemplate {
      private Long id;
      private String name;
      private String description;
      private List<QueryItem> queries;   // JSON: [{"name":"总体概况","sql":"SELECT ..."}]
      private String schedule;           // cron: "0 0 2 1 * ?"
      private Boolean enabled;
      private String createdBy;
      private LocalDateTime createdAt;
      private LocalDateTime updatedAt;

      @Data
      public static class QueryItem {
          private String name;
          private String sql;
      }
  }