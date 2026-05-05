package com.example.sportsevents.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ScoreResponse(
        String eventId,
        String currentScore
) {
    @JsonCreator
    public ScoreResponse(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("currentScore") String currentScore
    ) {
        this.eventId = eventId;
        this.currentScore = currentScore;
    }
}
