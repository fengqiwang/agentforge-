package com.agentforge.report.reconciliation;

import com.agentforge.common.model.reconciliation.ColumnMapping;
import com.agentforge.common.model.reconciliation.ReconciliationRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IFileUploadService {

    /**
     * 解析结果：包含列名 + 原始行数据（Map<列名, 值>）
     * 作用：解析后先返回原始数据供预览，待用户确认列映射后再转为 ReconciliationRecord
     */
    class ParseResult {
        /** 列名数组 */
        public final String[] columns;
        /** 原始行数据（列名→值的映射） */
        public final List<Map<String, String>> rows;

        public ParseResult(String[] columns, List<Map<String, String>> rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }

    ParseResult parse(MultipartFile file);

    List<ReconciliationRecord> mapToRecords(List<Map<String, String>> rows,
                                           ColumnMapping mapping, String source);

    ColumnMapping detectColumnMapping(ParseResult parseResult);
}
