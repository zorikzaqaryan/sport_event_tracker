package com.example.sportsevents.api;

import com.example.sportsevents.api.dto.EventStatusRequest;
import com.example.sportsevents.api.dto.EventStatusResponse;
import com.example.sportsevents.api.dto.ErrorResponse;
import com.example.sportsevents.service.EventStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/events")
@Tag(name = "Event Status", description = "Receive live/not-live status updates and start/stop polling.")
public class EventStatusController {

    private final EventStatusService eventStatusService;

    public EventStatusController(EventStatusService eventStatusService) {
        this.eventStatusService = eventStatusService;
    }

    @Operation(
            summary = "Update the live status of an event",
            description = "Idempotent. LIVE starts polling for the event; NOT_LIVE stops it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status accepted",
                    content = @Content(schema = @Schema(implementation = EventStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/status")
    public ResponseEntity<EventStatusResponse> updateStatus(@Valid @RequestBody EventStatusRequest request) {
        log.debug("Received status update request: {}", request);
        EventStatusResponse response = eventStatusService.updateStatus(request.eventId(), request.status());
        return ResponseEntity.ok(response);
    }
}
