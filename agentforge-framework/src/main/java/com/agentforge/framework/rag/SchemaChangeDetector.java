package com.agentforge.framework.rag;

import com.agentforge.common.model.TableSchema;
import com.agentforge.framework.rag.schema.SchemaComparator;
import com.agentforge.framework.rag.schema.SchemaDiff;
import com.agentforge.framework.rag.schema.SchemaReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema 变更感知：定时（每天凌晨 3 点）比对当前库结构与 af_schema_index 快照，
 * 发现变更则记录日志并（可选）触发增量 RAG 重建。
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #detect()} — 检测差异，返回 SchemaDiff 列表</li>
 *   <li>{@link #syncSnapshot()} — 把快照刷新为当前结构（即「全量重建元数据」）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaChangeDetector {

    private final SchemaReader schemaReader;
    private final SchemaComparator comparator;
    private final JdbcTemplate jdbcTemplate;

    /** 检测当前库与快照的差异。 */
    public List<SchemaDiff> detect() {
        List<TableSchema> current = schemaReader.readCoreTables();
        Map<String, Integer> snapshot = loadSnapshot();
        List<SchemaDiff> diffs = comparator.compare(current, snapshot);
        log.info("[SchemaChange] 检测完成：核心表 {} 张，发现变更 {} 处", current.size(), diffs.size());
        diffs.forEach(d -> log.warn("[SchemaChange] {} {} - {}",
                d.getChangeType(), d.getTableName(), d.getDetail()));
        return diffs;
    }

    /** 把 af_schema_index 快照刷新为当前库结构（全量同步）。 */
    public int syncSnapshot() {
        List<TableSchema> current = schemaReader.readCoreTables();
        int updated = 0;
        for (TableSchema t : current) {
            int colCount = t.getColumns() != null ? t.getColumns().size() : 0;
            int n = jdbcTemplate.update(
                    "INSERT INTO af_schema_index (table_name, column_count, table_comment, index_status) " +
                            "VALUES (?,?,?,1) ON DUPLICATE KEY UPDATE column_count=VALUES(column_count), " +
                            "table_comment=VALUES(table_comment), updated_at=NOW()",
                    t.getTableName(), colCount, t.getTableComment());
            updated += n;
        }
        log.info("[SchemaChange] 快照已同步：{} 张表", updated);
        return updated;
    }

    /** 定时检测：每天凌晨 3:07。发现变更仅记录（不自动重建，避免误触发）。 */
    @Scheduled(cron = "0 7 3 * * ?")
    public void scheduledDetect() {
        try {
            List<SchemaDiff> diffs = detect();
            if (!diffs.isEmpty()) {
                log.warn("[SchemaChange] 定时检测发现 {} 处变更，建议人工触发 /reindex 重建索引", diffs.size());
            }
        } catch (Exception e) {
            log.error("[SchemaChange] 定时检测失败", e);
        }
    }

    /** 从 af_schema_index 加载快照 {tableName → columnCount}。 */
    private Map<String, Integer> loadSnapshot() {
        Map<String, Integer> map = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT table_name, column_count FROM af_schema_index",
                rs -> {
                    map.put(rs.getString("table_name"),
                            rs.getObject("column_count") != null ? rs.getInt("column_count") : 0);
                });
        return map;
    }
}
