package com.example.sportsevents.service;

import com.example.sportsevents.api.dto.EventStatusResponse;
import com.example.sportsevents.domain.EventStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventStatusServiceTest {

    @Mock
    private LiveEventScheduler scheduler;

    @InjectMocks
    private EventStatusService service;

    @Test
    void liveStartsScheduler() {
        when(scheduler.startPolling("e1")).thenReturn(true);

        EventStatusResponse response = service.updateStatus("e1", EventStatus.LIVE);

        verify(scheduler).startPolling("e1");
        assertThat(response.status()).isEqualTo(EventStatus.LIVE);
        assertThat(response.message()).contains("started");
        assertThat(service.getStatus("e1")).isEqualTo(EventStatus.LIVE);
    }

    @Test
    void duplicateLiveDoesNotStartDuplicateScheduler() {
        when(scheduler.startPolling("e1")).thenReturn(true);

        service.updateStatus("e1", EventStatus.LIVE);
        EventStatusResponse second = service.updateStatus("e1", EventStatus.LIVE);

        // Second call is short-circuited by putIfAbsent: scheduler is never contacted again.
        verify(scheduler, times(1)).startPolling("e1");
        assertThat(second.message()).contains("already active");
    }

    @Test
    void notLiveStopsScheduler() {
        when(scheduler.startPolling("e1")).thenReturn(true);
        when(scheduler.stopPolling("e1")).thenReturn(true);

        service.updateStatus("e1", EventStatus.LIVE);
        EventStatusResponse response = service.updateStatus("e1", EventStatus.NOT_LIVE);

        verify(scheduler).stopPolling("e1");
        assertThat(response.status()).isEqualTo(EventStatus.NOT_LIVE);
        assertThat(response.message()).contains("stopped");
        assertThat(service.getStatus("e1")).isEqualTo(EventStatus.NOT_LIVE);
    }

    @Test
    void notLiveForUnknownEventIsIdempotent() {
        EventStatusResponse response = service.updateStatus("missing", EventStatus.NOT_LIVE);

        // Short-circuited by remove() returning null: scheduler is never contacted.
        verify(scheduler, never()).stopPolling("missing");
        verify(scheduler, never()).startPolling("missing");
        assertThat(response.message()).contains("not active");
    }
}
