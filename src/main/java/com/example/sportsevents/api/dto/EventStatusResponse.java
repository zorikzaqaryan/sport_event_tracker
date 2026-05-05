package com.example.sportsevents.api.dto;

import com.example.sportsevents.domain.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response describing the outcome of an event status update.")
public record EventStatusResponse(
        String eventId,
        EventStatus status,
        String message
) {
}
