package com.agentforge.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SqlGenerationResult {
    /** 生成级别：TEMPLATE / FEW_SHOT / LLM */
    private String level;

    private String sql;
    private Double confidence;
    private String explanation;
    private String tablesUsed;

    private int tokenCount;

    /** 是否命中 */
    private boolean matched;

    public static SqlGenerationResult unmatched() {
        return SqlGenerationResult.builder()
                .matched(false)
                .build();
    }
}