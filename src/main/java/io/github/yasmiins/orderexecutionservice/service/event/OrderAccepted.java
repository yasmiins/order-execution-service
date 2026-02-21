package io.github.yasmiins.orderexecutionservice.service.event;

import java.util.UUID;

/**
 * Emitted after commit when an order is accepted.
 */
public record OrderAccepted(UUID orderId) {
}
