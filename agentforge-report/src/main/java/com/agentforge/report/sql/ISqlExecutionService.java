package com.agentforge.report.sql;

import com.agentforge.common.model.SqlGenerationResult;

import java.util.List;
import java.util.Map;

public interface ISqlExecutionService {

    Map<String, Object> generateAndExecute(String question, SqlGenerationResult generated);

    ExecutionResult execute(String sql);

    ExecutionResult execute(String sql, String sessionId);

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    class ExecutionResult {
        private boolean success;
        private List<Map<String, Object>> rows;
        private String error;

        public static ExecutionResult success(List<Map<String, Object>> rows) {
            return ExecutionResult.builder().success(true).rows(rows).build();
        }

        public static ExecutionResult fail(String error) {
            return ExecutionResult.builder().success(false).error(error).build();
        }
    }
}
