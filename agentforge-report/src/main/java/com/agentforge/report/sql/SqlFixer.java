 package com.agentforge.report.sql;

  import com.agentforge.common.model.ValidationResult;
  import com.agentforge.framework.prompt.PromptManager;
  import com.agentforge.framework.rag.SchemaRetriever;
  import com.agentforge.safety.sql.SqlSafetyValidator;
  import dev.langchain4j.data.message.AiMessage;
  import dev.langchain4j.data.message.ChatMessage;
  import dev.langchain4j.data.message.UserMessage;
  import dev.langchain4j.model.chat.ChatModel;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.*;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class SqlFixer {

      private final ChatModel chatModel;
      private final PromptManager promptManager;
      private final SchemaRetriever schemaRetriever;
      private final SqlSafetyValidator safetyValidator;

      private static final int MAX_LLM_RETRIES = 3;

      /** 已知字段名（真实核心表，对齐 Claude.md） */
      private static final Set<String> KNOWN_FIELDS = Set.of(
              // 交易汇总
              "cusid", "cusname", "tranamt", "tranfee", "amount", "settledate", "transtype", "producttype",
              "allinpayfee", "oldallinpayfee", "income", "outfee", "agentprofit", "collectstat",
              // 商户
              "city", "custype", "state", "regdate", "one", "province", "indate",
              // 归属
              "organtbstart", "organtbend", "interno", "internoend", "startrate", "endrate",
              "allocationdept", "allocationrate", "status", "type",
              // 部门/员工/标签
              "dept_id", "dept_name", "nick_name", "user_name", "phonenumber", "tag_name", "tag_pid",
              // 工单/费率
              "busi_type", "createtime", "exigency", "handleusername", "rate", "product_type"
      );

      /** 已知表名（真实 11 张核心表） */
      private static final Set<String> KNOWN_TABLES = Set.of(
              "syb_transuminfor", "tlt_transuminfor",
              "syb_merchant", "syb_merchant_rub",
              "syb_merchantattribute", "tlt_merchantattribute",
              "sys_dept", "sys_user", "syb_merchant_tag",
              "jxallinpay_busi_order", "busi_rate"
      );

      /**
       * 修复SQL（两级策略）
       */
      public SqlFixResult fix(String sql, String errorMessage) {
          log.info("开始修复SQL，错误：{}", errorMessage);

          // Level 1: 快速修复（不调LLM）
          String quickFixed = quickFix(sql, errorMessage);
          if (quickFixed != null && !quickFixed.equals(sql)) {
              ValidationResult validation = safetyValidator.validate(quickFixed);
              if (validation.isPassed()) {
                  log.info("Level 1 快速修复成功");
                  return SqlFixResult.builder()
                          .fixedSql(quickFixed)
                          .fixDescription("快速修复：字段名/表名纠正")
                          .level("QUICK_FIX")
                          .success(true)
                          .build();
              }
          }

          // Level 2: LLM修复
          return llmFix(sql, errorMessage);
      }

      // ==================== Level 1: 快速修复 ====================

      private String quickFix(String sql, String errorMessage) {
          String fixed = sql;

          // 1. 字段名拼写纠正
          fixed = fixFieldNames(fixed, errorMessage);

          // 2. 表名拼写纠正
          fixed = fixTableNames(fixed, errorMessage);

          // 3. 常见语法错误
          fixed = fixCommonSyntax(fixed);

          return fixed;
      }

      private String fixFieldNames(String sql, String errorMessage) {
          // MySQL: Unknown column 'xxx' in 'field list'
          Pattern p = Pattern.compile("Unknown column '(\\w+)'");
          Matcher m = p.matcher(errorMessage);
          if (!m.find()) return sql;

          String unknownField = m.group(1);
          String closest = findClosestMatch(unknownField, KNOWN_FIELDS);
          if (closest != null && !closest.equals(unknownField)) {
              log.info("字段名纠正：{} → {}", unknownField, closest);
              return sql.replaceAll("\\b" + Pattern.quote(unknownField) + "\\b",
                      Matcher.quoteReplacement(closest));
          }
          return sql;
      }

      private String fixTableNames(String sql, String errorMessage) {
          // MySQL: Table 'synthesis.xxx' doesn't exist
          Pattern p = Pattern.compile("Table '.*?\\.(\\w+)' doesn't exist");
          Matcher m = p.matcher(errorMessage);
          if (!m.find()) return sql;

          String unknownTable = m.group(1);
          String closest = findClosestMatch(unknownTable, KNOWN_TABLES);
          if (closest != null && !closest.equals(unknownTable)) {
              log.info("表名纠正：{} → {}", unknownTable, closest);
              return sql.replaceAll("\\b" + Pattern.quote(unknownTable) + "\\b",
                      Matcher.quoteReplacement(closest));
          }
          return sql;
      }

      private String fixCommonSyntax(String sql) {
          String fixed = sql.trim();
          // 去掉末尾分号
          if (fixed.endsWith(";")) {
              fixed = fixed.substring(0, fixed.length() - 1);
          }
          return fixed;
      }

      /**
       * Levenshtein距离 ≤ 2 视为拼写错误
       */
      private String findClosestMatch(String input, Set<String> candidates) {
          String best = null;
          int minDist = Integer.MAX_VALUE;

          for (String candidate : candidates) {
              int dist = levenshtein(input.toLowerCase(), candidate.toLowerCase());
              if (dist < minDist) {
                  minDist = dist;
                  best = candidate;
              }
          }

          return minDist <= 2 ? best : null;
      }

      private int levenshtein(String a, String b) {
          int m = a.length(), n = b.length();
          int[][] dp = new int[m + 1][n + 1];

          for (int i = 0; i <= m; i++) dp[i][0] = i;
          for (int j = 0; j <= n; j++) dp[0][j] = j;

          for (int i = 1; i <= m; i++) {
              for (int j = 1; j <= n; j++) {
                  int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                  dp[i][j] = Math.min(
                          Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                          dp[i - 1][j - 1] + cost
                  );
              }
          }
          return dp[m][n];
      }

      // ==================== Level 2: LLM修复 ====================

      private SqlFixResult llmFix(String sql, String errorMessage) {
          String schemaInfo;
          try {
              schemaInfo = schemaRetriever.retrieve(sql).toString();
          } catch (Exception e) {
              schemaInfo = "Schema信息不可用";
          }

          for (int i = 1; i <= MAX_LLM_RETRIES; i++) {
              log.info("Level 2 LLM修复，第 {}/{} 次", i, MAX_LLM_RETRIES);

              String prompt = promptManager.buildPrompt("sql-fix", Map.of(
                      "original_sql", sql,
                      "error_message", errorMessage,
                      "schema", schemaInfo
              ));

              try {
                  String response = callLlm(prompt);
                  String fixedSql = parseFixedSql(response);

                  if (fixedSql == null || fixedSql.isBlank()) {
                      log.warn("LLM修复返回空SQL");
                      continue;
                  }

                  ValidationResult validation = safetyValidator.validate(fixedSql);
                  if (!validation.isPassed()) {
                      log.warn("LLM修复后SQL仍不安全：{}", validation.getErrorMessage());
                      // 把安全错误追加到errorMessage，供下次重试参考
                      errorMessage = errorMessage + "；安全校验：" + validation.getErrorMessage();
                      continue;
                  }

                  log.info("Level 2 LLM修复成功（第{}次）", i);
                  return SqlFixResult.builder()
                          .fixedSql(fixedSql)
                          .fixDescription("LLM修复（第" + i + "次）")
                          .level("LLM_FIX")
                          .success(true)
                          .build();

              } catch (Exception e) {
                  log.error("LLM修复第{}次异常", i, e);
              }
          }

          return SqlFixResult.builder()
                  .fixedSql(sql)
                  .fixDescription("修复失败，已重试" + MAX_LLM_RETRIES + "次")
                  .level("FAILED")
                  .success(false)
                  .build();
      }

      private String callLlm(String prompt) {
          List<ChatMessage> messages = List.of(UserMessage.from(prompt));
          AiMessage response = chatModel.chat(messages).aiMessage();
          return response.text();
      }

      /**
       * 从LLM返回的JSON中提取 fixed_sql 字段
       */
      private String parseFixedSql(String response) {
          String json = response.trim();
          if (json.startsWith("```")) {
              json = json.replaceAll("^```json?\\s*", "").replaceAll("\\s*```$", "");
          }

          // 提取 "fixed_sql" : "..."
          Pattern p = Pattern.compile("\"fixed_sql\"\\s*:\\s*\"");
          Matcher m = p.matcher(json);
          if (!m.find()) return null;

          int start = m.end();
          StringBuilder sb = new StringBuilder();
          boolean escaped = false;

          for (int i = start; i < json.length(); i++) {
              char c = json.charAt(i);
              if (escaped) {
                  sb.append(c);
                  escaped = false;
              } else if (c == '\\') {
                  escaped = true;
              } else if (c == '"') {
                  break;
              } else {
                  sb.append(c);
              }
          }
          return sb.toString();
      }

      // ==================== 修复结果模型 ====================

      @lombok.Data
      @lombok.Builder
      @lombok.NoArgsConstructor
      @lombok.AllArgsConstructor
      public static class SqlFixResult {
          private String fixedSql;
          private String fixDescription;
          /** QUICK_FIX / LLM_FIX / FAILED */
          private String level;
          private boolean success;
      }
  }