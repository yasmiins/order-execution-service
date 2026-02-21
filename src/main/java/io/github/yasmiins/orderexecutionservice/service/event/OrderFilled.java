package io.github.yasmiins.orderexecutionservice.service.event;

import java.util.UUID;

/**
 * Emitted after commit when an order transitions to FILLED.
 */
public record OrderFilled(UUID orderId) {
}
