package com.example.sportsevents.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient externalScoreRestClient(AppProperties properties) {
        AppProperties.External.ScoreApi cfg = properties.external().scoreApi();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) cfg.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) cfg.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
