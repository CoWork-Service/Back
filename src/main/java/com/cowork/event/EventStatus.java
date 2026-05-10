package com.cowork.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum EventStatus {
    PLANNING,
    ONGOING,
    DONE,
    CANCELLED;

    @JsonCreator
    public static EventStatus from(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "planning" -> PLANNING;
            case "ongoing" -> ONGOING;
            case "done" -> DONE;
            case "cancelled", "canceled" -> CANCELLED;
            default -> EventStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        };
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
