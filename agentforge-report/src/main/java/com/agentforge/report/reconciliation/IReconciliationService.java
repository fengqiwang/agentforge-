package com.agentforge.report.reconciliation;

import com.agentforge.common.model.reconciliation.ReconciliationResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IReconciliationService {

    Map<String, Object> uploadAndPreview(MultipartFile file);

    ReconciliationResult executeMatch(String name, MultipartFile file,
                                      Map<String, String> columnMapping,
                                      String matchStrategy);

    List<Map<String, Object>> listBatches();

    Map<String, Object> getBatch(Long id);

    boolean deleteBatch(Long id);

    Map<String, Object> getDetails(Long batchId, String type, int page, int size);

    ReconciliationResult getResult(Long batchId);
}
