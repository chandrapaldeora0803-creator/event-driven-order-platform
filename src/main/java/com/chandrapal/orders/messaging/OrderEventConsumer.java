package com.chandrapal.orders.messaging;

import com.chandrapal.orders.domain.OrderStatus;
import com.chandrapal.orders.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Consumes OrderCreated events and runs a simple risk check:
 * orders up to the auto-approve limit are CONFIRMED, larger ones are REJECTED.
 *
 * Kafka delivers at-least-once, so the same event can arrive twice. The consumer
 * stores each handled eventId in processed_events inside the same transaction as
 * the status change; a duplicate is detected and skipped.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProcessedEventRepository processed;
    private final OrderService orders;
    private final ObjectMapper objectMapper;
    private final BigDecimal autoApproveLimit;

    public OrderEventConsumer(ProcessedEventRepository processed,
                              OrderService orders,
                              ObjectMapper objectMapper,
                              @Value("${app.risk.auto-approve-limit:10000}") BigDecimal autoApproveLimit) {
        this.processed = processed;
        this.orders = orders;
        this.objectMapper = objectMapper;
        this.autoApproveLimit = autoApproveLimit;
    }

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "order-processor")
    @Transactional
    public void onMessage(String payload) throws JsonProcessingException {
        OrderEvent event = objectMapper.readValue(payload, OrderEvent.class);

        if (!OrderService.ORDER_CREATED.equals(event.type())) {
            return;
        }
        if (processed.existsById(event.eventId())) {
            log.info("Skipping duplicate event {}", event.eventId());
            return;
        }

        OrderStatus decision = event.amount().compareTo(autoApproveLimit) <= 0
                ? OrderStatus.CONFIRMED
                : OrderStatus.REJECTED;
        try {
            orders.updateStatus(event.orderId(), decision);
            log.info("Order {} -> {}", event.orderId(), decision);
        } catch (IllegalStateException e) {
            // e.g. the customer cancelled before we processed the event; nothing to do.
            log.info("Ignoring event {}: {}", event.eventId(), e.getMessage());
        }
        processed.save(new ProcessedEvent(event.eventId()));
    }
}
