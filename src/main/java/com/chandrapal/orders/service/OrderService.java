package com.chandrapal.orders.service;

import com.chandrapal.orders.api.CreateOrderRequest;
import com.chandrapal.orders.api.OrderResponse;
import com.chandrapal.orders.domain.Order;
import com.chandrapal.orders.domain.OrderRepository;
import com.chandrapal.orders.domain.OrderStatus;
import com.chandrapal.orders.messaging.OrderEvent;
import com.chandrapal.orders.outbox.OutboxEvent;
import com.chandrapal.orders.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderService {

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String CACHE = "orders";

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orders, OutboxRepository outbox, ObjectMapper objectMapper) {
        this.orders = orders;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Saves the order and its OrderCreated event in ONE transaction (transactional outbox).
     * We never call Kafka here: if we did, a Kafka failure after the DB commit (or the
     * other way round) would leave the two systems inconsistent.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = orders.save(Order.create(request.customerId(), request.amount()));
        OrderEvent event = new OrderEvent(UUID.randomUUID(), order.getId(),
                order.getCustomerId(), order.getAmount(), ORDER_CREATED);
        outbox.save(new OutboxEvent(event.eventId(), order.getId(), ORDER_CREATED, toJson(event)));
        return OrderResponse.from(order);
    }

    /** Read-through cache: repeated reads are served from Redis instead of PostgreSQL. */
    @Cacheable(cacheNames = CACHE, key = "#id")
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return orders.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /** Any status change evicts the cached copy so readers never see a stale status. */
    @CacheEvict(cacheNames = CACHE, key = "#id")
    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus next) {
        Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        order.transitionTo(next);
        return OrderResponse.from(order);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise event", e);
        }
    }
}
