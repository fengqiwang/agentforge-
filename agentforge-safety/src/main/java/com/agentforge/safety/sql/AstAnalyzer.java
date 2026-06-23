 package com.agentforge.safety.sql;

  import lombok.Getter;
  import lombok.extern.slf4j.Slf4j;
  import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
  import net.sf.jsqlparser.expression.operators.relational.NamedExpressionList;
  import net.sf.jsqlparser.schema.Table;
  import net.sf.jsqlparser.statement.Statement;
  import net.sf.jsqlparser.statement.select.*;
  import net.sf.jsqlparser.util.TablesNamesFinder;
  import org.springframework.stereotype.Component;

  import java.util.ArrayList;
  import java.util.HashSet;
  import java.util.List;
  import java.util.Set;

  @Slf4j
  @Component
  public class AstAnalyzer {

      /**
       * 提取SQL中所有引用的表名
       */
      public Set<String> extractTableNames(Statement statement) {
          TablesNamesFinder finder = new TablesNamesFinder();
          List<String> tables = finder.getTableList(statement);
          Set<String> result = new HashSet<>();
          for (String table : tables) {
              result.add(table.toLowerCase());
          }
          return result;
      }

      /**
       * 计算子查询嵌套深度
       */
      public int getSubqueryDepth(Statement statement) {
          if (!(statement instanceof Select select)) {
              return 0;
          }
          return measureDepth(select.getSelectBody(), 0);
      }

      private int measureDepth(Select selectBody, int currentDepth) {
          if (selectBody instanceof PlainSelect plain) {
              return measurePlainSelectDepth(plain, currentDepth);
          }
          if (selectBody instanceof SetOperationList setOp) {
              int maxDepth = currentDepth;
              for (Select body : setOp.getSelects()) {
                  int depth = measureDepth(body, currentDepth);
                  maxDepth = Math.max(maxDepth, depth);
              }
              return maxDepth;
          }
          return currentDepth;
      }

      private int measurePlainSelectDepth(PlainSelect plain, int currentDepth) {
          int maxDepth = currentDepth;

          // FROM中的子查询
          if (plain.getFromItem() instanceof Select sub) {
              int depth = measureDepth(sub.getSelectBody(), currentDepth + 1);
              maxDepth = Math.max(maxDepth, depth);
          }

          // JOIN中的子查询
          if (plain.getJoins() != null) {
              for (Join join : plain.getJoins()) {
                  if (join.getRightItem() instanceof Select sub) {
                      int depth = measureDepth(sub.getSelectBody(), currentDepth + 1);
                      maxDepth = Math.max(maxDepth, depth);
                  }
              }
          }

          // WHERE/SELECT中的子查询（通过表达式遍历）
          SubqueryDepthVisitor visitor = new SubqueryDepthVisitor(currentDepth);
          plain.accept(visitor);
          maxDepth = Math.max(maxDepth, visitor.getMaxDepth());

          return maxDepth;
      }

      /**
       * 统计JOIN数量
       */
      public int getJoinCount(Statement statement) {
          if (!(statement instanceof Select select)) {
              return 0;
          }
          Select body = select.getSelectBody();
          if (body instanceof PlainSelect plain) {
              return plain.getJoins() == null ? 0 : plain.getJoins().size();
          }
          if (body instanceof SetOperationList setOp) {
              // UNION等取最多的那个
              int max = 0;
              for (Select sb : setOp.getSelects()) {
                  if (sb instanceof PlainSelect plain) {
                      int count = plain.getJoins() == null ? 0 : plain.getJoins().size();
                      max = Math.max(max, count);
                  }
              }
              return max;
          }
          return 0;
      }

      /**
       * 子查询深度遍历器（遍历WHERE等表达式中的子查询）
       */
      private static class SubqueryDepthVisitor extends net.sf.jsqlparser.expression.ExpressionVisitorAdapter {
          private final int baseDepth;
          @Getter
          private int maxDepth;

          public SubqueryDepthVisitor(int baseDepth) {
              this.baseDepth = baseDepth;
              this.maxDepth = baseDepth;
          }

          public void visit(Select subSelect) {
              // 每遇到一个子查询，深度+1
              int newDepth = baseDepth + 1;
              // 递归测量子查询内部的深度
              SubqueryDepthVisitor inner = new SubqueryDepthVisitor(newDepth);
              if (subSelect.getSelectBody() instanceof PlainSelect plain) {
                  plain.accept(inner);
              }
              maxDepth = Math.max(maxDepth, inner.maxDepth);
          }

      }
  }