package com.chandrapal.orders.messaging;

import com.chandrapal.orders.domain.OrderStatus;
import com.chandrapal.orders.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private ProcessedEventRepository processed;

    @Mock
    private OrderService orders;

    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(processed, orders, mapper, new BigDecimal("1000"));
    }

    private String payload(String amount) throws Exception {
        return mapper.writeValueAsString(new OrderEvent(eventId, orderId, "cust-1",
                new BigDecimal(amount), OrderService.ORDER_CREATED));
    }

    @Test
    void confirmsOrderWithinLimit() throws Exception {
        when(processed.existsById(eventId)).thenReturn(false);

        consumer.onMessage(payload("500"));

        verify(orders).updateStatus(orderId, OrderStatus.CONFIRMED);
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void rejectsOrderAboveLimit() throws Exception {
        when(processed.existsById(eventId)).thenReturn(false);

        consumer.onMessage(payload("5000"));

        verify(orders).updateStatus(orderId, OrderStatus.REJECTED);
    }

    @Test
    void duplicateEventIsSkipped() throws Exception {
        when(processed.existsById(eventId)).thenReturn(true);

        consumer.onMessage(payload("500"));

        verifyNoInteractions(orders);
        verify(processed, never()).save(any());
    }

    @Test
    void eventForAlreadyCancelledOrderIsStillMarkedProcessed() throws Exception {
        when(processed.existsById(eventId)).thenReturn(false);
        when(orders.updateStatus(orderId, OrderStatus.CONFIRMED))
                .thenThrow(new IllegalStateException("already cancelled"));

        consumer.onMessage(payload("500"));

        verify(processed).save(any(ProcessedEvent.class));
    }
}
