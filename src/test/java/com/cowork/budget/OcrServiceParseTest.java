package com.cowork.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OcrServiceParseTest {

    private final OcrService service = new OcrService();

    @Test
    @DisplayName("날짜+시간 정상 파싱")
    void parseDateAndTime() {
        LocalDateTime result = service.parseDateTime("2025-05-16", "14:30:00");
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 16, 14, 30));
    }

    @Test
    @DisplayName("날짜만 있으면 00:00으로 반환")
    void parseDateOnly() {
        LocalDateTime result = service.parseDateTime("2025-05-16", "");
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 16, 0, 0));
    }

    @Test
    @DisplayName("날짜 구분자 없는 형식 파싱 (yyyyMMdd)")
    void parseDateNoSeparator() {
        LocalDateTime result = service.parseDateTime("20250516", "1430");
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 16, 14, 30));
    }

    @Test
    @DisplayName("슬래시 구분자 날짜 파싱")
    void parseDateSlash() {
        LocalDateTime result = service.parseDateTime("2025/05/16", "09:05:00");
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 16, 9, 5));
    }

    @Test
    @DisplayName("날짜가 null이면 null 반환")
    void nullDate() {
        assertThat(service.parseDateTime(null, "14:30")).isNull();
    }

    @Test
    @DisplayName("날짜가 공백이면 null 반환")
    void blankDate() {
        assertThat(service.parseDateTime("  ", "14:30")).isNull();
    }

    @Test
    @DisplayName("시간이 null이면 00:00으로 반환")
    void nullTime() {
        LocalDateTime result = service.parseDateTime("2025-05-16", null);
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 16, 0, 0));
    }

    @Test
    @DisplayName("오후 표기 시간을 24시간제로 파싱")
    void parseKoreanPmTime() {
        LocalDateTime result = service.parseDateTime("2025.5.16", "오후 2시 05분");
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 16, 14, 5));
    }

    @Test
    @DisplayName("Clova 영수증 응답에서 지출 폼 필드를 추출")
    void parseReceiptResponseFields() throws Exception {
        String response = """
                {
                  "images": [
                    {
                      "receipt": {
                        "result": {
                          "storeInfo": {
                            "name": { "text": "스타벅스 숭실대점", "confidenceScore": 0.98 },
                            "bizNum": { "text": "123-45-67890", "confidenceScore": 0.97 },
                            "addresses": [{ "text": "서울 동작구 상도로 369", "confidenceScore": 0.91 }],
                            "tel": [{ "text": "02-1234-5678", "confidenceScore": 0.93 }]
                          },
                          "paymentInfo": {
                            "date": { "text": "2025.05.16", "confidenceScore": 0.99 },
                            "time": { "text": "오후 2:30", "confidenceScore": 0.96 },
                            "cardInfo": {
                              "company": { "text": "신한카드", "confidenceScore": 0.9 },
                              "number": { "text": "1234-**-****-5678", "confidenceScore": 0.9 }
                            },
                            "confirmNum": { "text": "12345678", "confidenceScore": 0.88 }
                          },
                          "totalPrice": {
                            "price": { "text": "38,000", "confidenceScore": 0.99 }
                          }
                        }
                      }
                    }
                  ]
                }
                """;

        OcrService.OcrResult result = service.parseOcrResponse(response);

        assertThat(result.dateTime()).isEqualTo(LocalDateTime.of(2025, 5, 16, 14, 30));
        assertThat(result.amount()).isEqualTo(38000L);
        assertThat(result.vendor()).isEqualTo("스타벅스 숭실대점");
        assertThat(result.paymentMethod()).isEqualTo("개인카드");
        assertThat(result.category()).isEqualTo("식대");
        assertThat(result.businessNumber()).isEqualTo("123-45-67890");
        assertThat(result.confidenceScore()).isNotNull();
    }

    @Test
    @DisplayName("총액 필드가 없으면 키워드 주변 금액으로 보정")
    void parseAmountFromRawKeyword() throws Exception {
        String response = """
                {
                  "images": [
                    {
                      "receipt": {
                        "result": {
                          "storeInfo": { "name": { "text": "다이소" } },
                          "paymentInfo": { "date": { "text": "2025-05-16" }, "time": { "text": "14:30" } },
                          "totalPrice": {},
                          "subResults": [
                            { "items": [{ "name": { "text": "노트" }, "price": { "text": "1,000" } }] }
                          ],
                          "summary": { "text": "합계 8,900원" }
                        }
                      }
                    }
                  ]
                }
                """;

        OcrService.OcrResult result = service.parseOcrResponse(response);

        assertThat(result.amount()).isEqualTo(8900L);
        assertThat(result.category()).isEqualTo("소모품");
    }
}
