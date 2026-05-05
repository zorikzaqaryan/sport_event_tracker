package com.example.sportsevents.service;

import com.example.sportsevents.client.ExternalScoreClient;
import com.example.sportsevents.domain.ScoreResponse;
import com.example.sportsevents.domain.ScoreUpdateMessage;
import com.example.sportsevents.messaging.ScorePublisher;
import com.example.sportsevents.metrics.ScoreMetrics;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Objects;

/**
 * One-shot Runnable that fetches the current score for a single event and publishes it.
 * from a {@link java.util.concurrent.ScheduledExecutorService} task silently cancels all future
 * executions, which would silently break polling for that event.
 */
@Slf4j
public class ScorePollingJob implements Runnable {

    private final String eventId;
    private final ExternalScoreClient externalScoreClient;
    private final ScorePublisher scorePublisher;
    private final ScoreMetrics metrics;
    private final Clock clock;

    public ScorePollingJob(String eventId,
                           ExternalScoreClient externalScoreClient,
                           ScorePublisher scorePublisher,
                           ScoreMetrics metrics,
                           Clock clock) {
        this.eventId = eventId;
        this.externalScoreClient = externalScoreClient;
        this.scorePublisher = scorePublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public void run() {
        ScoreResponse response;
        try {
            response = externalScoreClient.fetchScore(eventId);
            metrics.recordPollSuccess();
        } catch (Exception ex) {
            metrics.recordPollFailure();
            log.warn("External score API call failed for eventId={}: {}", eventId, ex.getMessage());
            return;
        }

        if (response == null || response.currentScore() == null) {
            metrics.recordPollFailure();
            log.warn("External score API returned empty payload for eventId={}", eventId);
            return;
        }

        try {
            String resolvedEventId = Objects.requireNonNullElse(response.eventId(), eventId);
            ScoreUpdateMessage message = new ScoreUpdateMessage(
                    resolvedEventId,
                    response.currentScore(),
                    clock.instant()
            );
            // Fire-and-forget: success/failure logging and metrics are handled inside the publisher.
            scorePublisher.publish(resolvedEventId, message);
        } catch (Exception ex) {
            // Guards against a synchronous throw from a misbehaving publisher implementation.
            log.error("Unexpected synchronous failure from publisher for eventId={}", eventId, ex);
        }
    }
}
