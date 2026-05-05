package com.example.sportsevents.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum EventStatus {

    LIVE("live"),
    NOT_LIVE("not live");

    private final String jsonValue;

    EventStatus(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static EventStatus fromJson(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? LIVE : NOT_LIVE;
        }

        if (value instanceof String text) {
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "live" -> LIVE;
                case "not live" -> NOT_LIVE;
                default -> throw new IllegalArgumentException(
                        "Invalid status value. Accepted values: true, false, \"live\", \"not live\""
                );
            };
        }

        throw new IllegalArgumentException(
                "Invalid status type. Accepted values: true, false, \"live\", \"not live\""
        );
    }

    @JsonValue
    public String toJson() {
        return jsonValue;
    }
}