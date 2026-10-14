package com.chandrapal.orders.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outbox;

    @Mock
    private KafkaTemplate<String, String> kafka;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outbox, kafka, "orders.events");
    }

    private static OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "OrderCreated", "{}");
    }

    @Test
    void publishesPendingEventsAndMarksThemPublished() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        int sent = publisher.publishPending();

        assertThat(sent).isEqualTo(2);
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
    }

    @Test
    void stopsAtFirstFailureSoOrderingIsPreserved() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        int sent = publisher.publishPending();

        assertThat(sent).isZero();
        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void usesOrderIdAsKafkaKey() {
        OutboxEvent only = event();
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(only));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        verify(kafka).send("orders.events", only.getAggregateId().toString(), "{}");
    }
}
