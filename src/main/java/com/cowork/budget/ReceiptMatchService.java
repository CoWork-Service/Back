package com.cowork.budget;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 영수증 OCR 결과와 통장 거래 내역을 매칭한다.
 *
 * 핵심 조건:
 *   1. 금액 일치
 *   2. 결제 시각 차이 ±TOLERANCE_MINUTES 이내
 *   3. 여러 후보가 있으면 시각 차이와 거래처 문자열 유사도로 가장 확실한 행을 선택
 */
@Service
public class ReceiptMatchService {

    static final long TOLERANCE_MINUTES = 2;

    public record MatchResult(
            BankStatementParser.BankRow bankRow,
            boolean matched,
            double confidence,
            String reason,
            int candidateCount,
            Long timeDifferenceMinutes
    ) {}

    public Optional<BankStatementParser.BankRow> match(
            LocalDateTime receiptTime,
            Long receiptAmount,
            List<BankStatementParser.BankRow> bankRows) {
        return matchDetailed(receiptTime, receiptAmount, null, bankRows)
                .filter(MatchResult::matched)
                .map(MatchResult::bankRow);
    }

    public MatchResult match(OcrService.OcrResult receipt, List<BankStatementParser.BankRow> bankRows) {
        if (receipt == null) {
            return noMatch("OCR 결과가 없습니다.", 0);
        }
        return matchDetailed(receipt.dateTime(), receipt.amount(), receipt.vendor(), bankRows)
                .orElseGet(() -> noMatch("매칭 조건을 만족하는 거래가 없습니다.", 0));
    }

    private Optional<MatchResult> matchDetailed(
            LocalDateTime receiptTime,
            Long receiptAmount,
            String receiptVendor,
            List<BankStatementParser.BankRow> bankRows) {

        if (receiptTime == null || receiptAmount == null || bankRows == null || bankRows.isEmpty()) {
            return Optional.empty();
        }

        LocalDateTime receiptMinute = receiptTime.truncatedTo(ChronoUnit.MINUTES);
        List<Candidate> candidates = bankRows.stream()
                .filter(row -> row.dateTime() != null)
                .filter(row -> Objects.equals(row.amount(), receiptAmount))
                .map(row -> candidate(receiptMinute, receiptVendor, row))
                .filter(candidate -> candidate.timeDifferenceMinutes() <= TOLERANCE_MINUTES)
                .sorted(Comparator
                        .comparingLong(Candidate::timeDifferenceMinutes)
                        .thenComparing(Comparator.comparingDouble(Candidate::vendorScore).reversed()))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Candidate best = candidates.get(0);
        double confidence = Math.max(0.0, Math.min(1.0,
                0.95 - (best.timeDifferenceMinutes() * 0.12) + (best.vendorScore() * 0.08)));
        String reason = "금액 일치, 결제시각 %d분 차이%s".formatted(
                best.timeDifferenceMinutes(),
                best.vendorScore() > 0 ? ", 거래처 유사" : ""
        );

        return Optional.of(new MatchResult(
                best.row(),
                true,
                confidence,
                reason,
                candidates.size(),
                best.timeDifferenceMinutes()
        ));
    }

    private Candidate candidate(LocalDateTime receiptMinute, String receiptVendor, BankStatementParser.BankRow row) {
        LocalDateTime bankMinute = row.dateTime().truncatedTo(ChronoUnit.MINUTES);
        long diff = Math.abs(Duration.between(receiptMinute, bankMinute).toMinutes());
        return new Candidate(row, diff, vendorScore(receiptVendor, row.vendor(), row.description()));
    }

    private double vendorScore(String receiptVendor, String bankVendor, String bankDescription) {
        String receipt = normalize(receiptVendor);
        if (!StringUtils.hasText(receipt)) return 0.0;
        String bank = normalize((bankVendor == null ? "" : bankVendor) + " " + (bankDescription == null ? "" : bankDescription));
        if (!StringUtils.hasText(bank)) return 0.0;
        if (bank.contains(receipt) || receipt.contains(bank)) return 1.0;

        int commonChars = 0;
        for (int i = 0; i < receipt.length(); i++) {
            if (bank.indexOf(receipt.charAt(i)) >= 0) commonChars++;
        }
        return receipt.isEmpty() ? 0.0 : Math.min(0.8, commonChars / (double) receipt.length());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9A-Za-z가-힣]", "").toLowerCase();
    }

    private MatchResult noMatch(String reason, int candidateCount) {
        return new MatchResult(null, false, 0.0, reason, candidateCount, null);
    }

    private record Candidate(BankStatementParser.BankRow row, long timeDifferenceMinutes, double vendorScore) {}
}
