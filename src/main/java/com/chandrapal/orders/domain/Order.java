package com.chandrapal.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Optimistic locking: concurrent updates to the same order fail fast instead of overwriting. */
    @Version
    private long version;

    protected Order() {
        // for JPA
    }

    public static Order create(String customerId, BigDecimal amount) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.amount = amount;
        order.status = OrderStatus.CREATED;
        order.createdAt = Instant.now();
        return order;
    }

    public void transitionTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException(
                    "Order " + id + " cannot move from " + status + " to " + next);
        }
        this.status = next;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
