package com.agentforge.report.reconciliation.impl;

import com.agentforge.common.model.reconciliation.ReconciliationResult;
import com.agentforge.report.reconciliation.IReconciliationExporter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class ReconciliationExporterImpl implements IReconciliationExporter {

    @Override
    public byte[] exportExcel(ReconciliationResult result, String batchName,
                              String aiSummary) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            createOverviewSheet(workbook, result, batchName, aiSummary);
            createAllDetailsSheet(workbook, result);
            createDiffOnlySheet(workbook, result);
            workbook.write(out);
            log.info("Excel导出完成：{} bytes", out.size());
            return out.toByteArray();
        }
    }

    private void createOverviewSheet(Workbook wb, ReconciliationResult r,
                                     String batchName, String aiSummary) {
        Sheet sheet = wb.createSheet("对账概览");
        CellStyle titleStyle = wb.createCellStyle();
        Font titleFont = wb.createFont();
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setBold(true);
        titleStyle.setFont(titleFont);
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("对账报告：" + batchName);
        titleCell.setCellStyle(titleStyle);

        String[][] overview = {
                {"我方总笔数", String.valueOf(r.getTotalInternal())},
                {"第三方总笔数", String.valueOf(r.getTotalExternal())},
                {"匹配成功", String.valueOf(r.getMatchedCount())},
                {"金额差异", String.valueOf(r.getAmountDiffCount())},
                {"仅我方有", String.valueOf(r.getOnlyInternalCount())},
                {"仅第三方有", String.valueOf(r.getOnlyExternalCount())},
                {"我方总金额", r.getInternalTotal() != null ? r.getInternalTotal().toPlainString() : "0"},
                {"第三方总金额", r.getExternalTotal() != null ? r.getExternalTotal().toPlainString() : "0"},
                {"差异金额", r.getDiffAmount() != null ? r.getDiffAmount().toPlainString() : "0"},
        };

        int rowIdx = 2;
        for (String[] line : overview) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(line[0]);
            row.createCell(1).setCellValue(line[1]);
        }

        int total = r.getTotalInternal() + r.getTotalExternal();
        double matchRate = total > 0 ? r.getMatchedCount() * 100.0 / total : 0;
        Row rateRow = sheet.createRow(rowIdx++);
        rateRow.createCell(0).setCellValue("匹配率");
        rateRow.createCell(1).setCellValue(String.format("%.1f%%", matchRate));

        if (aiSummary != null && !aiSummary.isBlank()) {
            rowIdx++;
            Row summaryTitle = sheet.createRow(rowIdx++);
            Cell stCell = summaryTitle.createCell(0);
            stCell.setCellValue("AI分析摘要");
            stCell.setCellStyle(headerStyle);
            Row summaryRow = sheet.createRow(rowIdx);
            Cell summaryCell = summaryRow.createCell(0);
            summaryCell.setCellValue(aiSummary);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 5));
        }
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 8000);
    }

    private void createAllDetailsSheet(Workbook wb, ReconciliationResult r) {
        Sheet sheet = wb.createSheet("全部明细");
        String[] headers = {"匹配类型", "我方商户号", "我方金额", "我方日期",
                "第三方商户号", "第三方金额", "第三方日期", "差异金额"};
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        if (r.getDiffDetails() != null) {
            for (ReconciliationResult.DiffDetail d : r.getDiffDetails()) {
                Row row = sheet.createRow(rowIdx++);
                writeDetailRow(row, d);
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 4000);
        }
        if (rowIdx > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIdx - 1, 0, headers.length - 1));
        }
    }

    private void createDiffOnlySheet(Workbook wb, ReconciliationResult r) {
        Sheet sheet = wb.createSheet("差异明细");
        String[] headers = {"匹配类型", "我方商户号", "我方金额", "我方日期",
                "第三方商户号", "第三方金额", "第三方日期", "差异金额"};
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        if (r.getDiffDetails() != null) {
            for (ReconciliationResult.DiffDetail d : r.getDiffDetails()) {
                if (!ReconciliationResult.MATCHED.equals(d.getMatchType())) {
                    Row row = sheet.createRow(rowIdx++);
                    writeDetailRow(row, d);
                }
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 4000);
        }
        if (rowIdx > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIdx - 1, 0, headers.length - 1));
        }
    }

    private void writeDetailRow(Row row, ReconciliationResult.DiffDetail d) {
        row.createCell(0).setCellValue(d.getMatchType() != null ? d.getMatchType() : "");
        row.createCell(1).setCellValue(d.getInternalCusid() != null ? d.getInternalCusid() : "");
        row.createCell(2).setCellValue(d.getInternalAmount() != null ? d.getInternalAmount().toPlainString() : "");
        row.createCell(3).setCellValue(d.getInternalDate() != null ? d.getInternalDate() : "");
        row.createCell(4).setCellValue(d.getExternalCusid() != null ? d.getExternalCusid() : "");
        row.createCell(5).setCellValue(d.getExternalAmount() != null ? d.getExternalAmount().toPlainString() : "");
        row.createCell(6).setCellValue(d.getExternalDate() != null ? d.getExternalDate() : "");
        row.createCell(7).setCellValue(d.getDiffAmount() != null ? d.getDiffAmount().toPlainString() : "");
    }
}
