package com.agentforge.report.sql.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
  public class TemplateParam {
      private String name;
      private String type;         // daterange / daterange_int / enum / string / int
      private String column;       // SQL中对应的列名
      private List<String> values; // enum类型的可选值
      @JsonProperty("default_value")
      private Object defaultValue; // 默认值
  }