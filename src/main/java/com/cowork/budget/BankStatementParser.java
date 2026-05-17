package com.cowork.budget;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BankStatementParser {

    private static final int HEADER_SCAN_LIMIT = 12;
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}|19\\d{2})\\D{0,3}(\\d{1,2})\\D{0,3}(\\d{1,2})");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2})\\s*(?:시|:|：|\\.)\\s*(\\d{1,2})");

    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);

    public record BankRow(LocalDateTime dateTime, String vendor, Long amount, String description) {}

    public List<BankRow> parse(MultipartFile file) throws IOException {
        try (Workbook workbook = createWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<BankRow> rows = new ArrayList<>();

            HeaderInfo header = findHeader(sheet);
            int dateCol = header.dateCol() >= 0 ? header.dateCol() : 0;
            int timeCol = header.timeCol();
            int amountCol = header.amountCol() >= 0 ? header.amountCol() : 1;
            int vendorCol = header.vendorCol() >= 0 ? header.vendorCol() : 2;
            int descCol = header.descCol();

            for (int i = header.dataStartRow(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    LocalDateTime dateTime = parseCellDateTime(row.getCell(dateCol), timeCol >= 0 ? row.getCell(timeCol) : null);
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

            return rows;
        }
    }

    private Workbook createWorkbook(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xls")) {
            return new HSSFWorkbook(file.getInputStream());
        }
        return new XSSFWorkbook(file.getInputStream());
    }

    private HeaderInfo findHeader(Sheet sheet) {
        HeaderInfo best = HeaderInfo.fallback();
        int lastRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_LIMIT);

        for (int i = 0; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            HeaderInfo candidate = inspectHeaderRow(row, i);
            if (candidate.score() > best.score()) {
                best = candidate;
            }
        }
        return best.score() > 0 ? best : HeaderInfo.fallback();
    }

    private HeaderInfo inspectHeaderRow(Row row, int rowIndex) {
        int dateCol = -1, timeCol = -1, withdrawalCol = -1, amountCol = -1, vendorCol = -1, descCol = -1;
        int score = 0;

        for (Cell cell : row) {
            String value = normalizeHeader(getCellString(cell));
            if (!StringUtils.hasText(value)) continue;

            int idx = cell.getColumnIndex();
            if (containsAny(value, "거래일시", "거래일자", "거래일", "일자", "날짜")) {
                dateCol = idx;
                score += 3;
            } else if (containsAny(value, "거래시간", "시간")) {
                timeCol = idx;
                score += 1;
            }

            if (containsAny(value, "출금금액", "출금액", "출금", "지급금액", "찾으신금액", "사용금액")) {
                withdrawalCol = idx;
                score += 3;
            } else if (containsAny(value, "거래금액", "금액", "금액원")) {
                amountCol = idx;
                score += 2;
            }

            if (containsAny(value, "거래처", "사용처", "가맹점", "내용", "적요", "보낸분", "받는분")) {
                vendorCol = idx;
                score += 2;
            } else if (containsAny(value, "메모", "비고", "거래내용", "통장표시내용")) {
                descCol = idx;
                score += 1;
            }
        }

        return new HeaderInfo(rowIndex + 1, dateCol, timeCol, withdrawalCol >= 0 ? withdrawalCol : amountCol, vendorCol, descCol, score);
    }

    private String normalizeHeader(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s", "").replaceAll("[()\\[\\]]", "").toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private LocalDateTime parseCellDateTime(Cell dateCell, Cell timeCell) {
        LocalDateTime dateTime = parseCellDateTime(dateCell);
        if (dateTime == null) return null;

        int[] parsedTime = parseTime(getCellString(timeCell));
        if (parsedTime == null) return dateTime;
        return dateTime.toLocalDate().atTime(parsedTime[0], parsedTime[1]);
    }

    private LocalDateTime parseCellDateTime(Cell cell) {
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            try {
                return cell.getLocalDateTimeCellValue();
            } catch (Exception ignored) {
            }
        }

        return parseStringDateTime(getCellString(cell));
    }

    LocalDateTime parseStringDateTime(String value) {
        if (!StringUtils.hasText(value)) return null;

        String normalized = value.trim().replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ");
        String[][] dateTimeFormats = {
                {"yyyy-MM-dd HH:mm:ss"},
                {"yyyy-MM-dd HH:mm"},
                {"yyyy/MM/dd HH:mm:ss"},
                {"yyyy/MM/dd HH:mm"},
                {"yyyy.MM.dd HH:mm:ss"},
                {"yyyy.MM.dd HH:mm"},
                {"yyyyMMdd HHmmss"},
                {"yyyyMMddHHmmss"},
        };
        for (String[] fmt : dateTimeFormats) {
            try {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(fmt[0]));
            } catch (Exception ignored) {
            }
        }

        LocalDate date = parseDate(normalized);
        if (date == null) return null;
        int[] time = parseTime(normalized);
        return time == null ? date.atStartOfDay() : date.atTime(time[0], time[1]);
    }

    Long parseAmount(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return Math.abs((long) cell.getNumericCellValue());
        }
        String value = getCellString(cell);
        if (!StringUtils.hasText(value)) return null;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return null;
        try {
            return Math.abs(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        Matcher matcher = DATE_PATTERN.matcher(value);
        if (matcher.find()) {
            return toDate(matcher.group(1), matcher.group(2), matcher.group(3));
        }

        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            return toDate(digits.substring(0, 4), digits.substring(4, 6), digits.substring(6, 8));
        }
        if (digits.length() == 6) {
            return toDate("20" + digits.substring(0, 2), digits.substring(2, 4), digits.substring(4, 6));
        }
        return null;
    }

    private LocalDate toDate(String yearText, String monthText, String dayText) {
        try {
            return LocalDate.of(Integer.parseInt(yearText), Integer.parseInt(monthText), Integer.parseInt(dayText));
        } catch (Exception e) {
            return null;
        }
    }

    private int[] parseTime(String value) {
        if (!StringUtils.hasText(value)) return null;
        Matcher matcher = TIME_PATTERN.matcher(value);
        if (!matcher.find()) return null;
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
        return new int[]{hour, minute};
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return dataFormatter.formatCellValue(cell).trim();
    }

    private record HeaderInfo(int dataStartRow, int dateCol, int timeCol, int amountCol, int vendorCol, int descCol, int score) {
        static HeaderInfo fallback() {
            return new HeaderInfo(1, -1, -1, -1, -1, -1, 0);
        }
    }
}
