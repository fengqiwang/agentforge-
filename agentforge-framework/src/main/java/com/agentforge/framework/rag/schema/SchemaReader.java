package com.agentforge.framework.rag.schema;

import com.agentforge.common.model.ColumnSchema;
import com.agentforge.common.model.TableSchema;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SchemaReader {

    private final JdbcTemplate jdbc;

    public SchemaReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TableSchema> readAll() {
        String sql = """
              SELECT c.TABLE_NAME, t.TABLE_COMMENT, c.COLUMN_NAME, c.COLUMN_TYPE,
                     c.COLUMN_COMMENT, c.IS_NULLABLE, c.COLUMN_KEY
              FROM information_schema.COLUMNS c
              JOIN information_schema.TABLES t
                ON c.TABLE_NAME = t.TABLE_NAME AND c.TABLE_SCHEMA = t.TABLE_SCHEMA
              WHERE c.TABLE_SCHEMA = 'synthesis'
              ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
              """;

        Map<String, TableSchema> map = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            String tableName = rs.getString("TABLE_NAME");
            map.computeIfAbsent(tableName, t -> {
                        try {
                            return TableSchema.builder()
                                    .tableName(t)
                                    .tableComment(rs.getString("TABLE_COMMENT"))
                                    .build();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).addColumn(ColumnSchema.builder()
                    .columnName(rs.getString("COLUMN_NAME"))
                    .columnType(rs.getString("COLUMN_TYPE"))
                    .columnComment(rs.getString("COLUMN_COMMENT"))
                    .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                    .isKey("PRI".equals(rs.getString("COLUMN_KEY")))
                    .build()
            );
        });
        return new ArrayList<>(map.values());
    }

    /**
     * 只读核心业务表（对齐 synthesis Claude.md 真实 schema）。
     * 覆盖：交易汇总(收银宝+收付通)、商户(正常+睡眠+归属)、部门/员工、标签、工单、费率。
     */
    public List<TableSchema> readCoreTables() {
        List<String> coreTables = List.of(
                "syb_transuminfor", "tlt_transuminfor",          // 交易汇总：收银宝 + 收付通
                "syb_merchant", "syb_merchant_rub",              // 商户：正常 + 睡眠
                "syb_merchantattribute", "tlt_merchantattribute",// 归属绑定
                "sys_dept", "sys_user",                          // 部门 / 员工
                "syb_merchant_tag",                              // 商户标签（客户层级 tag_pid=27）
                "jxallinpay_busi_order", "busi_rate"             // 工单 / 费率
        );
        return readAll().stream()
                .filter(t -> coreTables.contains(t.getTableName()))
                .toList();
    }

    /** 查询 af_schema_index 表中已索引的表状态。 */
    public List<Map<String, Object>> listIndexedTables() {
        return jdbc.queryForList(
                "SELECT table_name, column_count, table_comment, index_status, updated_at " +
                "FROM af_schema_index ORDER BY table_name"
        );
    }
}