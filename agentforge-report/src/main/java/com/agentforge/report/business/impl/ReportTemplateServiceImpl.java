package com.agentforge.report.business.impl;

import com.agentforge.report.business.IReportTemplateService;
import com.agentforge.report.business.ReportTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportTemplateServiceImpl implements IReportTemplateService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ReportTemplate create(ReportTemplate template) {
        String queriesJson = toJson(template.getQueries());
        jdbcTemplate.update(
            "INSERT INTO af_report_template (name, description, queries, schedule, enabled, created_by) VALUES (?,?,?,?,?,?)",
            template.getName(), template.getDescription(), queriesJson,
            template.getSchedule(), template.getEnabled() != null && template.getEnabled() ? 1 : 0,
            template.getCreatedBy()
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        template.setId(id);
        return template;
    }

    @Override
    public ReportTemplate update(Long id, ReportTemplate template) {
        String queriesJson = toJson(template.getQueries());
        jdbcTemplate.update(
            "UPDATE af_report_template SET name=?, description=?, queries=?, schedule=?, enabled=? WHERE id=?",
            template.getName(), template.getDescription(), queriesJson,
            template.getSchedule(), template.getEnabled() != null && template.getEnabled() ? 1 : 0, id
        );
        template.setId(id);
        return template;
    }

    @Override
    public List<ReportTemplate> list() {
        return jdbcTemplate.query("SELECT * FROM af_report_template ORDER BY updated_at DESC",
            (rs, rowNum) -> mapTemplate(rs));
    }

    @Override
    public ReportTemplate getById(Long id) {
        return jdbcTemplate.queryForObject("SELECT * FROM af_report_template WHERE id=?",
            (rs, rowNum) -> mapTemplate(rs), id);
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM af_report_template WHERE id=?", id);
    }

    private ReportTemplate mapTemplate(ResultSet rs) throws java.sql.SQLException {
        ReportTemplate t = new ReportTemplate();
        t.setId(rs.getLong("id"));
        t.setName(rs.getString("name"));
        t.setDescription(rs.getString("description"));
        t.setQueries(fromJson(rs.getString("queries")));
        t.setSchedule(rs.getString("schedule"));
        t.setEnabled(rs.getInt("enabled") == 1);
        t.setCreatedBy(rs.getString("created_by"));
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        t.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return t;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private List<ReportTemplate.QueryItem> fromJson(String json) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ReportTemplate.QueryItem.class));
        } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
