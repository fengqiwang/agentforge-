package com.agentforge.report.business.impl;

import com.agentforge.report.business.BusinessReport;
import com.agentforge.report.business.IBusinessReportService;
import com.agentforge.report.business.IReportComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 两期报告对比服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportComparisonServiceImpl implements IReportComparisonService {

    private final IBusinessReportService reportService;

    /**
     * 对比两期报告，逐查询计算记录数变化。
     */
    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> compare(Long id1, Long id2) {
        BusinessReport r1 = reportService.getById(id1);
        BusinessReport r2 = reportService.getById(id2);

        Map<String, Object> result = new LinkedHashMap<>();
        if (r1 == null || r2 == null) {
            result.put("error", "报告不存在: id1=" + id1 + " id2=" + id2);
            return result;
        }

        result.put("report1", Map.of("id", r1.getId(), "date", r1.getReportDate(), "template", r1.getTemplateName()));
        result.put("report2", Map.of("id", r2.getId(), "date", r2.getReportDate(), "template", r2.getTemplateName()));

        Map<String, Object> comparison = new LinkedHashMap<>();
        if (r1.getDataResult() != null && r2.getDataResult() != null) {
            for (String key : r1.getDataResult().keySet()) {
                Object o1 = r1.getDataResult().get(key);
                Object o2 = r2.getDataResult().get(key);
                if (!(o1 instanceof Map) || !(o2 instanceof Map)) continue;
                Map<String, Object> d1 = (Map<String, Object>) o1;
                Map<String, Object> d2 = (Map<String, Object>) o2;
                int count1 = toInt(d1.get("count"));
                int count2 = toInt(d2.get("count"));
                int change = count2 - count1;
                double rate = count1 > 0 ? change * 100.0 / count1 : 0;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("period1_count", count1);
                item.put("period2_count", count2);
                item.put("change", change);
                item.put("changeRate", count1 > 0 ? String.format("%.1f%%", rate) : "N/A");
                item.put("trend", change > 0 ? "UP" : change < 0 ? "DOWN" : "FLAT");
                comparison.put(key, item);
            }
        }
        result.put("comparison", comparison);
        return result;
    }

    private int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return 0;
    }
}
