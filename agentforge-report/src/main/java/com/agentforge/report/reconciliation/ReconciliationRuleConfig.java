package com.agentforge.report.reconciliation;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 对账规则配置
 * 可通过API动态修改：匹配策略、金额容差、告警阈值
 */
@Component
public class ReconciliationRuleConfig {

    private volatile Rules rules = Rules.defaults();

    public Rules getRules() {
        return rules;
    }

    public void updateRules(Rules newRules) {
        this.rules = newRules;
    }

    @Data
    public static class Rules {
        /** 默认匹配策略：EXACT / FUZZY */
        private String defaultStrategy = "FUZZY";

        /** 金额容差（元），小于此值视为匹配 */
        private BigDecimal amountTolerance = new BigDecimal("1.00");

        /** 差异告警阈值（元），差异总额超过此值触发风险告警 */
        private BigDecimal alertThreshold = new BigDecimal("1000.00");

        /** 最大处理记录数 */
        private int maxRecords = 5000;

        public static Rules defaults() {
            return new Rules();
        }
    }
}
