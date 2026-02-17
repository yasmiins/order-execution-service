package io.github.yasmiins.orderexecutionservice.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;

public record OrderResponse(
    UUID id,
    String symbol,
    OrderSide side,
    OrderType orderType,
    BigDecimal quantity,
    BigDecimal filledQuantity,
    BigDecimal price,
    OrderStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getInstrument().getSymbol(),
            order.getSide(),
            order.getOrderType(),
            order.getQuantity(),
            order.getFilledQuantity(),
            order.getPrice(),
            order.getStatus(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
