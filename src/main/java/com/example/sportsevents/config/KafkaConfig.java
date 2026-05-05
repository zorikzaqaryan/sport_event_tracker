package com.example.sportsevents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfig {

    // Creates the "live-score-updates" topic on startup if it does not already exist.
    // Partitions and replicas are driven by application.yml so they can be tuned per environment
    // without touching code.
    @Bean
    public NewTopic scoreUpdatesTopic(AppProperties properties) {
        AppProperties.Kafka kafka = properties.kafka();
        return TopicBuilder.name(kafka.scoreTopic())
                .partitions(kafka.scoreTopicPartitions())
                .replicas(kafka.scoreTopicReplicas())
                .build();
    }

    // By default, Kafka's JsonSerializer uses its own internal ObjectMapper, which serialises
    // Java time types (Instant, etc.) as Unix timestamps. This customizer replaces it with
    // Spring Boot's auto-configured ObjectMapper, which has JavaTimeModule registered and
    // WRITE_DATES_AS_TIMESTAMPS disabled — so "publishedAt" arrives on the topic as a
    // human-readable ISO-8601 string ("2026-11-11T11:11:00Z") instead of a raw number.
    @Bean
    public DefaultKafkaProducerFactoryCustomizer kafkaProducerFactoryCustomizer(ObjectMapper objectMapper) {
        return factory -> factory.setValueSerializer(new JsonSerializer<>(objectMapper));
    }
}
