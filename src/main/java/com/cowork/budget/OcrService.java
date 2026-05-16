package com.cowork.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class OcrService {

    @Value("${clova.ocr.secret-key}")
    private String secretKey;

    @Value("${clova.ocr.invoke-url}")
    private String invokeUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record OcrResult(LocalDateTime dateTime, Long amount) {}

    public OcrResult parseReceipt(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

        String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                "version", "V2",
                "requestId", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis(),
                "images", java.util.List.of(java.util.Map.of(
                        "format", mimeType.replace("image/", ""),
                        "name", "receipt",
                        "data", base64
                ))
        ));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(invokeUrl))
                .header("Content-Type", "application/json")
                .header("X-OCR-SECRET", secretKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("클로바 OCR 호출 실패: " + response.statusCode());
        }

        return parseOcrResponse(response.body());
    }

    private OcrResult parseOcrResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode images = root.path("images");

        if (images.isEmpty()) {
            return new OcrResult(null, null);
        }

        JsonNode receiptResult = images.get(0).path("receipt").path("result");

        // 날짜+시간 파싱 (분 단위까지)
        LocalDateTime dateTime = null;
        try {
            String dateStr = receiptResult.path("paymentInfo").path("date").path("text").asText();
            String timeStr = receiptResult.path("paymentInfo").path("time").path("text").asText();
            dateTime = parseDateTime(dateStr, timeStr);
        } catch (Exception e) {
            log.debug("날짜/시간 파싱 실패: {}", e.getMessage());
        }

        // 금액 파싱
        Long amount = null;
        try {
            String amountStr = receiptResult.path("totalPrice").path("price").path("text").asText();
            amount = parseAmount(amountStr);
        } catch (Exception e) {
            log.debug("금액 파싱 실패: {}", e.getMessage());
        }

        return new OcrResult(dateTime, amount);
    }

    /**
     * Clova OCR의 date 텍스트("2024-05-16")와 time 텍스트("14:30:00")를 조합해
     * LocalDateTime(분 단위)으로 변환한다.
     */
    LocalDateTime parseDateTime(String dateStr, String timeStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String dateDigits = dateStr.replaceAll("[^0-9]", "");
        if (dateDigits.length() < 8) return null;

        LocalDate date;
        try {
            int year  = Integer.parseInt(dateDigits.substring(0, 4));
            int month = Integer.parseInt(dateDigits.substring(4, 6));
            int day   = Integer.parseInt(dateDigits.substring(6, 8));
            date = LocalDate.of(year, month, day);
        } catch (Exception e) {
            log.debug("날짜 변환 실패: {}", e.getMessage());
            return null;
        }

        if (timeStr == null || timeStr.isBlank()) {
            return date.atStartOfDay();
        }

        String timeDigits = timeStr.replaceAll("[^0-9]", "");
        if (timeDigits.length() >= 4) {
            try {
                int hour   = Integer.parseInt(timeDigits.substring(0, 2));
                int minute = Integer.parseInt(timeDigits.substring(2, 4));
                return date.atTime(hour, minute);
            } catch (Exception e) {
                log.debug("시간 변환 실패: {}", e.getMessage());
            }
        }

        return date.atStartOfDay();
    }

    private Long parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return null;
        String digits = amountStr.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return null;
        return Long.parseLong(digits);
    }
}
