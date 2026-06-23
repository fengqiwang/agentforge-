package com.agentforge.framework.rag;

  import com.agentforge.common.model.TimeRange;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.time.DayOfWeek;
  import java.time.LocalDate;
  import java.time.format.DateTimeFormatter;
  import java.time.temporal.TemporalAdjusters;
  import java.util.LinkedHashMap;
  import java.util.Map;
  import java.util.Objects;
  import java.util.function.Supplier;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;
  import java.util.stream.Stream;

@Slf4j
  @Component
  public class TimeRangeParser {

      private static final DateTimeFormatter INT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
      private static final DateTimeFormatter STR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

      // ==================== 对外入口 ====================

      /**
       * 从用户问题中解析时间范围
       */
      public TimeRange parse(String question) {
          if (question == null || question.isBlank()) {
              return defaultRange();
          }

          String text = question.trim();

          // 按优先级依次匹配
          TimeRange result = tryParse(text);
          if (result != null) {
              log.debug("时间解析：'{}' → {} ~ {}", text, result.getStart(), result.getEnd());
              return result;
          }

          // 未匹配到时间表达式，默认本月
          return defaultRange();
      }

      // ==================== 具体解析逻辑 ====================

      private TimeRange tryParse(String text) {
          return Stream.<Supplier<TimeRange>>of(
                          () -> matchExactDate(text),
                          () -> matchRecentDays(text),
                          () -> matchRelativeDay(text),
                          () -> matchWeek(text),
                          () -> matchMonth(text),
                          () -> matchYear(text)
                  ).map(Supplier::get)
                  .filter(Objects::nonNull)
                  .findFirst()
                  .orElse(null);
      }
      // ==================== 精确日期 ====================

      private TimeRange matchExactDate(String text) {
          // 匹配 "2024年3月15日" "2024年03月15日"
          Pattern p = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
          Matcher m = p.matcher(text);
          if (m.find()) {
              LocalDate date = LocalDate.of(
                      Integer.parseInt(m.group(1)),
                      Integer.parseInt(m.group(2)),
                      Integer.parseInt(m.group(3)));
              return buildRange(date, date, m.group());
          }

          // 匹配纯数字 "20240315"
          Pattern p2 = Pattern.compile("\\b(\\d{8})\\b");
          Matcher m2 = p2.matcher(text);
          if (m2.find()) {
              try {
                  LocalDate date = LocalDate.parse(m2.group(1), INT_FMT);
                  return buildRange(date, date, m2.group());
              } catch (Exception ignored) {}
          }

          return null;
      }

      // ==================== 近N天 ====================

      private TimeRange matchRecentDays(String text) {
          Pattern p = Pattern.compile("近(\\d+)天");
          Matcher m = p.matcher(text);
          if (m.find()) {
              int days = Integer.parseInt(m.group(1));
              LocalDate end = LocalDate.now().minusDays(1);     // 昨天
              LocalDate start = end.minusDays(days - 1);
              return buildRange(start, end, m.group());
          }
          return null;
      }

      // ==================== 相对日 ====================

      private TimeRange matchRelativeDay(String text) {
          LocalDate today = LocalDate.now();

          if (text.contains("今天") || text.contains("今日")) {
              return buildRange(today, today, "今天");
          }
          if (text.contains("昨天") || text.contains("昨日")) {
              LocalDate y = today.minusDays(1);
              return buildRange(y, y, "昨天");
          }
          if (text.contains("前天")) {
              LocalDate d = today.minusDays(2);
              return buildRange(d, d, "前天");
          }

          return null;
      }

      // ==================== 周 ====================

      private TimeRange matchWeek(String text) {
          LocalDate today = LocalDate.now();


          // 上上周
          if (text.contains("上上周")) {
              LocalDate monday = today.minusWeeks(2).with(DayOfWeek.MONDAY);
              LocalDate sunday = monday.plusDays(6);
              return buildRange(monday, sunday, "上上周");
          }

          // 本周一~今天
          if (text.contains("本周") || text.contains("这周") || text.contains("这周")) {
              LocalDate monday = today.with(DayOfWeek.MONDAY);
              return buildRange(monday, today, "本周");
          }

          // 上周一~上周日
          if (text.contains("上周")) {
              LocalDate lastMonday = today.minusWeeks(1).with(DayOfWeek.MONDAY);
              LocalDate lastSunday = lastMonday.plusDays(6);
              return buildRange(lastMonday, lastSunday, "上周");
          }
          return null;
      }

      // ==================== 月 ====================

      private TimeRange matchMonth(String text) {
          LocalDate today = LocalDate.now();

          // 上上个月
          if (text.contains("上上个月") || text.contains("前个月") || text.contains("上上个月")) {
              LocalDate month = today.minusMonths(2);
              return monthRange(month, "上上个月");
          }

          // 上个月
          if (text.contains("上个月") || text.contains("上月") || text.contains("上一月")) {
              LocalDate month = today.minusMonths(1);
              return monthRange(month, "上个月");
          }

          // 本月
          if (text.contains("本月") || text.contains("这个月") || text.contains("当月")) {
              return monthRange(today, "本月");
          }

          // 具体月份：3月、3月份、去年3月
          Pattern p = Pattern.compile("(去年)?(\\d{1,2})月份?");
          Matcher m = p.matcher(text);
          if (m.find()) {
              int year = m.group(1) != null ? today.getYear() - 1 : today.getYear();
              int month = Integer.parseInt(m.group(2));
              if (month >= 1 && month <= 12) {
                  // 如果指定月份是未来的月份，往前推一年
                  LocalDate target = LocalDate.of(year, month, 1);
                  if (target.isAfter(today)) {
                      target = LocalDate.of(year - 1, month, 1);
                  }
                  return monthRange(target, m.group());
              }
          }

          return null;
      }

      // ==================== 年 ====================

      private TimeRange matchYear(String text) {
          LocalDate today = LocalDate.now();

          if (text.contains("今年") || text.contains("本年")) {
              LocalDate start = LocalDate.of(today.getYear(), 1, 1);
              return buildRange(start, today, "今年");
          }

          if (text.contains("去年") || text.contains("上年")) {
              LocalDate start = LocalDate.of(today.getYear() - 1, 1, 1);
              LocalDate end = LocalDate.of(today.getYear() - 1, 12, 31);
              return buildRange(start, end, "去年");
          }

          return null;
      }

      // ==================== 工具方法 ====================

      private TimeRange monthRange(LocalDate anyDayInMonth, String expression) {
          LocalDate start = anyDayInMonth.withDayOfMonth(1);
          LocalDate end = anyDayInMonth.with(TemporalAdjusters.lastDayOfMonth());
          return buildRange(start, end, expression);
      }

      private TimeRange buildRange(LocalDate start, LocalDate end, String expression) {
          return TimeRange.builder()
                  .start(toInt(start))
                  .end(toInt(end))
                  .startStr(start.format(STR_FMT))
                  .endStr(end.format(STR_FMT))
                  .expression(expression)
                  .build();
      }

      private int toInt(LocalDate date) {
          return Integer.parseInt(date.format(INT_FMT));
      }

      private TimeRange defaultRange() {
          return monthRange(LocalDate.now(), "本月");
      }

      // ==================== 批量测试入口 ====================

      /**
       * 测试用：解析并输出可读结果
       */
      public Map<String, String> testParse(String question) {
          TimeRange range = parse(question);
          Map<String, String> result = new LinkedHashMap<>();
          result.put("question", question);
          result.put("expression", range.getExpression());
          result.put("start_int", String.valueOf(range.getStart()));
          result.put("end_int", String.valueOf(range.getEnd()));
          result.put("start_str", range.getStartStr());
          result.put("end_str", range.getEndStr());
          return result;
      }
  }