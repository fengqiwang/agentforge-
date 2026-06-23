 package com.agentforge.safety.sql;

  import jakarta.annotation.PostConstruct;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.jdbc.core.JdbcTemplate;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Component;

  import java.util.Set;
  import java.util.concurrent.ConcurrentHashMap;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class TableWhitelist {

      private final JdbcTemplate jdbcTemplate;

      /** 白名单表集合 */
      private final Set<String> whitelist = ConcurrentHashMap.newKeySet();

      @PostConstruct
      public void init() {
          // 核心业务表（对齐 synthesis Claude.md）— 交易/商户/归属/部门/标签/工单/费率
          Set<String> coreTables = Set.of(
                  "syb_transuminfor",
                  "tlt_transuminfor",
                  "syb_merchant",
                  "syb_merchant_rub",
                  "syb_merchantattribute",
                  "tlt_merchantattribute",
                  "sys_dept",
                  "sys_user",
                  "syb_merchant_tag",
                  "jxallinpay_busi_order",
                  "busi_rate"
          );
          whitelist.addAll(coreTables);

          // 从 af_sql_template 动态加载模板中涉及的表
          try {
              loadFromTemplates();
          } catch (Exception e) {
              log.warn("从模板加载白名单表失败，使用默认白名单：{}", e.getMessage());
          }

          log.info("表白名单加载完成，共 {} 张表：{}", whitelist.size(), whitelist);
      }

      private void loadFromTemplates() {
          jdbcTemplate.queryForList(
                  "SELECT DISTINCT tables_used FROM af_sql_template WHERE tables_used IS NOT NULL",
                  String.class
          ).forEach(tablesStr -> {
              // tables_used 是 JSON 数组（如 ["syb_transuminfor","syb_merchant"]），去掉括号引号后按逗号拆分
              String cleaned = tablesStr.replaceAll("[\\[\\]\"\\\\]", "");
              for (String table : cleaned.split(",")) {
                  String trimmed = table.trim().toLowerCase();
                  if (!trimmed.isEmpty()) {
                      whitelist.add(trimmed);
                  }
              }
          });
      }

      public boolean isAllowed(String tableName) {
          if (tableName == null) return false;
          return whitelist.contains(tableName.toLowerCase());
      }

      public Set<String> getAllowedTables() {
          return Set.copyOf(whitelist);
      }
  }