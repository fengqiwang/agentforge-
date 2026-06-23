package com.agentforge.report.business;

import java.util.List;

public interface IReportTemplateService {

    ReportTemplate create(ReportTemplate template);

    ReportTemplate update(Long id, ReportTemplate template);

    List<ReportTemplate> list();

    ReportTemplate getById(Long id);

    void delete(Long id);
}
