package com.chandrapal.orders.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event published to Kafka. eventId is unique per event and is what consumers
 * use for de-duplication; orderId is used as the Kafka message key.
 */
public record OrderEvent(UUID eventId, UUID orderId, String customerId, BigDecimal amount, String type) {
}
