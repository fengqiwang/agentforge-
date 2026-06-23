package com.agentforge.report.reconciliation;

import com.agentforge.common.model.reconciliation.ReconciliationResult;

import java.io.IOException;

public interface IReconciliationExporter {

    byte[] exportExcel(ReconciliationResult result, String batchName,
                       String aiSummary) throws IOException;
}
