package com.agentforge.report.business;

  import com.agentforge.common.constant.DateFormats;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.stereotype.Component;

  import java.time.LocalDate;
  import java.util.*;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class ReportExecutor {

      private final JdbcTemplate jdbcTemplate;
      private final IReportTemplateService templateService;
      private final IBusinessReportService reportService;
      private final ObjectMapper objectMapper;
      private final ReportAssembler reportAssembler;

      public BusinessReport execute(Long templateId) {
          ReportTemplate template = templateService.getById(templateId);

          // 1. 创建报告记录，状态=GENERATING
          BusinessReport report = new BusinessReport();
          report.setTemplateId(templateId);
          report.setTemplateName(template.getName());
          report.setReportDate(LocalDate.now().format(DateFormats.YEAR_MONTH));
          report.setStatus(ReportStatus.GENERATING.getCode());
          reportService.save(report);

          // 2. 逐条执行SQL，收集结果
          Map<String, Object> dataResult = new LinkedHashMap<>();
          try {
              for (ReportTemplate.QueryItem query : template.getQueries()) {
                  Map<String, Object> queryResult = executeQuery(query);
                  dataResult.put(query.getName(), queryResult);
              }
              report.setDataResult(dataResult);
              report.setStatus(ReportStatus.COMPLETED.getCode());
          } catch (Exception e) {
              log.error("报告执行失败: template={}", templateId, e);
              report.setDataResult(dataResult);  // 保留已成功的部分
              report.setStatus(ReportStatus.FAILED.getCode());
              report.setErrorMessage("执行错误: " + e.getMessage());
          }

          reportService.update(report);

          // Week 8：执行成功后自动组装 AI 分析
          if (ReportStatus.COMPLETED.getCode().equals(report.getStatus())) {
              try {
                  report = reportAssembler.assemble(report);
              } catch (Exception e) {
                  log.warn("AI 分析组装失败，不影响报告数据: {}", e.getMessage());
              }
          }
          return report;
      }

      private Map<String, Object> executeQuery(ReportTemplate.QueryItem query) {
          String sql = query.getSql().trim();
          if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

          // 只读安全：包装为子查询，禁止写操作
          String safeSql = "SELECT * FROM (" + sql + ") AS _report_sub";
          List<Map<String, Object>> rows = jdbcTemplate.queryForList(safeSql);

          Map<String, Object> result = new LinkedHashMap<>();
          result.put("count", rows.size());
          result.put("rows", rows);
          return result;
      }
  }