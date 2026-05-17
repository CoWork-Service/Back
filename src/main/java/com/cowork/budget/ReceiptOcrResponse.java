package com.cowork.budget;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record ReceiptOcrResponse(
        String date,
        String time,
        String dateTime,
        String vendor,
        Long amount,
        String paymentMethod,
        String category,
        String description,
        String businessNumber,
        String address,
        String phoneNumber,
        String cardCompany,
        String cardNumber,
        String approvalNumber,
        Double confidenceScore,
        List<OcrService.OcrField> fields
) {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static ReceiptOcrResponse of(OcrService.OcrResult result) {
        return new ReceiptOcrResponse(
                result.dateTime() != null ? result.dateTime().toLocalDate().toString() : null,
                result.dateTime() != null ? result.dateTime().toLocalTime().format(TIME_FORMATTER) : null,
                result.dateTime() != null ? result.dateTime().format(DATE_TIME_FORMATTER) : null,
                result.vendor(),
                result.amount(),
                result.paymentMethod(),
                result.category(),
                result.description(),
                result.businessNumber(),
                result.address(),
                result.phoneNumber(),
                result.cardCompany(),
                result.cardNumber(),
                result.approvalNumber(),
                result.confidenceScore(),
                result.fields()
        );
    }
}
