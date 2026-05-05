package com.example.sportsevents.api;

import com.example.sportsevents.domain.ScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Self-contained mock of the external score API.
 *
 * <p>Used as the default upstream when running locally so that the application is fully runnable
 * without a separate service. In production, point {@code app.external.score-api.base-url} to the
 * real upstream and this controller is simply unused.
 */
@Slf4j
@RestController
@RequestMapping("/mock")
@Tag(name = "Mock Score API", description = "Local mock of the upstream score API for development.")
public class MockScoreController {

    private final ConcurrentHashMap<String, AtomicInteger> homeScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> awayScores = new ConcurrentHashMap<>();

    @Operation(summary = "Mock score endpoint, returns a slowly-incrementing fake score.")
    @GetMapping("/scores/{eventId}")
    public ScoreResponse getScore(@PathVariable String eventId) {
        AtomicInteger home = homeScores.computeIfAbsent(eventId, k -> new AtomicInteger(0));
        AtomicInteger away = awayScores.computeIfAbsent(eventId, k -> new AtomicInteger(0));
        if (Math.random() < 0.3) {
            home.incrementAndGet();
        } else if (Math.random() < 0.2) {
            away.incrementAndGet();
        }
        ScoreResponse response = new ScoreResponse(eventId, home.get() + ":" + away.get());
        log.debug("Mock score for eventId={} -> {}", eventId, response.currentScore());
        return response;
    }
}
