package com.agentforge.framework.prompt;

  import com.agentforge.common.model.PromptTemplate;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import jakarta.annotation.PostConstruct;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
  import org.springframework.data.redis.core.StringRedisTemplate;
  import org.springframework.stereotype.Component;
  import org.yaml.snakeyaml.Yaml;

  import java.io.IOException;
  import java.io.InputStream;
  import java.util.*;
  import java.util.concurrent.ConcurrentHashMap;
  import java.util.concurrent.TimeUnit;

@Slf4j
  @Component
  @RequiredArgsConstructor
  public class PromptManager {

      // 在 PromptManager 中注入 StringRedisTemplate
      private final StringRedisTemplate redisTemplate;
      private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "agentforge:prompt:";
    private static final long CACHE_TTL_HOURS = 1;

      private static final String CLASSPATH_PATTERN = "classpath:prompts/*.yml";

      private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();

      private final Yaml yaml = new Yaml();

      @PostConstruct
      public void init() {
          loadAll();
      }

      /**
       * 加载所有YAML Prompt文件
       */
      public synchronized void loadAll() {
          templates.clear();
          try {
              PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
              var resources = resolver.getResources(CLASSPATH_PATTERN);

              for (var resource : resources) {
                  try (InputStream is = resource.getInputStream()) {
                      Map<String, Object> raw = yaml.load(is);
                      if (raw == null) continue;

                      PromptTemplate template = parseTemplate(raw);
                      templates.put(template.getName(), template);
                      log.info("加载Prompt：{}", template.getName());
                  }
              }
              log.info("Prompt加载完成，共 {} 个", templates.size());
          } catch (IOException e) {
              log.error("Prompt加载失败", e);
          }
      }

      /**
       * 获取模板
       */
      public PromptTemplate getTemplate(String name) {
          return templates.get(name);
      }

      /**
       * 构建Prompt（带Redis缓存）
       * 作用：先查Redis缓存，命中则直接返回，未命中则读YAML构建并缓存
       */
      public String buildPrompt(String name, Map<String, String> variables) {
          String cacheKey = CACHE_PREFIX + name;

          // 1. 查 Redis 缓存
          PromptTemplate template = getFromCache(cacheKey);
          if (template == null) {
              // 2. 缓存未命中，从内存中的 templates Map 获取
              template = templates.get(name);
              if (template == null) {
                  throw new IllegalArgumentException("Prompt模板不存在：" + name);
              }
              // 写入 Redis 缓存
              saveToCache(cacheKey, template);
          }

          // 3. 变量替换
          return replaceVariables(template.toPromptText(), variables);
      }

      /**
       * 热重载：清除 Redis 缓存 + 重新读取 YAML
       */
      public void reload() {
          loadAll();  // 重新读取 YAML 文件
          // 清除所有 Prompt 缓存
          var keys = redisTemplate.keys(CACHE_PREFIX + "*");
          if (keys != null && !keys.isEmpty()) {
              redisTemplate.delete(keys);
          }
          log.info("Prompt模板已重载，缓存已清除");
      }

      private PromptTemplate getFromCache(String cacheKey) {
          String json = redisTemplate.opsForValue().get(cacheKey);
          if (json == null) return null;
          try {
              return objectMapper.readValue(json, PromptTemplate.class);
          } catch (Exception e) {
              log.warn("Prompt缓存反序列化失败：{}", e.getMessage());
              return null;
          }
      }

      private void saveToCache(String cacheKey, PromptTemplate template) {
          try {
              String json = objectMapper.writeValueAsString(template);
              redisTemplate.opsForValue().set(cacheKey, json,
                      CACHE_TTL_HOURS, TimeUnit.HOURS);
          } catch (Exception e) {
              log.warn("Prompt缓存写入失败：{}", e.getMessage());
          }
      }

      public Set<String> getTemplateNames() {
          return templates.keySet();
      }

      /**
       * 变量替换：将 ${var} 替换为实际值
       */
      private String replaceVariables(String text, Map<String, String> variables) {
          if (text == null || variables == null || variables.isEmpty()) {
              return text;
          }
          String result = text;
          for (Map.Entry<String, String> entry : variables.entrySet()) {
              result = result.replace("${" + entry.getKey() + "}", entry.getValue());
          }
          return result;
      }

      // ==================== 解析 ====================

      @SuppressWarnings("unchecked")
      private PromptTemplate parseTemplate(Map<String, Object> raw) {
          PromptTemplate.PromptTemplateBuilder builder = PromptTemplate.builder();

          builder.name((String) raw.get("name"));
          builder.system((String) raw.get("system"));

          Object rulesObj = raw.get("rules");
          if (rulesObj instanceof List<?> rulesList) {
              builder.rules((List<String>) rulesList);
          }

          Object shotsObj = raw.get("few_shots");
          if (shotsObj instanceof List<?> shotsList) {
              builder.fewShots((List<Map<String, String>>) shotsList);
          }

          builder.inputTemplate((String) raw.get("input_template"));
          builder.outputFormat((String) raw.get("output_format"));

          return builder.build();
      }
  }