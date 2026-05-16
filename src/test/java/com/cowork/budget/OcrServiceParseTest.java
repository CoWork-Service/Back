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
}
