package com.chandrapal.orders.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls the outbox table and publishes pending events to Kafka.
 *
 * Delivery is at-least-once: if the app crashes after Kafka acks but before the row
 * is marked published, the event is sent again. Consumers are therefore idempotent.
 * The order id is used as the Kafka key, so all events for one order land on the
 * same partition and keep their order.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public OutboxPublisher(OutboxRepository outbox,
                           KafkaTemplate<String, String> kafka,
                           @Value("${app.kafka.topic}") String topic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:1000}")
    @Transactional
    public int publishPending() {
        List<OutboxEvent> pending = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        int sent = 0;
        for (OutboxEvent event : pending) {
            try {
                kafka.send(topic, event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException e) {
                // Stop at the first failure to preserve ordering; retry on the next poll.
                log.warn("Failed to publish outbox event {}, will retry", event.getId(), e);
                break;
            }
        }
        if (sent > 0) {
            log.info("Published {} outbox event(s)", sent);
        }
        return sent;
    }
}
