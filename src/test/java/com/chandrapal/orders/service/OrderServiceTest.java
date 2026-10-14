package com.chandrapal.orders.service;

import com.chandrapal.orders.api.CreateOrderRequest;
import com.chandrapal.orders.api.OrderResponse;
import com.chandrapal.orders.domain.Order;
import com.chandrapal.orders.domain.OrderRepository;
import com.chandrapal.orders.domain.OrderStatus;
import com.chandrapal.orders.outbox.OutboxEvent;
import com.chandrapal.orders.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orders;

    @Mock
    private OutboxRepository outbox;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, outbox, new ObjectMapper());
    }

    @Test
    void createWritesOrderAndOutboxEventTogether() {
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(new CreateOrderRequest("cust-1", new BigDecimal("250.00")));

        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getAggregateId()).isEqualTo(response.id());
        assertThat(event.getEventType()).isEqualTo(OrderService.ORDER_CREATED);
        assertThat(event.getPayload()).contains("cust-1").contains(response.id().toString());
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void getThrowsWhenOrderIsMissing() {
        UUID id = UUID.randomUUID();
        when(orders.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void updateStatusMovesOrderForward() {
        Order order = Order.create("cust-1", BigDecimal.TEN);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.updateStatus(order.getId(), OrderStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelledOrderCannotBeConfirmed() {
        Order order = Order.create("cust-1", BigDecimal.TEN);
        order.transitionTo(OrderStatus.CANCELLED);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(order.getId(), OrderStatus.CONFIRMED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void orderRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Order.create("cust-1", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
