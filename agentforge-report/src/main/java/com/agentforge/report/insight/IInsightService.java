package com.agentforge.report.insight;

import java.util.List;
import java.util.Map;

public interface IInsightService {

    /** 生成洞察：先跑全维度分析，再调 AI。 */
    Long generate(String periodType, String start, String end);

    Map<String, Object> getById(Long id);

    List<Map<String, Object>> list();
}
