package com.example.sportsevents.messaging;

import com.example.sportsevents.config.AppProperties;
import com.example.sportsevents.domain.ScoreUpdateMessage;
import com.example.sportsevents.metrics.ScoreMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class KafkaScorePublisherIntegrationTest {

    private static final String TOPIC = "live-score-updates";

    private static KafkaContainer kafka;
    private static KafkaTemplate<String, ScoreUpdateMessage> kafkaTemplate;

    @BeforeAll
    static void startKafka() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
        kafka.start();

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @AfterAll
    static void stopKafka() {
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    void publishesMessageWithEventIdKeyAndJsonPayload() {
        AppProperties properties = new AppProperties(
                new AppProperties.Polling(Duration.ofSeconds(10), 1),
                new AppProperties.Kafka(TOPIC, 1, 1),
                new AppProperties.External(new AppProperties.External.ScoreApi(
                        "http://unused", Duration.ofSeconds(2), Duration.ofSeconds(5)))
        );
        ScoreMetrics metrics = new ScoreMetrics(new SimpleMeterRegistry());
        KafkaScorePublisher publisher = new KafkaScorePublisher(kafkaTemplate, metrics, properties);

        Instant publishedAt = Instant.parse("2026-11-11T11:11:00Z");
        ScoreUpdateMessage message = new ScoreUpdateMessage("1234", "2:1", publishedAt);

        boolean ok = publisher.publish("1234", message).join();
        assertThat(ok).isTrue();

        AtomicReference<ConsumerRecord<String, ScoreUpdateMessage>> received = new AtomicReference<>();
        try (KafkaConsumer<String, ScoreUpdateMessage> consumer = newConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofMillis(500));
                records.forEach(received::set);
                assertThat(received.get()).isNotNull();
            });
        }

        ConsumerRecord<String, ScoreUpdateMessage> record = received.get();
        assertThat(record.key()).isEqualTo("1234");
        assertThat(record.value().eventId()).isEqualTo("1234");
        assertThat(record.value().currentScore()).isEqualTo("2:1");
        assertThat(record.value().publishedAt()).isEqualTo(publishedAt);
    }

    private KafkaConsumer<String, ScoreUpdateMessage> newConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ScoreUpdateMessage.class.getName());
        return new KafkaConsumer<>(consumerProps);
    }
}
