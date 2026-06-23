package com.agentforge.report.reconciliation.impl;

import com.agentforge.common.model.reconciliation.ColumnMapping;
import com.agentforge.common.model.reconciliation.ReconciliationRecord;
import com.agentforge.report.reconciliation.IFileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 文件上传与解析服务
 * 作用：接收第三方对账文件（CSV/Excel），解析为统一的 ReconciliationRecord 列表
 *
 * 支持格式：
 * - CSV：自动检测编码（UTF-8/GBK），逗号或制表符分隔
 * - Excel（.xlsx）：通过 Apache POI 解析
 *
 * 文件大小限制：10MB
 */
@Slf4j
@Service
public class FileUploadServiceImpl implements IFileUploadService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 解析上传文件，返回记录列表和列名信息
     * 作用：统一入口，根据文件扩展名分发到 CSV 或 Excel 解析
     *
     * @param file 上传的文件
     * @return ParseResult 包含记录列表、列名数组、行数
     */
    @Override
    public ParseResult parse(MultipartFile file) {
        validateFile(file);

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();

        try {
            if (".csv".equals(ext)) {
                return parseCsv(file);
            } else if (".xlsx".equals(ext) || ".xls".equals(ext)) {
                return parseExcel(file);
            } else {
                throw new IllegalArgumentException("不支持的文件格式：" + ext + "，仅支持 CSV 和 Excel（.xlsx）");
            }
        } catch (IOException e) {
            log.error("文件解析失败：{}", e.getMessage());
            throw new RuntimeException("文件解析失败：" + e.getMessage());
        }
    }

    /**
     * 根据 ColumnMapping 将原始行数据转换为 ReconciliationRecord
     * 作用：把 Map<列名, 值> 转为标准化的对账记录
     */
    @Override
    public List<ReconciliationRecord> mapToRecords(List<Map<String, String>> rows, ColumnMapping mapping, String source) {
        List<ReconciliationRecord> records = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            Map<String, String> fieldMapping = mapping.getMapping();

            String tranno = fieldMapping.containsKey("tranno") ? row.get(fieldMapping.get("tranno")) : null;
            String cusid = fieldMapping.containsKey("cusid") ? row.get(fieldMapping.get("cusid")) : null;
            String amountStr = fieldMapping.containsKey("amount") ? row.get(fieldMapping.get("amount")) : null;
            String date = fieldMapping.containsKey("date") ? row.get(fieldMapping.get("date")) : null;
            String status = fieldMapping.containsKey("status") ? row.get(fieldMapping.get("status")) : null;

            BigDecimal amount = parseAmount(amountStr);

            records.add(ReconciliationRecord.builder()
                    .tranno(tranno != null ? tranno.trim() : null)
                    .cusid(cusid != null ? cusid.trim() : null)
                    .amount(amount)
                    .date(date != null ? date.trim() : null)
                    .status(status != null ? status.trim() : null)
                    .rowNumber(i + 2) // +2 因为第1行是表头，数据从第2行开始
                    .source(source)
                    .build());
        }

        return records;
    }

    /**
     * 自动检测列映射
     * 作用：上传后预览时调用，返回建议的列映射供用户确认或调整
     */
    @Override
    public ColumnMapping detectColumnMapping(ParseResult parseResult) {
        return ColumnMapping.autoDetect(parseResult.columns);
    }

    // ==================== CSV 解析 ====================

    private ParseResult parseCsv(MultipartFile file) throws IOException {
        // 检测编码
        byte[] bytes = file.getBytes();
        Charset charset = detectCharset(bytes);

        List<Map<String, String>> rows = new ArrayList<>();
        String[] columns = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), charset))) {

            // 读取表头
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV文件为空或没有表头行");
            }

            // 检测分隔符：逗号、制表符、分号
            char delimiter = detectDelimiter(headerLine);
            columns = splitLine(headerLine, delimiter);

            // 去除 BOM 标记
            if (columns.length > 0) {
                columns[0] = columns[0].replace("﻿", "").trim();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = splitLine(line, delimiter);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.length && i < values.length; i++) {
                    row.put(columns[i].trim(), values[i].trim());
                }
                rows.add(row);
            }
        }

        log.info("CSV解析完成：{} 列，{} 行数据", columns.length, rows.size());
        return new ParseResult(columns, rows);
    }

    // ==================== Excel 解析 ====================

    private ParseResult parseExcel(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] columns = null;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Excel文件为空");
            }

            // 第一行为表头
            Row headerRow = sheet.getRow(0);
            columns = new String[headerRow.getLastCellNum()];
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.getCell(i);
                columns[i] = cell != null ? getCellValueAsString(cell).trim() : "column_" + i;
            }

            // 数据行
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;
                for (int c = 0; c < columns.length; c++) {
                    Cell cell = row.getCell(c);
                    String value = cell != null ? getCellValueAsString(cell).trim() : "";
                    rowData.put(columns[c], value);
                    if (!value.isEmpty()) hasData = true;
                }
                if (hasData) {
                    rows.add(rowData);
                }
            }
        }

        log.info("Excel解析完成：{} 列，{} 行数据", columns.length, rows.size());
        return new ParseResult(columns, rows);
    }

    // ==================== 工具方法 ====================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过10MB限制");
        }
    }

    private Charset detectCharset(byte[] bytes) {
        // 检查 BOM
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        // 尝试 UTF-8 解码，失败则回退 GBK
        String test = new String(bytes, StandardCharsets.UTF_8);
        if (!test.contains("�")) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName("GBK");
    }

    private char detectDelimiter(String line) {
        long commas = line.chars().filter(c -> c == ',').count();
        long tabs = line.chars().filter(c -> c == '\t').count();
        long semicolons = line.chars().filter(c -> c == ';').count();

        if (tabs > commas && tabs > semicolons) return '\t';
        if (semicolons > commas) return ';';
        return ',';
    }

    private String[] splitLine(String line, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                parts.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString().trim());
        return parts.toArray(new String[0]);
    }

    private String getCellValueAsString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                // 避免科学计数法，日期按数值处理
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return null;
        try {
            // 去除逗号、空格、货币符号
            String cleaned = amountStr.replaceAll("[,\\s¥$￥]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
