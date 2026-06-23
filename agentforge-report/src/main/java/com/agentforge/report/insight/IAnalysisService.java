package com.agentforge.report.insight;

import java.util.List;
import java.util.Map;

public interface IAnalysisService {

    /** 分析任务执行结果（含任务 id 与 typed 维度结果，避免 DB 反序列化丢失类型）。 */
    record AnalysisOutcome(Long taskId, Map<String, AnalysisResult> results) {}

    /** 创建并执行分析任务，返回 typed 结果。 */
    AnalysisOutcome createTaskWithResults(String name, List<String> dimensions, String start, String end);

    /** 创建并执行分析任务，仅返回任务 id（向后兼容）。 */
    Long createTask(String name, List<String> dimensions, String start, String end);

    Map<String, Object> getById(Long id);

    List<Map<String, Object>> list();
}
