package com.cowork.budget;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OcrService {

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}|19\\d{2})\\D{0,3}(\\d{1,2})\\D{0,3}(\\d{1,2})");
    private static final Pattern SHORT_DATE_PATTERN = Pattern.compile("(?<!\\d)(\\d{2})\\D{0,3}(\\d{1,2})\\D{0,3}(\\d{1,2})(?!\\d)");
    private static final Pattern TIME_PATTERN = Pattern.compile("(오전|오후|AM|PM|am|pm)?\\s*(\\d{1,2})\\s*(?:시|:|：|\\.)\\s*(\\d{1,2})");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,3}(?:,\\d{3})+|\\d{3,8})(?!\\d)");
    private static final List<String> TOTAL_AMOUNT_KEYWORDS = List.of(
            "합계", "총액", "총금액", "결제금액", "받을금액", "승인금액", "판매금액", "결제 금액", "총 결제", "total"
    );

    @Value("${clova.ocr.secret-key:}")
    private String secretKey;

    @Value("${clova.ocr.invoke-url:}")
    private String invokeUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record OcrField(String name, String text, Double confidenceScore) {}

    public record OcrResult(
            LocalDateTime dateTime,
            Long amount,
            String vendor,
            String businessNumber,
            String address,
            String phoneNumber,
            String paymentMethod,
            String cardCompany,
            String cardNumber,
            String approvalNumber,
            String category,
            String description,
            Double confidenceScore,
            List<OcrField> fields
    ) {}

    public OcrResult parseReceipt(MultipartFile file) throws Exception {
        if (!StringUtils.hasText(secretKey) || !StringUtils.hasText(invokeUrl)) {
            throw new BusinessException(ErrorCode.OCR_CONFIG_MISSING);
        }

        byte[] bytes = file.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String format = resolveImageFormat(file);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "version", "V2",
                "requestId", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis(),
                "images", List.of(Map.of(
                        "format", format,
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
            log.warn("Clova OCR failed: status={}, body={}", response.statusCode(), response.body());
            throw new BusinessException(ErrorCode.OCR_FAILED);
        }

        return parseOcrResponse(response.body());
    }

    OcrResult parseOcrResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode images = root.path("images");

        if (images.isEmpty()) {
            return emptyResult();
        }

        JsonNode image = images.get(0);
        JsonNode receiptResult = image.path("receipt").path("result");
        if (receiptResult.isMissingNode() || receiptResult.isNull()) {
            return emptyResult();
        }

        String rawText = collectText(receiptResult);
        JsonNode storeInfo = receiptResult.path("storeInfo");
        JsonNode paymentInfo = receiptResult.path("paymentInfo");
        JsonNode totalPrice = receiptResult.path("totalPrice");

        String storeName = textAt(storeInfo, "name");
        String storeSubName = textAt(storeInfo, "subName");
        String vendor = firstText(joinStoreName(storeName, storeSubName), storeName, storeSubName, textFromRawStoreName(rawText));
        String businessNumber = firstText(textAt(storeInfo, "bizNum"), match(rawText, "(?:사업자|사업자번호|사업자등록번호)\\D*(\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{5})"));
        String address = firstText(textAt(storeInfo, "addresses", "0"), textAt(storeInfo, "addresses"), textFromRawAddress(rawText));
        String phoneNumber = firstText(textAt(storeInfo, "tel", "0"), textAt(storeInfo, "tel"), match(rawText, "(0\\d{1,2}[-\\s]?\\d{3,4}[-\\s]?\\d{4})"));

        String dateText = firstText(textAt(paymentInfo, "date"), match(rawText, "(20\\d{2}[.\\-/년 ]+\\d{1,2}[.\\-/월 ]+\\d{1,2})"));
        String timeText = firstText(textAt(paymentInfo, "time"), match(rawText, "((?:오전|오후|AM|PM|am|pm)?\\s*\\d{1,2}\\s*(?:시|:|：|\\.)\\s*\\d{1,2})"));
        LocalDateTime dateTime = parseDateTime(dateText, timeText);
        if (dateTime == null) {
            dateTime = parseDateTime(rawText, rawText);
        }

        Long amount = firstAmount(
                parseAmount(textAt(totalPrice, "price")),
                maxAmount(totalPrice),
                amountFromKeyword(rawText),
                maxLikelyAmount(rawText)
        );

        String cardCompany = textAt(paymentInfo, "cardInfo", "company");
        String cardNumber = textAt(paymentInfo, "cardInfo", "number");
        String approvalNumber = textAt(paymentInfo, "confirmNum");
        String paymentMethod = inferPaymentMethod(rawText, cardCompany, cardNumber, approvalNumber);
        String category = inferCategory(vendor, rawText);
        String description = inferDescription(vendor, category);

        List<OcrField> fields = new ArrayList<>();
        addField(fields, "vendor", vendor, confidenceAt(storeInfo.path("name")));
        addField(fields, "businessNumber", businessNumber, confidenceAt(storeInfo.path("bizNum")));
        addField(fields, "address", address, confidenceAt(firstArrayItem(storeInfo.path("addresses"))));
        addField(fields, "phoneNumber", phoneNumber, confidenceAt(firstArrayItem(storeInfo.path("tel"))));
        addField(fields, "date", dateTime != null ? dateTime.toLocalDate().toString() : null, confidenceAt(paymentInfo.path("date")));
        addField(fields, "time", dateTime != null ? "%02d:%02d".formatted(dateTime.getHour(), dateTime.getMinute()) : null, confidenceAt(paymentInfo.path("time")));
        addField(fields, "amount", amount != null ? String.valueOf(amount) : null, confidenceAt(totalPrice.path("price")));
        addField(fields, "paymentMethod", paymentMethod, confidenceAt(paymentInfo.path("cardInfo")));
        addField(fields, "cardCompany", cardCompany, confidenceAt(paymentInfo.path("cardInfo").path("company")));
        addField(fields, "cardNumber", cardNumber, confidenceAt(paymentInfo.path("cardInfo").path("number")));
        addField(fields, "approvalNumber", approvalNumber, confidenceAt(paymentInfo.path("confirmNum")));
        addField(fields, "category", category, null);
        addField(fields, "description", description, null);

        return new OcrResult(
                dateTime,
                amount,
                vendor,
                businessNumber,
                address,
                phoneNumber,
                paymentMethod,
                cardCompany,
                cardNumber,
                approvalNumber,
                category,
                description,
                averageConfidence(fields),
                List.copyOf(fields)
        );
    }

    /**
     * Clova OCR의 date 텍스트("2024-05-16")와 time 텍스트("14:30:00")를 조합해
     * LocalDateTime(분 단위)으로 변환한다.
     */
    LocalDateTime parseDateTime(String dateStr, String timeStr) {
        LocalDate date = parseDate(firstText(dateStr, timeStr));
        if (date == null && StringUtils.hasText(timeStr)) {
            date = parseDate(timeStr);
        }
        if (date == null) return null;

        int[] time = parseTime(firstText(timeStr, dateStr));
        if (time == null && StringUtils.hasText(dateStr)) {
            time = parseTime(dateStr);
        }
        if (time == null) {
            return date.atStartOfDay();
        }
        return date.atTime(time[0], time[1]);
    }

    Long parseAmount(String amountStr) {
        if (!StringUtils.hasText(amountStr)) return null;
        String digits = amountStr.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return null;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private OcrResult emptyResult() {
        return new OcrResult(null, null, null, null, null, null, null, null, null, null, "기타", "영수증 지출", null, List.of());
    }

    private String resolveImageFormat(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        String value = firstText(contentType, filename);
        if (value == null) return "jpg";

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("png") || lower.endsWith(".png")) return "png";
        if (lower.contains("jpeg") || lower.contains("jpg") || lower.endsWith(".jpeg") || lower.endsWith(".jpg")) return "jpg";
        if (lower.contains("pdf") || lower.endsWith(".pdf")) return "pdf";
        return "jpg";
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;

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

        Matcher shortMatcher = SHORT_DATE_PATTERN.matcher(value);
        if (shortMatcher.find()) {
            return toDate("20" + shortMatcher.group(1), shortMatcher.group(2), shortMatcher.group(3));
        }
        return null;
    }

    private LocalDate toDate(String yearText, String monthText, String dayText) {
        try {
            int year = Integer.parseInt(yearText);
            int month = Integer.parseInt(monthText);
            int day = Integer.parseInt(dayText);
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            log.debug("날짜 변환 실패: {}", e.getMessage());
            return null;
        }
    }

    private int[] parseTime(String value) {
        if (!StringUtils.hasText(value)) return null;

        Matcher matcher = TIME_PATTERN.matcher(value);
        String meridiem = null;
        int hour;
        int minute;

        if (matcher.find()) {
            meridiem = matcher.group(1);
            hour = Integer.parseInt(matcher.group(2));
            minute = Integer.parseInt(matcher.group(3));
        } else {
            String digits = value.replaceAll("[^0-9]", "");
            if (digits.length() != 4 && digits.length() != 6) return null;
            hour = Integer.parseInt(digits.substring(0, 2));
            minute = Integer.parseInt(digits.substring(2, 4));
        }

        if (meridiem != null) {
            String lower = meridiem.toLowerCase(Locale.ROOT);
            boolean pm = lower.equals("pm") || meridiem.equals("오후");
            boolean am = lower.equals("am") || meridiem.equals("오전");
            if (pm && hour < 12) hour += 12;
            if (am && hour == 12) hour = 0;
        }

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
        return new int[]{hour, minute};
    }

    private String textAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            if (current == null || current.isMissingNode() || current.isNull()) return null;
            if (part.matches("\\d+") && current.isArray()) {
                int index = Integer.parseInt(part);
                current = index < current.size() ? current.get(index) : null;
            } else {
                current = current.path(part);
            }
        }
        return extractText(current);
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return clean(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = extractText(item);
                if (StringUtils.hasText(text)) return text;
            }
            return null;
        }
        String formatted = extractText(node.path("formatted").path("value"));
        if (StringUtils.hasText(formatted)) return formatted;
        for (String key : List.of("text", "inferText", "value", "keyText")) {
            String text = extractText(node.path(key));
            if (StringUtils.hasText(text)) return text;
        }
        return null;
    }

    private JsonNode firstArrayItem(JsonNode node) {
        if (node != null && node.isArray() && !node.isEmpty()) return node.get(0);
        return node;
    }

    private Double confidenceAt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String key : List.of("confidenceScore", "confidence", "inferConfidence", "score")) {
            JsonNode value = node.path(key);
            if (value.isNumber()) return value.asDouble();
            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (node.isArray() && !node.isEmpty()) return confidenceAt(node.get(0));
        return null;
    }

    private String collectText(JsonNode node) {
        List<String> values = new ArrayList<>();
        collectText(node, values);
        return String.join("\n", values);
    }

    private void collectText(JsonNode node, List<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isObject()) {
            for (String key : List.of("text", "inferText", "value", "keyText")) {
                String text = extractText(node.path(key));
                if (StringUtils.hasText(text)) values.add(text);
            }
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), values));
        } else if (node.isArray()) {
            node.forEach(child -> collectText(child, values));
        }
    }

    private Long maxAmount(JsonNode node) {
        List<Long> amounts = new ArrayList<>();
        collectAmounts(node, amounts);
        return amounts.stream()
                .filter(amount -> amount >= 100 && amount <= 10_000_000)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private void collectAmounts(JsonNode node, List<Long> amounts) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        Long amount = parseAmount(extractText(node));
        if (amount != null) amounts.add(amount);
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectAmounts(entry.getValue(), amounts));
        } else if (node.isArray()) {
            node.forEach(child -> collectAmounts(child, amounts));
        }
    }

    private Long amountFromKeyword(String rawText) {
        if (!StringUtils.hasText(rawText)) return null;
        String[] lines = rawText.split("\\R+");
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean hasKeyword = TOTAL_AMOUNT_KEYWORDS.stream().anyMatch(keyword -> lower.contains(keyword.toLowerCase(Locale.ROOT)));
            if (!hasKeyword) continue;
            Long amount = lastAmountIn(line);
            if (amount != null) return amount;
        }
        return null;
    }

    private Long maxLikelyAmount(String rawText) {
        if (!StringUtils.hasText(rawText)) return null;
        Matcher matcher = AMOUNT_PATTERN.matcher(rawText);
        List<Long> amounts = new ArrayList<>();
        while (matcher.find()) {
            Long amount = parseAmount(matcher.group(1));
            if (amount != null && amount >= 100 && amount <= 10_000_000) {
                amounts.add(amount);
            }
        }
        return amounts.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private Long lastAmountIn(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        Long last = null;
        while (matcher.find()) {
            Long amount = parseAmount(matcher.group(1));
            if (amount != null && amount >= 100 && amount <= 10_000_000) {
                last = amount;
            }
        }
        return last;
    }

    private String joinStoreName(String name, String subName) {
        if (!StringUtils.hasText(name)) return clean(subName);
        if (!StringUtils.hasText(subName)) return clean(name);
        if (name.contains(subName) || subName.contains(name)) return clean(name);
        return clean(name + " " + subName);
    }

    private String textFromRawStoreName(String rawText) {
        return firstText(
                match(rawText, "(?:상호|가맹점명|매장명|점포명)\\s*[:：]?\\s*([^\\n\\r]{2,40})"),
                null
        );
    }

    private String textFromRawAddress(String rawText) {
        return match(rawText, "((?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)[^\\n\\r]{5,80})");
    }

    private String inferPaymentMethod(String rawText, String cardCompany, String cardNumber, String approvalNumber) {
        String source = clean(rawText);
        if (StringUtils.hasText(cardCompany) || StringUtils.hasText(cardNumber) || StringUtils.hasText(approvalNumber)
                || containsAny(source, "카드", "승인", "CARD", "card")) {
            return "개인카드";
        }
        if (containsAny(source, "계좌", "이체", "송금")) return "계좌이체";
        if (containsAny(source, "현금", "cash", "CASH")) return "현금";
        return null;
    }

    private String inferCategory(String vendor, String rawText) {
        String source = (clean(vendor) + " " + clean(rawText)).toLowerCase(Locale.ROOT);
        if (containsAny(source, "식당", "분식", "김밥", "치킨", "피자", "버거", "맥도날드", "롯데리아", "카페", "커피", "스타벅스", "이디야", "투썸", "파리바게뜨", "식대", "음식", "음료")) {
            return "식대";
        }
        if (containsAny(source, "인쇄", "출력", "복사", "제본", "프린트", "킨코스", "현수막", "포스터")) {
            return "인쇄비";
        }
        if (containsAny(source, "다이소", "문구", "오피스", "마트", "이마트", "홈플러스", "쿠팡", "소모품", "비품", "용품", "문구점")) {
            return "소모품";
        }
        if (containsAny(source, "대관", "행사", "이벤트", "무대", "음향", "렌탈", "공연")) {
            return "행사비";
        }
        return "기타";
    }

    private String inferDescription(String vendor, String category) {
        if (StringUtils.hasText(vendor)) return vendor + " 영수증";
        if (StringUtils.hasText(category) && !"기타".equals(category)) return category + " 영수증";
        return "영수증 지출";
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value)) return false;
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    private Long firstAmount(Long... amounts) {
        for (Long amount : amounts) {
            if (amount != null) return amount;
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            String clean = clean(value);
            if (StringUtils.hasText(clean)) return clean;
        }
        return null;
    }

    private String match(String text, String regex) {
        if (!StringUtils.hasText(text)) return null;
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? clean(matcher.group(1)) : null;
    }

    private void addField(List<OcrField> fields, String name, String text, Double confidenceScore) {
        if (StringUtils.hasText(text)) {
            fields.add(new OcrField(name, text, confidenceScore));
        }
    }

    private Double averageConfidence(List<OcrField> fields) {
        return fields.stream()
                .map(OcrField::confidenceScore)
                .filter(value -> value != null && value > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
