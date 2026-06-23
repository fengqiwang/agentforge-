package com.agentforge.framework.rag.schema;

import com.agentforge.common.model.TableSchema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schema 差异比对：当前库结构 vs af_schema_index 快照（按列数比对）。
 *
 * <p>snapshot：{tableName → columnCount}，来自 af_schema_index。
 * current：{@link SchemaReader#readCoreTables()} 的实时结构。
 */
@Component
public class SchemaComparator {

    /**
     * 比对当前结构与快照。
     *
     * @param current  当前库的表结构
     * @param snapshot 快照 {tableName → columnCount}
     * @return 差异列表（空表示无变更）
     */
    public List<SchemaDiff> compare(List<TableSchema> current, Map<String, Integer> snapshot) {
        List<SchemaDiff> diffs = new ArrayList<>();

        Map<String, Integer> currentMap = new java.util.LinkedHashMap<>();
        for (TableSchema t : current) {
            int colCount = t.getColumns() != null ? t.getColumns().size() : 0;
            currentMap.put(t.getTableName(), colCount);
        }

        // ADDED / COLUMN_CHANGED
        for (Map.Entry<String, Integer> e : currentMap.entrySet()) {
            Integer stored = snapshot.get(e.getKey());
            if (stored == null) {
                diffs.add(SchemaDiff.added(e.getKey(), e.getValue()));
            } else if (!stored.equals(e.getValue())) {
                diffs.add(SchemaDiff.columnChanged(e.getKey(), e.getValue(), stored));
            }
        }

        // REMOVED
        Set<String> currentTables = new HashSet<>(currentMap.keySet());
        for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
            if (!currentTables.contains(e.getKey())) {
                diffs.add(SchemaDiff.removed(e.getKey(), e.getValue()));
            }
        }
        return diffs;
    }
}
