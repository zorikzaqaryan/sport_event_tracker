package com.example.sportsevents.service;

import com.example.sportsevents.client.ExternalScoreClient;
import com.example.sportsevents.domain.ScoreResponse;
import com.example.sportsevents.domain.ScoreUpdateMessage;
import com.example.sportsevents.messaging.ScorePublisher;
import com.example.sportsevents.metrics.ScoreMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScorePollingJobTest {

    private static final String EVENT_ID = "evt-1";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-05T10:00:00Z");

    @Mock
    private ExternalScoreClient externalScoreClient;
    @Mock
    private ScorePublisher scorePublisher;

    private ScoreMetrics metrics;
    private ScorePollingJob job;

    @BeforeEach
    void setUp() {
        metrics = new ScoreMetrics(new SimpleMeterRegistry());
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        job = new ScorePollingJob(EVENT_ID, externalScoreClient, scorePublisher, metrics, clock);
    }

    @Test
    void successfulFetchTransformsAndPublishes() {
        when(externalScoreClient.fetchScore(EVENT_ID)).thenReturn(new ScoreResponse(EVENT_ID, "1:0"));
        when(scorePublisher.publish(eq(EVENT_ID), any())).thenReturn(CompletableFuture.completedFuture(true));

        job.run();

        ArgumentCaptor<ScoreUpdateMessage> captor = ArgumentCaptor.forClass(ScoreUpdateMessage.class);
        verify(scorePublisher).publish(eq(EVENT_ID), captor.capture());
        ScoreUpdateMessage msg = captor.getValue();
        assertThat(msg.eventId()).isEqualTo(EVENT_ID);
        assertThat(msg.currentScore()).isEqualTo("1:0");
        assertThat(msg.publishedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void externalApiFailureIsHandledAndDoesNotThrow() {
        when(externalScoreClient.fetchScore(EVENT_ID)).thenThrow(new RestClientException("upstream down"));

        job.run();

        verify(scorePublisher, never()).publish(any(), any());
    }

    @Test
    void publisherFailureIsHandledAndDoesNotCrash() {
        when(externalScoreClient.fetchScore(EVENT_ID)).thenReturn(new ScoreResponse(EVENT_ID, "0:0"));
        when(scorePublisher.publish(eq(EVENT_ID), any())).thenReturn(CompletableFuture.completedFuture(false));

        job.run();

        verify(scorePublisher, times(1)).publish(eq(EVENT_ID), any());
    }

    @Test
    void emptyResponsePayloadIsHandled() {
        when(externalScoreClient.fetchScore(EVENT_ID)).thenReturn(new ScoreResponse(EVENT_ID, null));

        job.run();

        verify(scorePublisher, never()).publish(any(), any());
    }

    @Test
    void runtimeExceptionInPublisherDoesNotPropagate() {
        when(externalScoreClient.fetchScore(EVENT_ID)).thenReturn(new ScoreResponse(EVENT_ID, "2:1"));
        // Simulates a synchronous throw before the future is even returned (misbehaving impl).
        when(scorePublisher.publish(eq(EVENT_ID), any())).thenThrow(new RuntimeException("boom"));

        job.run();

        verify(scorePublisher).publish(eq(EVENT_ID), any());
    }
}
