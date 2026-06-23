 package com.agentforge.safety.sql;

  import lombok.extern.slf4j.Slf4j;
  import net.sf.jsqlparser.JSQLParserException;
  import net.sf.jsqlparser.expression.LongValue;
  import net.sf.jsqlparser.parser.CCJSqlParserUtil;
  import net.sf.jsqlparser.statement.Statement;
  import net.sf.jsqlparser.statement.select.*;
  import org.springframework.stereotype.Component;

  import java.util.regex.Pattern;

  @Slf4j
  @Component
  public class SqlSanitizer {

      private static final int DEFAULT_LIMIT = 1000;
      private static final int MAX_LIMIT = 5000;

      private static final Pattern COMMENT_LINE = Pattern.compile("--.*(?=\n|$)");
      private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

      /**
       * SQL清洗主入口
       */
      public String sanitize(String sql) {
          if (sql == null || sql.isBlank()) return sql;

          // 1. 去除注释
          String cleaned = removeComments(sql);

          // 2. 规范化空白
          cleaned = cleaned.replaceAll("\\s+", " ").trim();

          // 3. 关键字大写（通过解析重建实现，更准确）
          cleaned = normalizeKeywords(cleaned);

          // 4. 自动补LIMIT
          cleaned = ensureLimit(cleaned);

          log.debug("SQL清洗完成：{}", cleaned);
          return cleaned;
      }

      // ==================== 去注释 ====================

      private String removeComments(String sql) {
          String result = COMMENT_BLOCK.matcher(sql).replaceAll("");
          result = COMMENT_LINE.matcher(result).replaceAll("");
          return result.trim();
      }

      // ==================== 关键字规范化 ====================

      private String normalizeKeywords(String sql) {
          try {
              Statement stmt = CCJSqlParserUtil.parse(sql);
              // JSqlParser的toString()会输出规范化后的SQL
              return stmt.toString();
          } catch (JSQLParserException e) {
              // 解析失败，返回原文
              log.debug("关键字规范化跳过，SQL解析失败");
              return sql;
          }
      }

      // ==================== 自动补LIMIT ====================

      private String ensureLimit(String sql) {
          try {
              Statement stmt = CCJSqlParserUtil.parse(sql);
              if (!(stmt instanceof Select select)) {
                  return sql;
              }

              Select body = select.getSelectBody();
              ensureLimitOnBody(body);

              return select.toString();
          } catch (JSQLParserException e) {
              // 解析失败，字符串级别兜底追加
              return appendStringLimit(sql);
          }
      }

      private void ensureLimitOnBody(Select body) {
          if (body instanceof PlainSelect plain) {
              Limit limit = plain.getLimit();
              if (limit == null) {
                  plain.setLimit(new Limit().withRowCount(new LongValue(DEFAULT_LIMIT)));
                  log.debug("自动追加LIMIT {}", DEFAULT_LIMIT);
              } else if (limit.getRowCount() instanceof net.sf.jsqlparser.expression.LongValue lv) {
                  long val = lv.getValue();
                  if (val > MAX_LIMIT) {
                      limit.setRowCount(new net.sf.jsqlparser.expression.LongValue(MAX_LIMIT));
                      log.debug("LIMIT {}超过上限，调整为 {}", val, MAX_LIMIT);
                  }
              }
          } else if (body instanceof SetOperationList setOp) {
              // UNION: 给整体加LIMIT
              Limit limit = setOp.getLimit();
              if (limit == null) {
                  setOp.setLimit(new Limit().withRowCount(new LongValue(DEFAULT_LIMIT)));
              }
          }
      }

      private String appendStringLimit(String sql) {
          String upper = sql.toUpperCase().trim();
          if (!upper.contains("LIMIT")) {
              return sql + " LIMIT " + DEFAULT_LIMIT;
          }
          return sql;
      }
  }