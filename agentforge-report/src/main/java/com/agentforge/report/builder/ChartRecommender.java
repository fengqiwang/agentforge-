package com.agentforge.report.builder;

  import com.agentforge.common.model.ReportConfig.*;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.ArrayList;
  import java.util.List;
  import java.util.Map;
  import java.util.stream.Collectors;

  @Slf4j
  @Component
  public class ChartRecommender {

      /**
       * 根据SQL和数据形状推荐图表
       */
      public ChartConfig recommend(String sql, List<ColumnConfig> columnTypes, List<Map<String, Object>> rows) {
          if (rows == null || rows.isEmpty()) {
              return null;
          }

          String upperSql = sql.toUpperCase();

          // 分类列和数值列
          List<ColumnConfig> categoryCols = columnTypes.stream()
                  .filter(c -> "text".equals(c.getFormat()) || "date".equals(c.getFormat()))
                  .collect(Collectors.toList());

          List<ColumnConfig> numericCols = columnTypes.stream()
                  .filter(c -> "number".equals(c.getFormat()) || "money".equals(c.getFormat())
                          || "percent".equals(c.getFormat()))
                  .collect(Collectors.toList());

          boolean hasGroupBy = upperSql.contains("GROUP BY");
          boolean hasOrderBy = upperSql.contains("ORDER BY");
          boolean hasLimit = upperSql.contains("LIMIT");
          boolean hasDateCategory = categoryCols.stream().anyMatch(c -> "date".equals(c.getFormat()));
          boolean hasPercentCol = columnTypes.stream().anyMatch(c -> "percent".equals(c.getFormat()));

          // ===== 规则1：结果只有1行 → 数字卡片 =====
          if (rows.size() == 1) {
              log.info("图表推荐：number_card（单行结果）");
              return ChartConfig.builder()
                      .type("number_card")
                      .numberCards(buildNumberCards(numericCols, rows.get(0)))
                      .build();
          }

          // ===== 规则2：无GROUP BY → 仅表格，无图表 =====
          if (!hasGroupBy) {
              log.info("图表推荐：无（明细数据，仅表格展示）");
              return null;
          }

          // ===== 规则3：GROUP BY时间字段 → 折线图（优先于饼图） =====
          if (hasDateCategory && !numericCols.isEmpty()) {
              String xField = categoryCols.stream()
                      .filter(c -> "date".equals(c.getFormat()))
                      .findFirst()
                      .map(ColumnConfig::getField)
                      .orElse(categoryCols.get(0).getField());

              log.info("图表推荐：line（GROUP BY时间）");
              return ChartConfig.builder()
                      .type("line")
                      .xField(xField)
                      .yFields(numericCols.stream().map(ColumnConfig::getField).collect(Collectors.toList()))
                      .title("趋势变化")
                      .build();
          }

          // ===== 规则4：含百分比列 + 分类 → 饼图 =====
          if (hasPercentCol && !categoryCols.isEmpty() && rows.size() <= 20) {
              log.info("图表推荐：pie（含百分比列）");
              return ChartConfig.builder()
                      .type("pie")
                      .nameField(categoryCols.get(0).getField())
                      .valueField(numericCols.isEmpty() ? null : numericCols.get(0).getField())
                      .title("占比分布")
                      .build();
          }

          // ===== 规则5：LIMIT + ORDER BY → 柱状图（排名） =====
          if (hasLimit && hasOrderBy && !categoryCols.isEmpty() && !numericCols.isEmpty()) {
              log.info("图表推荐：bar（排名场景）");
              return ChartConfig.builder()
                      .type("bar")
                      .xField(categoryCols.get(0).getField())
                      .yFields(List.of(numericCols.get(0).getField()))
                      .title("排名对比")
                      .build();
          }

          // ===== 规则6：1分类 + 多数值列 → 柱状图（多系列，自动分组） =====
          if (categoryCols.size() == 1 && numericCols.size() >= 2) {
              log.info("图表推荐：bar 多系列（1分类+多数值）");
              return ChartConfig.builder()
                      .type("bar")
                      .xField(categoryCols.get(0).getField())
                      .yFields(numericCols.stream().map(ColumnConfig::getField).collect(Collectors.toList()))
                      .title("多维对比")
                      .build();
          }

          // ===== 规则7：1分类 + 1数值 → 柱状图 =====
          if (categoryCols.size() == 1 && numericCols.size() == 1) {
              log.info("图表推荐：bar（1分类+1数值）");
              return ChartConfig.builder()
                      .type("bar")
                      .xField(categoryCols.get(0).getField())
                      .yFields(List.of(numericCols.get(0).getField()))
                      .title("分类统计")
                      .build();
          }

          // ===== 规则8：多分类 → 柱状图（取第一个分类） =====
          if (!categoryCols.isEmpty() && !numericCols.isEmpty()) {
              log.info("图表推荐：bar（多分类，取第一个）");
              return ChartConfig.builder()
                      .type("bar")
                      .xField(categoryCols.get(0).getField())
                      .yFields(List.of(numericCols.get(0).getField()))
                      .title("数据统计")
                      .build();
          }

          // ===== 兜底：无合适图表 =====
          log.info("图表推荐：无（无法匹配图表规则）");
          return null;
      }

      // ==================== 工具方法 ====================

      private List<NumberCard> buildNumberCards(List<ColumnConfig> numericCols, Map<String, Object> row) {
          List<NumberCard> cards = new ArrayList<>();
          for (ColumnConfig col : numericCols) {
              String format = col.getFormat();
              String unit = "money".equals(format) ? "元" : "percent".equals(format) ? "%" : "";
              cards.add(NumberCard.builder()
                      .field(col.getField())
                      .label(col.getLabel())
                      .unit(unit)
                      .format(format)
                      .build());
          }
          return cards;
      }
  }