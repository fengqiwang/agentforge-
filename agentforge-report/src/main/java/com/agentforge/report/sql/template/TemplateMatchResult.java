package com.agentforge.report.sql.template;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
  @Builder
  public class TemplateMatchResult {
      private boolean matched;
      private String templateName;
      private String sql;          // 模板SQL（含{conditions}、{limit}占位符，后续由SqlGeneratorService填充）
      private String category;
      private List<String> tablesUsed;
  }