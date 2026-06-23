package com.agentforge.report.business;

import java.util.Map;

public interface IReportComparisonService {

    Map<String, Object> compare(Long id1, Long id2);
}
