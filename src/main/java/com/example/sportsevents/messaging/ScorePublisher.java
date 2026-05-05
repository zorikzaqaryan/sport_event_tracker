package com.example.sportsevents.messaging;

import com.example.sportsevents.domain.ScoreUpdateMessage;

import java.util.concurrent.CompletableFuture;

public interface ScorePublisher {

    /**
     * Publishes a score update for the given eventId asynchronously.
     * <p>
     * The returned future resolves to {@code true} on success and {@code false} on failure.
     * Implementations must never complete the future exceptionally; all errors are captured
     * as {@code false} and logged internally.
     */
    CompletableFuture<Boolean> publish(String eventId, ScoreUpdateMessage message);
}
