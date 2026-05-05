package com.example.sportsevents.messaging;

import com.example.sportsevents.config.AppProperties;
import com.example.sportsevents.domain.ScoreUpdateMessage;
import com.example.sportsevents.metrics.ScoreMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class KafkaScorePublisher implements ScorePublisher {

    private final KafkaTemplate<String, ScoreUpdateMessage> kafkaTemplate;
    private final ScoreMetrics metrics;
    private final String topic;

    public KafkaScorePublisher(KafkaTemplate<String, ScoreUpdateMessage> kafkaTemplate,
                               ScoreMetrics metrics,
                               AppProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
        this.topic = properties.kafka().scoreTopic();
    }

    /**
     * Sends the message asynchronously. Retries are delegated entirely to the Kafka producer
     * via {@code retries} and {@code delivery.timeout.ms} in application config, which avoids
     * the duplicate-publish risk that manual application-level retries carry: a timed-out
     * {@code future.get()} does not cancel the in-flight send, so retrying would produce a
     * second copy of the same message even though the first may still be delivered.
     */
    @Override
    public CompletableFuture<Boolean> publish(String eventId, ScoreUpdateMessage message) {
        return kafkaTemplate.send(topic, eventId, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Published score update eventId={} score={} topic={} partition={} offset={}",
                                eventId,
                                message.currentScore(),
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        metrics.recordPublishSuccess();
                    } else {
                        log.warn("Kafka publish failed eventId={} score={} topic={}: {}",
                                eventId,
                                message.currentScore(),
                                topic,
                                ex.getMessage(),
                                ex);
                        metrics.recordPublishFailure();
                    }
                })
                .thenApply(result -> true)
                .exceptionally(ex -> false);
    }
}
