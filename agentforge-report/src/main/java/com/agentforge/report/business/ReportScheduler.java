package com.agentforge.report.business;

  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.scheduling.annotation.Scheduled;
  import org.springframework.scheduling.support.CronExpression;
  import org.springframework.stereotype.Component;

  import java.time.LocalDateTime;
  import java.util.List;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class ReportScheduler {

      private final IReportTemplateService templateService;
      private final ReportExecutor reportExecutor;

      /** 每分钟检查一次是否有模板到了执行时间 */
      @Scheduled(fixedRate = 60_000)
      public void checkAndExecute() {
          List<ReportTemplate> templates = templateService.list();
          LocalDateTime now = LocalDateTime.now();

          for (ReportTemplate t : templates) {
              if (t.getSchedule() == null || t.getSchedule().isBlank()) continue;
              if (t.getEnabled() == null || !t.getEnabled()) continue;

              try {
                  CronExpression cron = CronExpression.parse(t.getSchedule());
                  // 检查当前时间是否匹配cron（允许±1分钟窗口）
                  LocalDateTime next = cron.next(now.minusMinutes(1));
                  if (next != null && !next.isAfter(now.plusMinutes(1))) {
                      log.info("定时触发报告: {} schedule={}", t.getName(), t.getSchedule());
                      reportExecutor.execute(t.getId());
                  }
              } catch (Exception e) {
                  log.error("cron解析失败 template={} schedule={}", t.getName(), t.getSchedule(), e);
              }
          }
      }
  }