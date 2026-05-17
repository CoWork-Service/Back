package com.cowork.budget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptMatchServiceTest {

    private ReceiptMatchService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptMatchService();
    }

    private static BankStatementParser.BankRow row(String dateTime, long amount, String vendor) {
        return new BankStatementParser.BankRow(LocalDateTime.parse(dateTime), vendor, amount, null);
    }

    @Test
    @DisplayName("시각과 금액이 정확히 일치하면 매칭된다")
    void exactMatch() {
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:30", 38000, "스타벅스"),
                row("2025-05-16T15:00", 12000, "편의점")
        );

        Optional<BankStatementParser.BankRow> result =
                service.match(LocalDateTime.parse("2025-05-16T14:30"), 38000L, bank);

        assertThat(result).isPresent();
        assertThat(result.get().vendor()).isEqualTo("스타벅스");
    }

    @Test
    @DisplayName("시각 차이가 1분이면 매칭된다")
    void oneMinuteDifference() {
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:31", 38000, "스타벅스")
        );

        Optional<BankStatementParser.BankRow> result =
                service.match(LocalDateTime.parse("2025-05-16T14:30"), 38000L, bank);

        assertThat(result).isPresent();
        assertThat(result.get().vendor()).isEqualTo("스타벅스");
    }

    @Test
    @DisplayName("시각 차이가 1분이지만 영수증이 더 늦은 경우도 매칭된다")
    void oneMinuteDifferenceReverse() {
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:30", 38000, "스타벅스")
        );

        Optional<BankStatementParser.BankRow> result =
                service.match(LocalDateTime.parse("2025-05-16T14:31"), 38000L, bank);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("시각 차이가 3분이면 매칭되지 않는다")
    void twoMinutesNoMatch() {
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:33", 38000, "스타벅스")
        );

        Optional<BankStatementParser.BankRow> result =
                service.match(LocalDateTime.parse("2025-05-16T14:30"), 38000L, bank);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("시각은 같아도 금액이 다르면 매칭되지 않는다")
    void amountMismatch() {
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:30", 99000, "스타벅스")
        );

        Optional<BankStatementParser.BankRow> result =
                service.match(LocalDateTime.parse("2025-05-16T14:30"), 38000L, bank);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("영수증 시각의 초 단위는 무시된다")
    void secondsAreIgnored() {
        // 영수증: 14:30:45 → 분 단위 잘라서 14:30으로 비교
        LocalDateTime receiptTime = LocalDateTime.of(2025, 5, 16, 14, 30, 45);

        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:30", 5000, "카페")
        );

        Optional<BankStatementParser.BankRow> result = service.match(receiptTime, 5000L, bank);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("여러 후보 중 시각이 가장 가까운 거래가 반환된다")
    void firstMatchReturned() {
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:29", 38000, "편의점A"),
                row("2025-05-16T14:30", 38000, "스타벅스"),
                row("2025-05-16T14:31", 38000, "편의점B")
        );

        Optional<BankStatementParser.BankRow> result =
                service.match(LocalDateTime.parse("2025-05-16T14:30"), 38000L, bank);

        assertThat(result).isPresent();
        assertThat(result.get().vendor()).isEqualTo("스타벅스");
    }

    @Test
    @DisplayName("시각 차이가 같으면 거래처가 유사한 후보를 우선한다")
    void vendorSimilarityBreaksTie() {
        OcrService.OcrResult receipt = new OcrService.OcrResult(
                LocalDateTime.parse("2025-05-16T14:30"),
                38000L,
                "스타벅스 숭실대점",
                null,
                null,
                null,
                "개인카드",
                null,
                null,
                null,
                "식대",
                "스타벅스 숭실대점 영수증",
                null,
                List.of()
        );
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:31", 38000, "편의점"),
                row("2025-05-16T14:31", 38000, "스타벅스")
        );

        ReceiptMatchService.MatchResult result = service.match(receipt, bank);

        assertThat(result.matched()).isTrue();
        assertThat(result.bankRow().vendor()).isEqualTo("스타벅스");
        assertThat(result.confidence()).isGreaterThan(0);
    }

    @Test
    @DisplayName("bankRows가 비어있으면 empty 반환")
    void emptyBankRows() {
        Optional<BankStatementParser.BankRow> result =
                service.match(LocalDateTime.parse("2025-05-16T14:30"), 38000L, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("receiptTime이 null이면 empty 반환")
    void nullReceiptTime() {
        List<BankStatementParser.BankRow> bank = List.of(
                row("2025-05-16T14:30", 38000, "스타벅스")
        );

        Optional<BankStatementParser.BankRow> result = service.match(null, 38000L, bank);

        assertThat(result).isEmpty();
    }
}
