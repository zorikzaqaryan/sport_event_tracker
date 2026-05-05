package com.example.sportsevents.client;

import com.example.sportsevents.domain.ScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ExternalScoreClient {

    private final RestClient restClient;

    public ExternalScoreClient(@Qualifier("externalScoreRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ScoreResponse fetchScore(String eventId) {
        log.debug("Calling external score API for eventId={}", eventId);
        return restClient.get()
                .uri("/scores/{eventId}", eventId)
                .retrieve()
                .body(ScoreResponse.class);
    }
}
