package com.agentforge.web.controller;

import com.agentforge.common.model.reconciliation.ReconciliationResult;
import com.agentforge.report.reconciliation.IReconciliationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 对账 REST API
 * 作用：提供文件上传预览、对账匹配执行、结果查询等接口
 *
 * 接口设计：
 * - POST /upload：上传文件并预览数据 + 自动列映射建议
 * - POST /match：确认列映射后执行对账匹配
 * - GET /list：对账批次列表
 * - GET /{id}：对账结果概览
 * - GET /{id}/details：差异明细分页查询
 */
@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final IReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    /**
     * 上传对账文件并预览
     * 作用：解析文件后返回列名、前10行数据、自动列映射建议
     *
     * 请求：multipart/form-data，字段名 file
     * 返回：{ columns, rowCount, previewRows, autoMapping }
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadAndPreview(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> preview = reconciliationService.uploadAndPreview(file);
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文件解析失败：" + e.getMessage()));
        }
    }

    /**
     * 执行对账匹配
     * 作用：用户确认列映射后，执行完整对账流程
     *
     * 请求：multipart/form-data
     *   - file: 对账文件
     *   - name: 批次名称
     *   - columnMapping: JSON字符串，如 {"tranno":"交易流水号","amount":"金额"}
     *   - matchStrategy: EXACT 或 FUZZY（默认）
     */
    @PostMapping("/match")
    public ResponseEntity<?> executeMatch(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "columnMapping", required = false) String columnMappingJson,
            @RequestParam(value = "matchStrategy", defaultValue = "FUZZY") String matchStrategy) {

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "批次名称不能为空"));
        }

        try {
            // 解析列映射JSON
            Map<String, String> columnMapping = null;
            if (columnMappingJson != null && !columnMappingJson.isBlank()) {
                columnMapping = objectMapper.readValue(columnMappingJson, new TypeReference<>() {});
            }

            // 如果没有提供映射，先解析文件自动检测
            if (columnMapping == null || columnMapping.isEmpty()) {
                var parseResult = reconciliationService.uploadAndPreview(file);
                @SuppressWarnings("unchecked")
                Map<String, String> autoMapping = (Map<String, String>) parseResult.get("autoMapping");
                columnMapping = autoMapping;
            }

            ReconciliationResult result = reconciliationService.executeMatch(
                    name, file, columnMapping, matchStrategy);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "对账执行失败：" + e.getMessage()));
        }
    }

    /**
     * 对账批次列表
     */
    @GetMapping("/list")
    public List<Map<String, Object>> list() {
        return reconciliationService.listBatches();
    }

    /**
     * 对账结果概览
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getBatch(@PathVariable Long id) {
        Map<String, Object> result = reconciliationService.getBatch(id);
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 差异明细分页查询
     *
     * @param id 对账批次ID
     * @param type 筛选类型：MATCHED/AMOUNT_DIFF/ONLY_INTERNAL/ONLY_EXTERNAL（可选）
     * @param page 页码（从0开始）
     * @param size 每页大小
     */
    @GetMapping("/{id}/details")
    public Map<String, Object> getDetails(
            @PathVariable Long id,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return reconciliationService.getDetails(id, type, page, size);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBatch(@PathVariable Long id) {
        boolean deleted = reconciliationService.deleteBatch(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "对账记录不存在"));
        }
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
