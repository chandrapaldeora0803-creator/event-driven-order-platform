package com.chandrapal.orders.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String customerId,
        @NotNull @DecimalMin("0.01") BigDecimal amount) {
}
