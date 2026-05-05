package com.example.sportsevents.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Polling polling,
        @NotNull Kafka kafka,
        @NotNull External external
) {
    public record Polling(
            @NotNull Duration interval,
            @Min(1) int schedulerPoolSize
    ) {
    }

    public record Kafka(
            @NotBlank String scoreTopic,
            @Min(1) int scoreTopicPartitions,
            @Min(1) int scoreTopicReplicas
    ) {
    }

    public record External(
            @NotNull ScoreApi scoreApi
    ) {
        public record ScoreApi(
                @NotBlank String baseUrl,
                @NotNull Duration connectTimeout,
                @NotNull Duration readTimeout
        ) {
        }
    }
}
