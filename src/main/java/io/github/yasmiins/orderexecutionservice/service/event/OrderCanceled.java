package io.github.yasmiins.orderexecutionservice.service.event;

import java.util.UUID;

/**
 * Emitted after commit when an order transitions to CANCELED.
 */
public record OrderCanceled(UUID orderId) {
}
