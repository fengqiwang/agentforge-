package com.agentforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
  @AllArgsConstructor
  public class FieldMapping {
      private String columnName;
      private String columnComment;
      private List<String> keywords;
  }