package com.example.sportsevents.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ScoreMetrics {

    private final AtomicInteger liveEventsCount = new AtomicInteger(0);

    private final Counter pollSuccessCounter;
    private final Counter pollFailureCounter;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    public ScoreMetrics(MeterRegistry registry) {
        Gauge.builder("live_events_count", liveEventsCount, AtomicInteger::get)
                .description("Number of events currently being polled")
                .register(registry);
        this.pollSuccessCounter = Counter.builder("score_poll_success_total")
                .description("Successful external score API calls")
                .register(registry);
        this.pollFailureCounter = Counter.builder("score_poll_failure_total")
                .description("Failed external score API calls")
                .register(registry);
        this.publishSuccessCounter = Counter.builder("score_publish_success_total")
                .description("Successful Kafka publishes")
                .register(registry);
        this.publishFailureCounter = Counter.builder("score_publish_failure_total")
                .description("Failed Kafka publishes")
                .register(registry);
    }

    public void incrementLiveEvents() {
        liveEventsCount.incrementAndGet();
    }

    public void decrementLiveEvents() {
        liveEventsCount.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void recordPollSuccess() {
        pollSuccessCounter.increment();
    }

    public void recordPollFailure() {
        pollFailureCounter.increment();
    }

    public void recordPublishSuccess() {
        publishSuccessCounter.increment();
    }

    public void recordPublishFailure() {
        publishFailureCounter.increment();
    }
}
