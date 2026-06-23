 package com.agentforge.safety.sql;

  import com.agentforge.common.model.ValidationResult;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import net.sf.jsqlparser.JSQLParserException;
  import net.sf.jsqlparser.parser.CCJSqlParserUtil;
  import net.sf.jsqlparser.statement.Statement;
  import net.sf.jsqlparser.statement.select.Select;
  import org.springframework.stereotype.Component;

  import java.util.Set;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class SqlSafetyValidator {

      private final AstAnalyzer astAnalyzer;
      private final TableWhitelist tableWhitelist;

      private static final int MAX_SUBQUERY_DEPTH = 3;
      private static final int MAX_JOIN_COUNT = 5;

      /**
       * SQL安全校验主入口
       */
      public ValidationResult validate(String sql) {
          if (sql == null || sql.isBlank()) {
              return ValidationResult.fail("SQL为空");
          }

          // 0. 预检：多语句注入（分号分割）
          String trimmed = sql.trim();
          if (trimmed.contains(";") && trimmed.indexOf(';') < trimmed.length() - 1) {
              return ValidationResult.fail("不允许执行多条SQL语句");
          }

          // 1. 解析AST
          Statement statement;
          try {
              statement = CCJSqlParserUtil.parse(trimmed);
          } catch (JSQLParserException e) {
              return ValidationResult.fail("SQL语法解析失败：" + simplifyError(e.getMessage()));
          }

          // 2. SELECT-only检查
          if (!(statement instanceof Select)) {
              String type = statement.getClass().getSimpleName();
              return ValidationResult.fail("只允许SELECT查询，当前类型：" + type);
          }

          // 3. 表白名单检查
          ValidationResult tableCheck = checkTableWhitelist(statement);
          if (!tableCheck.isPassed()) return tableCheck;

          // 4. 子查询深度限制
          ValidationResult subqueryCheck = checkSubqueryDepth(statement);
          if (!subqueryCheck.isPassed()) return subqueryCheck;

          // 5. JOIN数量限制
          ValidationResult joinCheck = checkJoinCount(statement);
          if (!joinCheck.isPassed()) return joinCheck;

          log.debug("SQL安全校验通过");
          return ValidationResult.safe();
      }

      // ==================== 规则 3: 表白名单 ====================

      private ValidationResult checkTableWhitelist(Statement statement) {
          Set<String> tables = astAnalyzer.extractTableNames(statement);
          for (String table : tables) {
              if (!tableWhitelist.isAllowed(table)) {
                  log.warn("表白名单拦截：{}", table);
                  return ValidationResult.fail("表 '" + table + "' 不在白名单中");
              }
          }
          return ValidationResult.safe();
      }

      // ==================== 规则 4: 子查询深度 ====================

      private ValidationResult checkSubqueryDepth(Statement statement) {
          int depth = astAnalyzer.getSubqueryDepth(statement);
          if (depth > MAX_SUBQUERY_DEPTH) {
              return ValidationResult.fail("子查询嵌套超过" + MAX_SUBQUERY_DEPTH + "层，当前" + depth + "层");
          }
          if (depth >= 2) {
              log.info("子查询深度={}，接近限制", depth);
          }
          return ValidationResult.safe();
      }

      // ==================== 规则 5: JOIN数量 ====================

      private ValidationResult checkJoinCount(Statement statement) {
          int count = astAnalyzer.getJoinCount(statement);
          if (count > MAX_JOIN_COUNT) {
              return ValidationResult.fail("JOIN数量超过" + MAX_JOIN_COUNT + "个，当前" + count + "个");
          }
          return ValidationResult.safe();
      }

      // ==================== 工具方法 ====================

      private String simplifyError(String message) {
          // JSqlParser的错误信息很长，只取第一行
          int newline = message.indexOf('\n');
          return newline > 0 ? message.substring(0, newline) : message;
      }
  }