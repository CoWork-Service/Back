package com.cowork.budget;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class BankStatementParser {

    public record BankRow(LocalDateTime dateTime, String vendor, Long amount, String description) {}

    public List<BankRow> parse(MultipartFile file) throws IOException {
        Workbook workbook = createWorkbook(file);
        Sheet sheet = workbook.getSheetAt(0);
        List<BankRow> rows = new ArrayList<>();

        int dateCol = -1, amountCol = -1, vendorCol = -1, descCol = -1;
        Row header = sheet.getRow(0);
        if (header != null) {
            for (Cell cell : header) {
                String val = getCellString(cell).replaceAll("\\s", "");
                int idx = cell.getColumnIndex();
                if (val.contains("거래일") || val.contains("날짜") || val.contains("일시")) dateCol = idx;
                else if (val.contains("거래금액") || val.contains("출금") || val.contains("금액")) amountCol = idx;
                else if (val.contains("거래처") || val.contains("내용") || val.contains("적요")) vendorCol = idx;
                else if (val.contains("메모") || val.contains("비고")) descCol = idx;
            }
        }

        if (dateCol == -1) dateCol = 0;
        if (amountCol == -1) amountCol = 1;
        if (vendorCol == -1) vendorCol = 2;

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            try {
                LocalDateTime dateTime = parseCellDateTime(row.getCell(dateCol));
                Long amount = parseAmount(row.getCell(amountCol));
                String vendor = getCellString(row.getCell(vendorCol));
                String desc = descCol >= 0 ? getCellString(row.getCell(descCol)) : null;

                if (dateTime != null && amount != null && amount > 0) {
                    rows.add(new BankRow(dateTime, vendor, amount, desc));
                }
            } catch (Exception e) {
                log.debug("행 {} 파싱 실패: {}", i, e.getMessage());
            }
        }

        workbook.close();
        return rows;
    }

    private Workbook createWorkbook(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.endsWith(".xls")) {
            return new HSSFWorkbook(file.getInputStream());
        }
        return new XSSFWorkbook(file.getInputStream());
    }

    /**
     * Excel 셀에서 LocalDateTime을 추출한다.
     * - 날짜 형식 셀: POI가 직접 LocalDateTime으로 변환
     * - 문자열 셀: "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd" 등 파싱
     */
    private LocalDateTime parseCellDateTime(Cell cell) {
        if (cell == null) return null;

        // POI가 날짜 셀로 인식한 경우
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }

        return parseStringDateTime(getCellString(cell));
    }

    LocalDateTime parseStringDateTime(String value) {
        if (value == null || value.isBlank()) return null;

        // 날짜+시간 형식 시도 (순서 중요: 긴 패턴 먼저)
        String[][] dateTimeFormats = {
                {"yyyy-MM-dd HH:mm:ss", null},
                {"yyyy-MM-dd HH:mm",    null},
                {"yyyy/MM/dd HH:mm:ss", null},
                {"yyyy/MM/dd HH:mm",    null},
                {"yyyyMMdd HHmmss",     null},
                {"yyyyMMddHHmmss",      null},
        };
        for (String[] fmt : dateTimeFormats) {
            try {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern(fmt[0]));
            } catch (Exception ignored) {}
        }

        // 날짜만 있는 경우 → 시간은 00:00으로
        String[] dateFormats = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "yyyy.MM.dd"};
        String datePart = value.trim().length() >= 10 ? value.trim().substring(0, 10) : value.trim();
        for (String fmt : dateFormats) {
            try {
                return LocalDate.parse(datePart, DateTimeFormatter.ofPattern(fmt)).atStartOfDay();
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Long parseAmount(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (long) cell.getNumericCellValue();
        }
        String str = getCellString(cell).replaceAll("[^0-9]", "");
        if (str.isBlank()) return null;
        return Long.parseLong(str);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
