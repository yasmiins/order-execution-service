package io.github.yasmiins.orderexecutionservice.web;

import java.math.BigDecimal;

import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
    @NotBlank String symbol,
    @NotNull OrderSide side,
    OrderType orderType,
    @NotNull @Positive BigDecimal quantity,
    @Positive BigDecimal price
) {
}
