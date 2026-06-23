package com.agentforge.web.controller;

import com.agentforge.framework.rag.SchemaChangeDetector;
import com.agentforge.framework.rag.SchemaIndexer;
import com.agentforge.framework.rag.schema.SchemaDiff;
import com.agentforge.framework.rag.schema.SchemaReader;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaIndexer schemaIndexer;
    private final SchemaReader schemaReader;
    private final SchemaChangeDetector schemaChangeDetector;

    @GetMapping("/detect")
    public Map<String, Object> detect() {
        List<SchemaDiff> diffs = schemaChangeDetector.detect();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changedCount", diffs.size());
        result.put("diffs", diffs);
        result.put("inSync", diffs.isEmpty());
        return result;
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        int updated = schemaChangeDetector.syncSnapshot();
        return Map.of("status", "ok", "syncedTables", updated);
    }

    @GetMapping("/tables")
    public List<Map<String, Object>> listTables() {
        return schemaReader.listIndexedTables();
    }

    @PostMapping("/reindex")
    public Map<String, String> reindexAll() {
        schemaIndexer.indexCoreTables();
        return Map.of("status", "ok", "message", "全量索引重建完成");
    }

    @PostMapping("/reindex/{tableName}")
    public Map<String, String> reindexTable(@PathVariable String tableName) {
        schemaIndexer.reindexTable(tableName);
        return Map.of("status", "ok", "message", tableName + " 索引重建完成");
    }
}
