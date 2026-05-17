package com.cowork.budget

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 영수증 OCR 결과(결제 시각 + 금액)와 통장 거래 내역을 매칭한다.
 *
 * 매칭 조건:
 *   1. 금액 일치
 *   2. 결제 시각 차이 ±TOLERANCE_MINUTES(1분) 이내 (초 단위 이하는 무시)
 */
@Service
public class ReceiptMatchService {

    static final long TOLERANCE_MINUTES = 1;

    /**
     * @param receiptTime   영수증 OCR에서 추출한 결제 시각
     * @param receiptAmount 영수증 OCR에서 추출한 결제 금액
     * @param bankRows      통장 거래 내역 목록
     * @return 조건을 만족하는 첫 번째 거래 내역 (없으면 empty)
     */
    public Optional<BankStatementParser.BankRow> match(
            LocalDateTime receiptTime,
            Long receiptAmount,
            List<BankStatementParser.BankRow> bankRows) {

        if (receiptTime == null || receiptAmount == null || bankRows == null) {
            return Optional.empty();
        }

        // 초 이하를 버리고 분 단위로 비교
        LocalDateTime receiptMinute = receiptTime.truncatedTo(ChronoUnit.MINUTES);

        return bankRows.stream()
                .filter(row -> row.dateTime() != null)
                .filter(row -> Objects.equals(row.amount(), receiptAmount))
                .filter(row -> {
                    LocalDateTime bankMinute = row.dateTime().truncatedTo(ChronoUnit.MINUTES);
                    return Math.abs(Duration.between(receiptMinute, bankMinute).toMinutes()) <= TOLERANCE_MINUTES;
                })
                .findFirst();
    }
}
