package com.chandrapal.orders.api;

import com.chandrapal.orders.domain.Order;
import com.chandrapal.orders.domain.OrderStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API view of an order. Also the object stored in the Redis cache, so it is an
 * immutable, Serializable record instead of the JPA entity.
 */
public record OrderResponse(UUID id,
                            String customerId,
                            BigDecimal amount,
                            OrderStatus status,
                            Instant createdAt) implements Serializable {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getAmount(),
                order.getStatus(), order.getCreatedAt());
    }
}
