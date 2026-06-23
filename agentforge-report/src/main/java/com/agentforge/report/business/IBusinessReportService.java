package com.agentforge.report.business;

import java.util.List;

public interface IBusinessReportService {

    void save(BusinessReport report);

    void update(BusinessReport report);

    BusinessReport getById(Long id);

    List<BusinessReport> listRecent(int limit);
}
