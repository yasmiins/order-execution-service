package io.github.yasmiins.orderexecutionservice.service;

import org.springframework.stereotype.Component;

import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OrderMetrics {

    private final Counter ordersAccepted;
    private final Counter ordersCanceled;
    private final Counter ordersRejectedValidation;
    private final Counter ordersRejectedIdempotency;
    private final Counter fillsCreatedPartial;
    private final Counter fillsCreatedFull;

    public OrderMetrics(MeterRegistry registry) {
        ordersAccepted = Counter.builder("orders.accepted")
            .description("Orders accepted")
            .register(registry);
        ordersCanceled = Counter.builder("orders.canceled")
            .description("Orders canceled")
            .register(registry);
        ordersRejectedValidation = Counter.builder("orders.rejected")
            .description("Orders rejected")
            .tag("reason", "validation")
            .register(registry);
        ordersRejectedIdempotency = Counter.builder("orders.rejected")
            .description("Orders rejected")
            .tag("reason", "idempotency")
            .register(registry);
        fillsCreatedPartial = Counter.builder("orders.fills.created")
            .description("Fills created")
            .tag("type", "partial")
            .register(registry);
        fillsCreatedFull = Counter.builder("orders.fills.created")
            .description("Fills created")
            .tag("type", "full")
            .register(registry);
    }

    public void incrementAccepted() {
        ordersAccepted.increment();
    }

    public void incrementCanceled() {
        ordersCanceled.increment();
    }

    public void incrementRejectedValidation() {
        ordersRejectedValidation.increment();
    }

    public void incrementRejectedIdempotency() {
        ordersRejectedIdempotency.increment();
    }

    public void incrementFillCreated(OrderStatus status) {
        if (status == OrderStatus.FILLED) {
            fillsCreatedFull.increment();
            return;
        }
        if (status == OrderStatus.PARTIALLY_FILLED) {
            fillsCreatedPartial.increment();
        }
    }
}
