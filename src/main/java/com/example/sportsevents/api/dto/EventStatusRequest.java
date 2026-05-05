package com.example.sportsevents.api.dto;

import com.example.sportsevents.domain.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(
        description = "Request body for updating an event's live status.",
        example = """
                { "eventId": "1234", "status": "LIVE" }"""
)
public record EventStatusRequest(
        @Schema(
                description = "Unique event identifier. Accepts a JSON string or number; numbers "
                        + "are coerced to strings (e.g. 1234 -> \"1234\").",
                example = "1234"
        )
        @NotBlank(message = "eventId must not be blank")
        String eventId,

        @Schema(
                description = "Live status of the event. Accepts: boolean (true=live, false=not live), "
                        + "or string \"live\"/\"not live\" (case-insensitive).",
                example = "LIVE",
                allowableValues = {"live", "not live", "true", "false"}
        )
        @NotNull(message = "status must not be null")
        EventStatus status
) {
}
