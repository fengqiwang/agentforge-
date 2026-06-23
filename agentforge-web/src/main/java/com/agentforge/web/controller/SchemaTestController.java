package com.agentforge.web.controller;

import com.agentforge.common.model.FieldMatch;
import com.agentforge.common.model.TableMatch;
import com.agentforge.common.model.TableRelation;
import com.agentforge.framework.rag.SchemaIndexer;
import com.agentforge.framework.rag.SchemaRetriever;
import com.agentforge.report.schema.SchemaLinker;
import com.agentforge.report.schema.TableRelationGraph;
import com.agentforge.report.sql.template.SqlTemplateMatcher;
import com.agentforge.report.sql.template.TemplateMatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/poc/schema")
@Slf4j
public class SchemaTestController {

    private final SchemaIndexer schemaIndexer;
    private final SchemaRetriever schemaRetriever;
    private final SchemaLinker schemaLinker;
    private final TableRelationGraph relationGraph;

    private final SqlTemplateMatcher templateMatcher;

    public SchemaTestController(SchemaIndexer schemaIndexer, SchemaRetriever schemaRetriever, SchemaLinker schemaLinker, TableRelationGraph relationGraph, SqlTemplateMatcher templateMatcher) {
        this.schemaIndexer = schemaIndexer;
        this.schemaRetriever = schemaRetriever;
        this.schemaLinker = schemaLinker;
        this.relationGraph = relationGraph;
        this.templateMatcher = templateMatcher;
    }

    /**
       * 触发索引（首次运行调用一次）
       */
      @PostMapping("/index")
      public String index() {
          schemaIndexer.indexCoreTables();
          return "索引完成";
      }

      /**
       * 测试检索：输入问题，返回相关表
       */
      @GetMapping("/search")
      public List<TableMatch> search(@RequestParam String question) {
          List<TableMatch> results = schemaRetriever.retrieve(question, 5);
          results.forEach(r -> log.info("  {} (score={}) — {}", r.getTableName(), r.getScore(), r.getTableComment()));
          return results;
      }


    /**
     * 完整Schema检索链路测试
     * 输入问题 → 检索表 → 映射字段 → 发现关联
     */
    @GetMapping("/full-search")
    public Map<String, Object> fullSearch(@RequestParam String question) {
        // 1. 向量检索Top-5
        List<TableMatch> tables = schemaRetriever.retrieve(question, 5);
        List<String> tableNames = tables.stream().map(TableMatch::getTableName).toList();

        // 2. 补全关联表（新增）
        List<String> expandedNames = relationGraph.expandRelatedTables(tableNames);

        // 3. 字段映射在补全后的表上做
        List<FieldMatch> fields = schemaLinker.linkFields(question, expandedNames);

        // 4. 关联关系
        String joinHints = relationGraph.formatJoinHints(expandedNames);
        List<TableRelation> relations = relationGraph.discoverJoins(expandedNames);

        return Map.of(
                "question", question,
                "tables", tables,
                "expandedTables", expandedNames,
                "fields", fields,
                "joinHints", joinHints,
                "relations", relations
        );
    }


    // 构造器注入

    @GetMapping("/template-match")
    public TemplateMatchResult templateMatch(@RequestParam String question) {
        TemplateMatchResult result = templateMatcher.match(question);
        if (result == null) {
            return TemplateMatchResult.builder().matched(false).build();
        }
        return result;
    }

  }