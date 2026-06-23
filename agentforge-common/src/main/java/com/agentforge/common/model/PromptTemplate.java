  package com.agentforge.common.model;

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
  public class PromptTemplate {
      private String name;
      private String system;
      private List<String> rules;
      private List<Map<String, String>> fewShots;
      /** 输入模板：放置变量占位符，如 ${original_sql}、${error_message} */
      private String inputTemplate;
      private String outputFormat;

      /**
       * 将模板拼接为完整的Prompt文本
       */
      public String toPromptText() {
          StringBuilder sb = new StringBuilder();

          if (system != null && !system.isBlank()) {
              sb.append(system).append("\n\n");
          }

          if (rules != null && !rules.isEmpty()) {
              sb.append("## 规则\n");
              for (int i = 0; i < rules.size(); i++) {
                  sb.append(i + 1).append(". ").append(rules.get(i)).append("\n");
              }
              sb.append("\n");
          }

          if (fewShots != null && !fewShots.isEmpty()) {
              sb.append("## 参考示例\n");
              for (Map<String, String> shot : fewShots) {
                  shot.forEach((k, v) -> sb.append(k).append("：").append(v).append("\n"));
                  sb.append("\n");
              }
          }

          if (inputTemplate != null && !inputTemplate.isBlank()) {
              sb.append(inputTemplate).append("\n\n");
          }

          if (outputFormat != null && !outputFormat.isBlank()) {
              sb.append("## 输出格式\n").append(outputFormat);
          }

          return sb.toString();
      }
  }