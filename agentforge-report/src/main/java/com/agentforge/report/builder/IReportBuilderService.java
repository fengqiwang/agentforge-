package com.agentforge.report.builder;

import com.agentforge.common.model.ReportConfig;

import java.util.List;
import java.util.Map;

public interface IReportBuilderService {

    ReportConfig buildFromQuestion(String question, String sessionId);

    ReportConfig buildFromSql(String name, String sql);

    ReportConfig save(ReportConfig config);

    ReportConfig getByShareToken(String shareToken);

    List<ReportConfig> listReports();

    boolean delete(Long id);

    Map<String, Object> executeParamSql(String sqlTemplate, Map<String, String> params);

    Map<String, Object> executeReport(String shareToken);
}
