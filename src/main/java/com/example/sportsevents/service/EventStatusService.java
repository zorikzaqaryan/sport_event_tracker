package com.example.sportsevents.service;

import com.example.sportsevents.api.dto.EventStatusResponse;
import com.example.sportsevents.domain.EventStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EventStatusService {

    private final LiveEventScheduler scheduler;
    private final Map<String, EventStatus> eventStates = new ConcurrentHashMap<>();

    public EventStatusService(LiveEventScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public EventStatusResponse updateStatus(String eventId, EventStatus newStatus) {
        EventStatusResult result = switch (newStatus) {
            case LIVE -> handleLive(eventId);
            case NOT_LIVE -> handleNotLive(eventId);
        };
        return new EventStatusResponse(eventId, newStatus, result.message());
    }

    private EventStatusResult handleLive(String eventId) {
        EventStatus previous = eventStates.putIfAbsent(eventId, EventStatus.LIVE);

        if (previous == EventStatus.LIVE) {
            log.info("Event status update eventId={} new=LIVE result={}", eventId, EventStatusResult.ALREADY_LIVE);
            return EventStatusResult.ALREADY_LIVE;
        }

        boolean started = scheduler.startPolling(eventId);
        EventStatusResult result = started ? EventStatusResult.STARTED : EventStatusResult.ALREADY_LIVE;
        log.info("Event status update eventId={} previous={} new=LIVE result={}", eventId, previous, result);
        return result;
    }

    private EventStatusResult handleNotLive(String eventId) {
        EventStatus previous = eventStates.remove(eventId);

        if (previous == null) {
            log.info("Event status update eventId={} new=NOT_LIVE result={}", eventId, EventStatusResult.NOT_ACTIVE);
            return EventStatusResult.NOT_ACTIVE;
        }

        boolean stopped = scheduler.stopPolling(eventId);
        EventStatusResult result = stopped ? EventStatusResult.STOPPED : EventStatusResult.NOT_ACTIVE;
        log.info("Event status update eventId={} previous={} new=NOT_LIVE result={}", eventId, previous, result);
        return result;
    }

    public EventStatus getStatus(String eventId) {
        return eventStates.getOrDefault(eventId, EventStatus.NOT_LIVE);
    }

    private enum EventStatusResult {
        STARTED("Polling started for event"),
        ALREADY_LIVE("Polling already active for event"),
        STOPPED("Polling stopped for event"),
        NOT_ACTIVE("Event was not active");

        private final String message;

        EventStatusResult(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }
}
