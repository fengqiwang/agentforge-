  package com.agentforge.common.model;

  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  /**
   * 时间范围，settledate字段统一用int格式YYYYMMDD
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public class TimeRange {

      /** 起始日期，int格式，如 20260401 */
      private int start;

      /** 结束日期，int格式，如 20260430 */
      private int end;

      /** 原始中文表述，如 "上个月" */
      private String expression;

      /** varchar类型场景下的起始日期，如 "2026-04-01" */
      private String startStr;

      /** varchar类型场景下的结束日期，如 "2026-04-30" */
      private String endStr;
  }