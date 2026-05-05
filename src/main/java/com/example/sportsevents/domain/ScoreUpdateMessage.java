package com.example.sportsevents.domain;

import java.time.Instant;

public record ScoreUpdateMessage(
        String eventId,
        String currentScore,
        Instant publishedAt
) {
}
