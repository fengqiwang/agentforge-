 package com.agentforge.report.builder;

  import com.agentforge.common.model.ReportConfig.ColumnConfig;
  import com.agentforge.common.model.ReportConfig.FilterConfig;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import net.sf.jsqlparser.JSQLParserException;
  import net.sf.jsqlparser.expression.*;
  import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
  import net.sf.jsqlparser.expression.operators.relational.*;
  import net.sf.jsqlparser.parser.CCJSqlParserUtil;
  import net.sf.jsqlparser.schema.Column;
  import net.sf.jsqlparser.statement.Statement;
  import net.sf.jsqlparser.statement.select.*;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.stereotype.Component;

  import java.util.*;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class FilterPanelGenerator {

      private final JdbcTemplate jdbcTemplate;

      /**
       * 已知枚举字段 → 加载下拉选项的SQL
       * 作用：为分类字段预定义查询语句，前端直接渲染为下拉选择框
       * 例如：city字段加载所有城市名称，用户在筛选面板选择城市后重新查询
       */
      private static final Map<String, String> ENUM_FIELD_QUERIES = Map.of(
              "city", "SELECT DISTINCT city FROM syb_merchant WHERE city IS NOT NULL AND city <> '' ORDER BY city LIMIT 50",
              "custype", "SELECT DISTINCT custype FROM syb_merchant WHERE custype IS NOT NULL AND custype <> '' ORDER BY custype LIMIT 20",
              "transtype", "SELECT DISTINCT transtype FROM syb_transuminfor WHERE transtype IS NOT NULL AND transtype <> '' ORDER BY transtype LIMIT 30",
              "producttype", "SELECT DISTINCT producttype FROM syb_transuminfor WHERE producttype IS NOT NULL AND producttype <> '' ORDER BY producttype LIMIT 30",
              "state", "SELECT DISTINCT state FROM syb_merchant WHERE state IS NOT NULL AND state <> '' ORDER BY state LIMIT 20",
              "busi_type", "SELECT DISTINCT busi_type FROM jxallinpay_busi_order WHERE busi_type IS NOT NULL AND busi_type <> '' ORDER BY busi_type LIMIT 20",
              "dept_name", "SELECT DISTINCT dept_name FROM sys_dept WHERE status = '0' AND del_flag = '0' ORDER BY dept_name LIMIT 50"
      );

      /**
       * 字段中文标签
       * 作用：筛选面板上显示的标签文本，让业务人员看懂每个筛选条件
       */
      private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
              java.util.Map.entry("city", "城市"),
              java.util.Map.entry("transtype", "交易类型"),
              java.util.Map.entry("producttype", "产品类型"),
              java.util.Map.entry("custype", "商户类型"),
              java.util.Map.entry("state", "商户状态"),
              java.util.Map.entry("busi_type", "业务类型"),
              java.util.Map.entry("dept_name", "部门"),
              java.util.Map.entry("settledate", "结算日期"),
              java.util.Map.entry("regdate", "注册日期"),
              java.util.Map.entry("createtime", "创建时间"),
              java.util.Map.entry("tranamt", "交易金额"),
              java.util.Map.entry("tranfee", "手续费"),
              java.util.Map.entry("amount", "笔数"),
              java.util.Map.entry("status", "状态"),
              java.util.Map.entry("cusid", "商户号"),
              java.util.Map.entry("cusname", "商户名称"));

      /**
       * 主入口：从SQL中提取筛选参数
       *
       * @param sql         原始SQL
       * @param columnTypes 列类型配置（来自DataTypeDetector）
       * @return 筛选面板配置列表
       */
      public List<FilterConfig> generate(String sql, List<ColumnConfig> columnTypes) {
          if (sql == null || sql.isBlank()) return List.of();

          List<FilterConfig> filters = new ArrayList<>();

          // 1. 检查模板占位符（如 {start_date}）
          //    作用：模板SQL已经参数化，只需生成时间范围筛选器
          Set<String> templateParams = extractTemplateParams(sql);
          if (!templateParams.isEmpty()) {
              filters.add(buildDateRangeFilter());
              return filters;
          }

          // 2. 解析SQL提取WHERE条件字段
          //    作用：用JSqlParser AST精确提取WHERE中的字段名，不依赖正则
          Set<String> whereFields = extractWhereFields(sql);

          // 3. 根据字段类型生成筛选控件
          for (String field : whereFields) {
              FilterConfig filter = buildFilter(field, sql, columnTypes);
              if (filter != null) {
                  filters.add(filter);
              }
          }

          log.info("筛选面板生成完成：{} 个筛选条件", filters.size());
          return filters;
      }

      /**
       * 将SQL参数化为模板
       * 作用：把WHERE中的具体值替换为占位符，前端选择筛选条件后用新值替换占位符重新查询
       *
       * 注意：只替换WHERE子句中的值，不影响SELECT/LIMIT等其他部分
       *
       * @param sql     原始SQL
       * @param filters 筛选配置
       * @return 参数化后的SQL模板
       */
      public String parameterize(String sql, List<FilterConfig> filters) {
          if (filters == null || filters.isEmpty()) return sql;

          // 提取WHERE子句，只在这个范围内替换
          String upperSql = sql.toUpperCase();
          int whereIndex = upperSql.indexOf("WHERE");
          if (whereIndex < 0) return sql;

          String beforeWhere = sql.substring(0, whereIndex);
          String whereAndAfter = sql.substring(whereIndex);

          for (FilterConfig filter : filters) {
              String field = filter.getField();
              switch (filter.getType()) {
                  case "daterange" -> {
                      // 替换字段 >= 20260301（int日期）
                      whereAndAfter = whereAndAfter.replaceAll(
                              "(?i)" + Pattern.quote(field) + "\\s*>=\\s*\\d{8}",
                              field + " >= ${start_date}");
                      whereAndAfter = whereAndAfter.replaceAll(
                              "(?i)" + Pattern.quote(field) + "\\s*<=\\s*\\d{8}",
                              field + " <= ${end_date}");
                      // varchar格式的日期
                      whereAndAfter = whereAndAfter.replaceAll(
                              "(?i)" + Pattern.quote(field) + "\\s*>=\\s*'\\d{4}-\\d{2}-\\d{2}'",
                              field + " >= '${start_date_str}'");
                      whereAndAfter = whereAndAfter.replaceAll(
                              "(?i)" + Pattern.quote(field) + "\\s*<=\\s*'\\d{4}-\\d{2}-\\d{2}'",
                              field + " <= '${end_date_str}'");
                  }
                  case "select" -> {
                      // 替换 field = 'xxx'
                      whereAndAfter = whereAndAfter.replaceAll(
                              "(?i)" + Pattern.quote(field) + "\\s*=\\s*'[^']*'",
                              field + " = '${" + field + "}'");
                  }
                  case "text" -> {
                      whereAndAfter = whereAndAfter.replaceAll(
                              "(?i)" + Pattern.quote(field) + "\\s*=\\s*'[^']*'",
                              field + " = '${" + field + "}'");
                      whereAndAfter = whereAndAfter.replaceAll(
                              "(?i)" + Pattern.quote(field) + "\\s+LIKE\\s+'[^']*'",
                              field + " LIKE '%${" + field + "}%'");
                  }
              }
          }

          return beforeWhere + whereAndAfter;
      }

      // ==================== WHERE字段提取（JSqlParser AST） ====================

      /**
       * 用JSqlParser解析WHERE条件中的字段名
       * 作用：精确提取SQL中被用于过滤的字段，不依赖正则猜测
       */
      private Set<String> extractWhereFields(String sql) {
          Set<String> fields = new LinkedHashSet<>();
          try {
              Statement stmt = CCJSqlParserUtil.parse(sql);
              if (!(stmt instanceof Select select)) return fields;
              extractFromSelectBody(select.getSelectBody(), fields);
          } catch (JSQLParserException e) {
              log.debug("SQL解析失败，跳过WHERE字段提取：{}", e.getMessage());
          }
          return fields;
      }

      /**
       * 递归处理SelectBody，支持UNION等复合查询
       */
      private void extractFromSelectBody(Select selectBody, Set<String> fields) {
          if (selectBody instanceof PlainSelect plain) {
              Expression where = plain.getWhere();
              if (where != null) {
                  extractFieldsFromExpression(where, fields);
              }
          } else if (selectBody instanceof SetOperationList setOp) {
              // UNION查询：提取每个子查询的WHERE字段
              for (Select sub : setOp.getSelects()) {
                  extractFromSelectBody(sub, fields);
              }
          }
      }

      /**
       * 递归遍历WHERE表达式树，提取所有字段名
       * 作用：处理 AND/OR 嵌套的复杂条件
       */
      private void extractFieldsFromExpression(Expression expr, Set<String> fields) {
          if (expr instanceof AndExpression and) {
              extractFieldsFromExpression(and.getLeftExpression(), fields);
              extractFieldsFromExpression(and.getRightExpression(), fields);
          } else if (expr instanceof EqualsTo eq) {
              addColumnField(eq.getLeftExpression(), fields);
          } else if (expr instanceof GreaterThan gt) {
              addColumnField(gt.getLeftExpression(), fields);
          } else if (expr instanceof GreaterThanEquals gte) {
              addColumnField(gte.getLeftExpression(), fields);
          } else if (expr instanceof MinorThan lt) {
              addColumnField(lt.getLeftExpression(), fields);
          } else if (expr instanceof MinorThanEquals lte) {
              addColumnField(lte.getLeftExpression(), fields);
          } else if (expr instanceof LikeExpression like) {
              addColumnField(like.getLeftExpression(), fields);
          } else if (expr instanceof InExpression in) {
              addColumnField(in.getLeftExpression(), fields);
          } else if (expr instanceof Between bet) {
              addColumnField(bet.getLeftExpression(), fields);
          }
      }

      private void addColumnField(Expression expr, Set<String> fields) {
          if (expr instanceof Column col) {
              fields.add(col.getColumnName().toLowerCase());
          }
      }

      // ==================== 工具方法 ====================

      private Set<String> extractTemplateParams(String sql) {
          Set<String> params = new HashSet<>();
          Pattern p = Pattern.compile("\\{(\\w+)}");
          Matcher m = p.matcher(sql);
          while (m.find()) {
              params.add(m.group(1));
          }
          return params;
      }

      private FilterConfig buildFilter(String field, String sql, List<ColumnConfig> columnTypes) {
          String label = FIELD_LABELS.getOrDefault(field, field);

          // 日期字段 → 日期范围选择器
          if (field.contains("date") || field.contains("time")) {
              return FilterConfig.builder()
                      .type("daterange")
                      .field(field)
                      .label(label)
                      .build();
          }

          // 已知枚举字段 → 下拉框（自动从数据库加载选项）
          if (ENUM_FIELD_QUERIES.containsKey(field)) {
              List<String> options = loadOptions(ENUM_FIELD_QUERIES.get(field));
              return FilterConfig.builder()
                      .type("select")
                      .field(field)
                      .label(label)
                      .options(options)
                      .build();
          }

          // 文本类列 → 文本输入框
          if (isTextColumn(field, columnTypes)) {
              return FilterConfig.builder()
                      .type("text")
                      .field(field)
                      .label(label)
                      .build();
          }

          // 兜底 → 文本输入框
          return FilterConfig.builder()
                  .type("text")
                  .field(field)
                  .label(label)
                  .build();
      }

      private FilterConfig buildDateRangeFilter() {
          return FilterConfig.builder()
                  .type("daterange")
                  .field("settledate")
                  .label("时间范围")
                  .build();
      }

      /**
       * 从数据库加载下拉选项
       * 作用：下拉框显示真实数据（如所有城市名称），用户直接选择
       */
      private List<String> loadOptions(String query) {
          try {
              return jdbcTemplate.queryForList(query, String.class);
          } catch (Exception e) {
              log.warn("加载下拉选项失败：{}", e.getMessage());
              return List.of();
          }
      }

      private boolean isTextColumn(String field, List<ColumnConfig> columnTypes) {
          return columnTypes.stream()
                  .anyMatch(c -> c.getField().equalsIgnoreCase(field) && "text".equals(c.getFormat()));
      }
  }