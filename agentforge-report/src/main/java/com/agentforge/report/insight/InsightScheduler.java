package com.agentforge.report.insight;

import com.agentforge.common.constant.DateFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 定期洞察调度：周度（每周一）/ 月度（每月1日）自动生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightScheduler {

    private final IInsightService insightService;
    private static final String PERIOD_WEEKLY = "WEEKLY";
    private static final String PERIOD_MONTHLY = "MONTHLY";

    /** 每周一 8:07 生成上周周度洞察。 */
    @Scheduled(cron = "${agentforge.scheduler.insight-weekly-cron:0 7 8 ? * MON}")
    public void weekly() {
        String end = LocalDate.now().format(DateFormats.COMPACT_DATE);
        String start = LocalDate.now().minusDays(7).format(DateFormats.COMPACT_DATE);
        log.info("定时周度洞察: {} - {}", start, end);
        try {
            insightService.generate(PERIOD_WEEKLY, start, end);
        } catch (Exception e) {
            log.error("周度洞察生成失败", e);
        }
    }

    /** 每月 1 日 9:03 生成上月月度洞察。 */
    @Scheduled(cron = "${agentforge.scheduler.insight-monthly-cron:0 3 9 1 * ?}")
    public void monthly() {
        String end = LocalDate.now().minusDays(1).format(DateFormats.COMPACT_DATE);
        String start = LocalDate.now().withDayOfMonth(1).minusMonths(1).format(DateFormats.COMPACT_DATE);
        log.info("定时月度洞察: {} - {}", start, end);
        try {
            insightService.generate(PERIOD_MONTHLY, start, end);
        } catch (Exception e) {
            log.error("月度洞察生成失败", e);
        }
    }
}
