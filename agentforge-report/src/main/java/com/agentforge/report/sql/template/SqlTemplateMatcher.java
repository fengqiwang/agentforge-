package com.agentforge.report.sql.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
  @Slf4j
  public class SqlTemplateMatcher {

      private final JdbcTemplate jdbc;
      private final ObjectMapper objectMapper;

      // 预编译的正则 + 模板数据
      private List<CompiledTemplate> compiledTemplates;

    public SqlTemplateMatcher(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
      public void loadTemplates() {
          refresh();
      }

      public void refresh() {
          List<SqlTemplate> raw = jdbc.query(
                  "SELECT * FROM af_sql_template WHERE enabled = 1 ORDER BY id",
                  (rs, rowNum) -> {
                      SqlTemplate t = new SqlTemplate();
                      t.setName(rs.getString("name"));
                      t.setPattern(rs.getString("pattern"));
                      try {
                          t.setKeywords(objectMapper.readValue(
                                  rs.getString("keywords"), new TypeReference<List<String>>() {
                                  }));
                          t.setTemplateSql(rs.getString("template_sql"));
                          t.setParams(readParams(rs.getString("params")));
                          t.setCategory(rs.getString("category"));
                          t.setTablesUsed(objectMapper.readValue(
                                  rs.getString("tables_used"), new TypeReference<List<String>>() {
                                  }));
                      }catch (Exception e) {
                          e.printStackTrace();
                      }
                      return t;
                  }
          );

          // 预编译正则
          compiledTemplates = raw.stream()
                  .map(t -> {
                      Pattern regex = null;
                      if (t.getPattern() != null && !t.getPattern().isEmpty()) {
                          try {
                              regex = Pattern.compile(t.getPattern());
                          } catch (Exception e) {
                              log.warn("模板 {} 正则编译失败: {}", t.getName(), e.getMessage());
                          }
                      }
                      return new CompiledTemplate(t, regex);
                  })
                  .toList();

          log.info("加载了 {} 个SQL模板（已预编译正则）", compiledTemplates.size());
      }

      /**
       * 尝试匹配模板
       * @return 匹配结果，未命中返回null
       */
      public TemplateMatchResult match(String question) {
          for (CompiledTemplate ct : compiledTemplates) {
              SqlTemplate tpl = tpl = ct.template;
              boolean matched = false;

              // 策略1：正则匹配（优先）
              if (ct.regex != null && ct.regex.matcher(question).find()) {
                  matched = true;
              }

              // 策略2：关键词匹配（正则未命中时）
              if (!matched && tpl.getKeywords() != null && tpl.getKeywords().size() >= 2) {
                  long matchCount = tpl.getKeywords().stream()
                          .filter(question::contains)
                          .count();
                  if (matchCount >= 2 && matchCount >= tpl.getKeywords().size() * 0.5) {
                      matched = true;
                  }
              }

              if (matched) {
                  log.info("模板命中: {} → pattern={}, keywords={}",
                          tpl.getName(), tpl.getPattern(), tpl.getKeywords());

                  // 记录命中次数
                  jdbc.update(
                      "UPDATE af_sql_template SET hit_count = hit_count + 1 WHERE name = ?",
                      tpl.getName()
                  );

                  return TemplateMatchResult.builder()
                          .matched(true)
                          .templateName(tpl.getName())
                          .sql(tpl.getTemplateSql())
                          .category(tpl.getCategory())
                          .tablesUsed(tpl.getTablesUsed())
                          .build();
              }
          }
          return null;
      }

      /**
       * 预编译模板（避免每次匹配都编译正则）
       */
      private record CompiledTemplate(SqlTemplate template, Pattern regex) {}

      /**
       * 兼容两种 params JSON 格式：
       * 1. [{name: "start_date", type: "daterange_int", ...}] — TemplateParam 对象数组
       * 2. ["start_date", "end_date"] — 纯字符串数组（旧格式）
       */
      private List<TemplateParam> readParams(String json) {
          if (json == null || json.isBlank() || "[]".equals(json.trim())) {
              return List.of();
          }
          try {
              // 尝试作为 TemplateParam 对象数组解析
              return objectMapper.readValue(json, new TypeReference<List<TemplateParam>>() {});
          } catch (Exception e1) {
              try {
                  // 兼容旧格式：纯字符串数组
                  List<String> names = objectMapper.readValue(json, new TypeReference<List<String>>() {});
                  return names.stream()
                          .map(name -> {
                              TemplateParam p = new TemplateParam();
                              p.setName(name);
                              p.setType("int"); // 默认类型
                              return p;
                          })
                          .toList();
              } catch (Exception e2) {
                  log.warn("模板 params JSON 解析失败: {}", json, e2);
                  return List.of();
              }
          }
      }
  }