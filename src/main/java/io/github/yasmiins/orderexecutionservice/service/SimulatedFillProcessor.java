package io.github.yasmiins.orderexecutionservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yasmiins.orderexecutionservice.config.SimulatedFillProperties;
import io.github.yasmiins.orderexecutionservice.domain.Execution;
import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import io.github.yasmiins.orderexecutionservice.repository.ExecutionRepository;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;
import io.github.yasmiins.orderexecutionservice.service.event.OrderFilled;
import io.github.yasmiins.orderexecutionservice.service.event.OrderPartiallyFilled;

@Service
public class SimulatedFillProcessor {

    private static final int SCALE = 6;

    private final OrderRepository orderRepository;
    private final ExecutionRepository executionRepository;
    private final SimulatedFillProperties properties;
    private final DomainEventPublisher eventPublisher;

    public SimulatedFillProcessor(
        OrderRepository orderRepository,
        ExecutionRepository executionRepository,
        SimulatedFillProperties properties,
        DomainEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.executionRepository = executionRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void processOrder(UUID orderId, BigDecimal price) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }

        OrderStatus beforeStatus = order.getStatus();
        if (beforeStatus == OrderStatus.CANCELED
            || beforeStatus == OrderStatus.REJECTED
            || beforeStatus == OrderStatus.FILLED) {
            return;
        }

        if (!isMarketable(order, price)) {
            return;
        }

        BigDecimal remaining = order.getQuantity().subtract(order.getFilledQuantity());
        if (remaining.signum() <= 0) {
            if (beforeStatus != OrderStatus.FILLED) {
                order.setStatus(OrderStatus.FILLED);
                orderRepository.save(order);
                publishStatusTransition(beforeStatus, OrderStatus.FILLED, order.getId());
            }
            return;
        }

        BigDecimal fillQuantity = calculateFillQuantity(order, remaining);
        Execution execution = new Execution(
            order,
            order.getInstrument(),
            fillQuantity,
            price
        );
        executionRepository.save(execution);

        BigDecimal newFilled = order.getFilledQuantity().add(fillQuantity);
        order.setFilledQuantity(newFilled);
        OrderStatus nextStatus = newFilled.compareTo(order.getQuantity()) >= 0
            ? OrderStatus.FILLED
            : OrderStatus.PARTIALLY_FILLED;
        order.setStatus(nextStatus);
        orderRepository.save(order);
        publishStatusTransition(beforeStatus, nextStatus, order.getId());
    }

    private boolean isMarketable(Order order, BigDecimal price) {
        if (order.getOrderType() == OrderType.MARKET) {
            return true;
        }
        BigDecimal limitPrice = order.getPrice();
        if (limitPrice == null) {
            return false;
        }
        if (order.getSide() == OrderSide.BUY) {
            return limitPrice.compareTo(price) >= 0;
        }
        return limitPrice.compareTo(price) <= 0;
    }

    private BigDecimal calculateFillQuantity(Order order, BigDecimal remaining) {
        BigDecimal percent = resolveFillPercent(order);
        BigDecimal filled = remaining.multiply(percent).setScale(SCALE, RoundingMode.DOWN);
        if (filled.signum() == 0) {
            return remaining;
        }
        if (filled.compareTo(remaining) > 0) {
            return remaining;
        }
        return filled;
    }

    private BigDecimal resolveFillPercent(Order order) {
        BigDecimal min = properties.getMinFillPercent();
        BigDecimal max = properties.getMaxFillPercent();
        int minBp = toBasisPoints(min);
        int maxBp = toBasisPoints(max);
        if (maxBp < minBp) {
            int temp = maxBp;
            maxBp = minBp;
            minBp = temp;
        }
        int range = maxBp - minBp;
        int hash = Math.abs(Objects.hash(order.getId(), order.getFilledQuantity()));
        int pick = range == 0 ? minBp : minBp + (hash % (range + 1));
        return BigDecimal.valueOf(pick).movePointLeft(2);
    }

    private int toBasisPoints(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private void publishStatusTransition(OrderStatus before, OrderStatus after, UUID orderId) {
        if (before == after) {
            return;
        }
        if (after == OrderStatus.PARTIALLY_FILLED) {
            eventPublisher.publishAfterCommit(new OrderPartiallyFilled(orderId));
        }
        if (after == OrderStatus.FILLED) {
            eventPublisher.publishAfterCommit(new OrderFilled(orderId));
        }
    }
}
