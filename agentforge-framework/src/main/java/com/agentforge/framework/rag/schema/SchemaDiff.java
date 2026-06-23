package com.agentforge.framework.rag.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Schema 变更差异。
 * changeType：ADDED（新增表）/ REMOVED（删除表）/ COLUMN_CHANGED（列数变化）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemaDiff {

    private String tableName;
    private String changeType;
    private Integer currentColumnCount;
    private Integer storedColumnCount;
    private String detail;

    public static SchemaDiff added(String table, int current) {
        return new SchemaDiff(table, "ADDED", current, null, "快照中不存在，疑似新增表");
    }

    public static SchemaDiff removed(String table, int stored) {
        return new SchemaDiff(table, "REMOVED", null, stored, "当前库中不存在，疑似删除表");
    }

    public static SchemaDiff columnChanged(String table, int current, int stored) {
        return new SchemaDiff(table, "COLUMN_CHANGED", current, stored,
                String.format("列数由 %d 变为 %d", stored, current));
    }
}
