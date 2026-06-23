package com.agentforge.report.sql.template;

import lombok.Data;

import java.util.List;

@Data
  public class SqlTemplate {
      private String name;
      private String pattern;
      private List<String> keywords;
      private String templateSql;
      private List<TemplateParam> params;
      private String category;
      private List<String> tablesUsed;
  }