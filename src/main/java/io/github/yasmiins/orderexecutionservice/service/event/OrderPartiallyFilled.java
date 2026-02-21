package io.github.yasmiins.orderexecutionservice.service.event;

import java.util.UUID;

/**
 * Emitted after commit when an order transitions to PARTIALLY_FILLED.
 */
public record OrderPartiallyFilled(UUID orderId) {
}
