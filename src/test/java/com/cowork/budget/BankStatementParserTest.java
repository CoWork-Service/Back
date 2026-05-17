package com.cowork.budget;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BankStatementParserTest {

    private final BankStatementParser parser = new BankStatementParser();

    @Test
    @DisplayName("상단 안내 행이 있어도 거래내역 헤더를 찾아 파싱한다")
    void parsesHeaderBelowTitleRows() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("statement");
        sheet.createRow(0).createCell(0).setCellValue("거래내역조회");
        Row header = sheet.createRow(2);
        header.createCell(0).setCellValue("거래일자");
        header.createCell(1).setCellValue("거래시간");
        header.createCell(2).setCellValue("출금금액");
        header.createCell(3).setCellValue("가맹점");
        header.createCell(4).setCellValue("메모");

        Row row = sheet.createRow(3);
        row.createCell(0).setCellValue("2025.05.16");
        row.createCell(1).setCellValue("14:30");
        row.createCell(2).setCellValue("38,000");
        row.createCell(3).setCellValue("스타벅스");
        row.createCell(4).setCellValue("회의 음료");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "statement.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray()
        );

        List<BankStatementParser.BankRow> rows = parser.parse(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).dateTime()).isEqualTo(LocalDateTime.of(2025, 5, 16, 14, 30));
        assertThat(rows.get(0).amount()).isEqualTo(38000L);
        assertThat(rows.get(0).vendor()).isEqualTo("스타벅스");
        assertThat(rows.get(0).description()).isEqualTo("회의 음료");
    }

    @Test
    @DisplayName("문자열 날짜에서 요일 표기를 제거하고 파싱한다")
    void parsesDateWithWeekday() {
        LocalDateTime result = parser.parseStringDateTime("2025.05.16(금) 09:05");

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 16, 9, 5));
    }
}
