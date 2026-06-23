package com.agentforge.common.model;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  import java.util.List;
  import java.util.Map;

@Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class ReportConfig {

      private Long id;
      private String name;
      private String description;
      private String sqlText;
      private ChartConfig chartConfig;
      private List<FilterConfig> filterConfig;
      private List<ColumnConfig> columnConfig;
      private String createdBy;
      private String shareToken;
      private Integer status;

      /**
       * SQL执行结果的前N行（前端图表和表格渲染的数据源）。
       * Chat页面和报表构建页面都需要此字段。
       */
      private List<Map<String, Object>> sampleRows;

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      public static class ChartConfig {
          private String type;
          private String xField;
          private List<String> yFields;
          private String title;
          private String valueField;
          private String nameField;
          private List<NumberCard> numberCards;
      }

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      public static class NumberCard {
          private String field;
          private String label;
          private String unit;
          private String format;
      }

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      public static class FilterConfig {
          private String type;
          private String field;
          private String label;
          private List<String> options;
          private String defaultValue;
      }

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      public static class ColumnConfig {
          private String field;
          private String label;
          private String format;
          private boolean visible;
          private String width;
          private boolean summarize;
      }
  }