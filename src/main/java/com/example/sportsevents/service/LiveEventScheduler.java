package com.example.sportsevents.service;

import com.example.sportsevents.client.ExternalScoreClient;
import com.example.sportsevents.config.AppProperties;
import com.example.sportsevents.messaging.ScorePublisher;
import com.example.sportsevents.metrics.ScoreMetrics;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages dynamic per-event polling schedules.
 *
 * <p>Why a TaskScheduler + map of ScheduledFutures instead of {@code @Scheduled}:
 * events come and go at runtime, and we need O(1) start/cancel per event without
 * restarting the application. {@code @Scheduled} only supports a fixed,
 * application-startup-time schedule.
 *
 * <p>Thread-safety: the {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}
 * call ensures that for any given eventId, start and stop operations are serialized,
 * which prevents duplicate jobs and the classic check-then-act race condition.
 */
@Slf4j
@Component
public class LiveEventScheduler {

    private final TaskScheduler taskScheduler;
    private final ExternalScoreClient externalScoreClient;
    private final ScorePublisher scorePublisher;
    private final ScoreMetrics metrics;
    private final Clock clock;
    private final Duration pollingInterval;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public LiveEventScheduler(@Qualifier("liveEventTaskScheduler") TaskScheduler taskScheduler,
                              ExternalScoreClient externalScoreClient,
                              ScorePublisher scorePublisher,
                              ScoreMetrics metrics,
                              Clock clock,
                              AppProperties properties) {
        this.taskScheduler = taskScheduler;
        this.externalScoreClient = externalScoreClient;
        this.scorePublisher = scorePublisher;
        this.metrics = metrics;
        this.clock = clock;
        this.pollingInterval = properties.polling().interval();
    }

    /**
     * Idempotent. Returns {@code true} if a new polling task was started, {@code false} if one
     * was already running for this eventId.
     */
    public boolean startPolling(String eventId) {
        boolean[] started = {false};
        scheduledTasks.compute(eventId, (id, existing) -> {
            if (existing != null && !existing.isCancelled() && !existing.isDone()) {
                return existing;
            }
            Runnable job = new ScorePollingJob(id, externalScoreClient, scorePublisher, metrics, clock);
            Instant firstRun = clock.instant().plus(pollingInterval);
            ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(job, firstRun, pollingInterval);
            metrics.incrementLiveEvents();
            started[0] = true;
            log.info("Started polling for eventId={} interval={}", id, pollingInterval);
            return future;
        });
        return started[0];
    }

    /**
     * Idempotent. Returns {@code true} if a polling task was cancelled, {@code false} if no task
     * was running for this eventId.
     */
    public boolean stopPolling(String eventId) {
        boolean[] stopped = {false};
        scheduledTasks.compute(eventId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            existing.cancel(false);
            metrics.decrementLiveEvents();
            stopped[0] = true;
            log.info("Stopped polling for eventId={}", id);
            return null;
        });
        return stopped[0];
    }

    @PreDestroy
    void cancelAll() {
        log.info("Cancelling {} active polling task(s) on shutdown", scheduledTasks.size());
        scheduledTasks.values().forEach(future -> future.cancel(false));
        scheduledTasks.clear();
    }
}
