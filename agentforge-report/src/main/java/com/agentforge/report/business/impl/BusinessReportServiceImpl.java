package com.agentforge.report.business.impl;

import com.agentforge.report.business.BusinessReport;
import com.agentforge.report.business.IBusinessReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessReportServiceImpl implements IBusinessReportService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(BusinessReport report) {
        jdbcTemplate.update(
            "INSERT INTO af_business_report (template_id, template_name, report_date, status, data_result) VALUES (?,?,?,?,?)",
            report.getTemplateId(), report.getTemplateName(), report.getReportDate(),
            report.getStatus(), toJson(report.getDataResult())
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        report.setId(id);
    }

    @Override
    public void update(BusinessReport report) {
        jdbcTemplate.update(
            "UPDATE af_business_report SET status=?, data_result=?, ai_analysis=? WHERE id=?",
            report.getStatus(), toJson(report.getDataResult()), report.getAiAnalysis(), report.getId()
        );
    }

    @Override
    public BusinessReport getById(Long id) {
        return jdbcTemplate.queryForObject("SELECT * FROM af_business_report WHERE id=?",
            (rs, rowNum) -> mapReport(rs), id);
    }

    @Override
    public List<BusinessReport> listRecent(int limit) {
        return jdbcTemplate.query("SELECT * FROM af_business_report ORDER BY created_at DESC LIMIT ?",
            (rs, rowNum) -> mapReport(rs), limit);
    }

    private BusinessReport mapReport(java.sql.ResultSet rs) throws java.sql.SQLException {
        BusinessReport r = new BusinessReport();
        r.setId(rs.getLong("id"));
        r.setTemplateId(rs.getLong("template_id"));
        r.setTemplateName(rs.getString("template_name"));
        r.setReportDate(rs.getString("report_date"));
        r.setStatus(rs.getString("status"));
        r.setDataResult(fromJson(rs.getString("data_result")));
        r.setAiAnalysis(rs.getString("ai_analysis"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
        return r;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> fromJson(String json) {
        if (json == null) return null;
        try { return objectMapper.readValue(json, java.util.Map.class); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
