package com.agentforge.report.reconciliation;

import com.agentforge.common.model.reconciliation.ReconciliationResult;

public interface IReconciliationSummaryGenerator {

    String generate(ReconciliationResult result);
}
