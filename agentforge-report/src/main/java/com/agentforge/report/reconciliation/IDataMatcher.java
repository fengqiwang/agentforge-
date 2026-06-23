package com.agentforge.report.reconciliation;

import com.agentforge.common.model.reconciliation.ReconciliationRecord;
import com.agentforge.common.model.reconciliation.ReconciliationResult;

import java.util.List;

public interface IDataMatcher {

    ReconciliationResult match(List<ReconciliationRecord> internalRecords,
                               List<ReconciliationRecord> externalRecords,
                               String strategy);
}
