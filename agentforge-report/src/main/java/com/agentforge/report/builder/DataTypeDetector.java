  package com.agentforge.report.builder;

  import com.agentforge.common.model.ReportConfig.ColumnConfig;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.math.BigDecimal;
  import java.time.LocalDate;
  import java.time.format.DateTimeFormatter;
  import java.time.format.DateTimeParseException;
  import java.util.*;
  import java.util.stream.Collectors;

  @Slf4j
  @Component
  public class DataTypeDetector {

      private static final Map<String, String> KNOWN_LABELS = Map.ofEntries(
              Map.entry("total_amount", "交易总额"),
              Map.entry("total_count", "交易笔数"),
              Map.entry("trans_count", "交易笔数"),
              Map.entry("merchant_count", "商户数量"),
              Map.entry("success_rate", "成功率"),
              Map.entry("total_fee", "手续费总额"),
              Map.entry("fee_rate", "手续费率"),
              Map.entry("daily_amount", "日交易额"),
              Map.entry("daily_income", "日收益"),
              Map.entry("avg_amount", "平均金额"),
              Map.entry("total_withdrawal", "提现总额"),
              Map.entry("fee_difference", "手续费差额"),
              Map.entry("settlement_amount", "清算金额"),
              Map.entry("pending_amount", "待清算金额"),
              Map.entry("failed_count", "失败笔数"),
              Map.entry("inactive_count", "不活跃数"),
              Map.entry("new_merchant_count", "新增商户数"),
              Map.entry("city", "城市"),
              Map.entry("cusname", "商户名称"),
              Map.entry("cusid", "商户号"),
              Map.entry("custype", "商户类型"),
              Map.entry("transtype", "交易类型"),
              Map.entry("tranno", "交易流水号"),
              Map.entry("tranamt", "交易金额"),
              Map.entry("feepad", "手续费"),
              Map.entry("income", "收益"),
              Map.entry("withdrawamt", "提现金额"),
              Map.entry("settledate", "结算日期"),
              Map.entry("channel", "渠道"),
              Map.entry("period", "周期"),
              Map.entry("respcode", "响应码"),
              Map.entry("respmsg", "响应信息"),
              Map.entry("status", "状态"),
              Map.entry("terminal_count", "终端数量")
      );

      /**
       * 分析SQL执行结果的列，自动检测类型并生成列配置
       */
      public List<ColumnConfig> detect(List<Map<String, Object>> rows) {
          if (rows == null || rows.isEmpty()) {
              return List.of();
          }

          // 保留列顺序（JdbcTemplate返回LinkedHashMap）
          List<String> columns = new ArrayList<>(rows.get(0).keySet());
          Map<String, Set<Object>> uniqueValues = extractUniqueValues(rows, columns);

          List<ColumnConfig> configs = new ArrayList<>();
          for (String field : columns) {
              Class<?> type = detectType(field, rows);
              ColumnConfig config = buildColumnConfig(field, type);
              configs.add(config);
          }

          log.info("列类型检测完成：{} 列", configs.size());
          configs.forEach(c -> log.debug("  {} → format={}", c.getField(), c.getFormat()));
          return configs;
      }

      /**
       * 判断某列是否为枚举列（不重复值 ≤ 20个）
       */
      public boolean isEnumColumn(String field, List<Map<String, Object>> rows) {
          long uniqueCount = rows.stream()
                  .map(row -> row.get(field))
                  .filter(Objects::nonNull)
                  .distinct()
                  .count();
          return uniqueCount > 0 && uniqueCount <= 20;
      }

      /**
       * 获取列的所有不重复值（用于生成下拉选项）
       */
      public List<String> getUniqueValues(String field, List<Map<String, Object>> rows) {
          return rows.stream()
                  .map(row -> row.get(field))
                  .filter(Objects::nonNull)
                  .map(Object::toString)
                  .distinct()
                  .limit(50)
                  .collect(Collectors.toList());
      }

      // ==================== 内部方法 ====================

      private Class<?> detectType(String column, List<Map<String, Object>> rows) {
          String lower = column.toLowerCase();
          int sampleSize = Math.min(rows.size(), 100);

          int intCount = 0, decimalCount = 0, dateCount = 0, stringCount = 0;

          for (int i = 0; i < sampleSize; i++) {
              Object value = rows.get(i).get(column);
              if (value == null) continue;

              if (value instanceof Integer || value instanceof Long) {
                  // 关键修正：列名含date且值为8位数字 → 识别为日期
                  if (lower.contains("date") && isIntDate(value)) {
                      dateCount++;
                  } else {
                      intCount++;
                  }
              } else if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
                  decimalCount++;
              } else if (value instanceof java.sql.Date || value instanceof java.util.Date) {
                  dateCount++;
              } else if (value instanceof String strValue) {
                  if (isDateString(strValue)) {
                      dateCount++;
                  } else {
                      stringCount++;
                  }
              }
          }

          int total = intCount + decimalCount + dateCount + stringCount;
          if (total == 0) return String.class;

          // 优先用字段名推断
          if (lower.contains("amt") || lower.contains("amount") || lower.contains("fee")
                  || lower.contains("income") || lower.contains("money") || lower.contains("withdrawal")) {
              return BigDecimal.class;
          }
          if (lower.contains("rate") || lower.contains("ratio") || lower.contains("percent")) {
              return BigDecimal.class;
          }
          if (lower.contains("date") || lower.contains("time")) {
              if (dateCount > 0) return java.sql.Date.class;
              if (intCount > 0 && isIntDateColumn(column, rows)) return java.sql.Date.class;
          }

          // 按采样多数决定
          if (dateCount >= intCount && dateCount >= decimalCount && dateCount >= stringCount) return
  java.sql.Date.class;
          if (intCount > decimalCount && intCount > stringCount) return Long.class;
          if (decimalCount > intCount && decimalCount > stringCount) return BigDecimal.class;

          return String.class;
      }

      /**
       * 判断Integer/Long值是否为YYYYMMDD格式的日期
       */
      private boolean isIntDate(Object value) {
          long v = ((Number) value).longValue();
          return v >= 19000101 && v <= 20991231;
      }

      /**
       * 判断整列是否都是YYYYMMDD格式
       */
      private boolean isIntDateColumn(String column, List<Map<String, Object>> rows) {
          int dateLike = 0, total = 0;
          for (int i = 0; i < Math.min(rows.size(), 20); i++) {
              Object val = rows.get(i).get(column);
              if (val instanceof Number) {
                  total++;
                  if (isIntDate(val)) dateLike++;
              }
          }
          return total > 0 && dateLike >= total * 0.8; // 80%以上是日期格式
      }

      private boolean isDateString(String value) {
          if (value == null || value.isBlank()) return false;
          try { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE); return true; } catch (DateTimeParseException
  ignored) {}
          if (value.length() == 8 && value.matches("\\d{8}")) return true;
          return false;
      }

      private Map<String, Set<Object>> extractUniqueValues(List<Map<String, Object>> rows, List<String> columns) {
          Map<String, Set<Object>> result = new LinkedHashMap<>();
          for (String col : columns) {
              Set<Object> uniques = rows.stream()
                      .map(row -> row.get(col))
                      .filter(Objects::nonNull)
                      .limit(1000)
                      .collect(Collectors.toCollection(LinkedHashSet::new));
              result.put(col, uniques);
          }
          return result;
      }

      private ColumnConfig buildColumnConfig(String field, Class<?> type) {
          return ColumnConfig.builder()
                  .field(field)
                  .label(generateLabel(field))
                  .format(resolveFormat(field, type))
                  .visible(true)
                  .width("auto")
                  .summarize(shouldSummarize(field, type))
                  .build();
      }

      private String resolveFormat(String field, Class<?> type) {
          String lower = field.toLowerCase();

          if (lower.contains("amt") || lower.contains("amount") || lower.contains("fee")
                  || lower.contains("income") || lower.contains("money") || lower.contains("withdrawal"))
              return "money";

          if (lower.contains("rate") || lower.contains("ratio") || lower.contains("percent"))
              return "percent";

          if (lower.contains("date") || lower.contains("time"))
              return "date";

          if (type == Long.class || type == Integer.class)
              return "number";

          if (type == BigDecimal.class || type == Double.class)
              return "number";

          return "text";
      }

      private boolean shouldSummarize(String field, Class<?> type) {
          if (type != Long.class && type != BigDecimal.class && type != Double.class) return false;
          String lower = field.toLowerCase();
          // ID类字段不求和
          return !lower.equals("id") && !lower.endsWith("_id") && !lower.equals("cusid");
      }

      private String generateLabel(String field) {
          return KNOWN_LABELS.getOrDefault(field, field);
      }
  }