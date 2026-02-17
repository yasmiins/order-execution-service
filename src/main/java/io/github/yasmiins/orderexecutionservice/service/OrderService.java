package io.github.yasmiins.orderexecutionservice.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.yasmiins.orderexecutionservice.config.OrderValidationProperties;
import io.github.yasmiins.orderexecutionservice.domain.Instrument;
import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;

@Service
public class OrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final OrderRepository orderRepository;
    private final Set<String> supportedSymbols;
    private final BigDecimal maxOrderSize;

    public OrderService(OrderRepository orderRepository, OrderValidationProperties validationProperties) {
        this.orderRepository = orderRepository;
        this.supportedSymbols = normalizeSymbols(validationProperties.getSupportedSymbols());
        this.maxOrderSize = validationProperties.getMaxOrderSize();
    }

    public Order createOrder(
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        OrderType orderType
    ) {
        OrderType resolvedType = resolveOrderType(orderType);
        String normalizedSymbol = normalizeSymbol(symbol);
        validateSymbol(normalizedSymbol);
        validateSide(side);
        validateQuantity(quantity);
        validateOrderSize(quantity);
        validatePrice(resolvedType, price);

        Order order = new Order(
            new Instrument(normalizedSymbol),
            side,
            resolvedType,
            quantity,
            price,
            ZERO,
            OrderStatus.NEW
        );
        return orderRepository.save(order);
    }

    private void validateSymbol(String symbol) {
        if (!supportedSymbols.isEmpty() && !supportedSymbols.contains(symbol)) {
            throw new OrderValidationException("Unsupported symbol: " + symbol);
        }
    }

    private void validateOrderSize(BigDecimal quantity) {
        if (maxOrderSize != null && quantity.compareTo(maxOrderSize) > 0) {
            throw new OrderValidationException("Order size exceeds max of " + maxOrderSize);
        }
    }

    private void validatePrice(OrderType orderType, BigDecimal price) {
        if (orderType == OrderType.LIMIT) {
            if (price == null || price.signum() <= 0) {
                throw new OrderValidationException("Limit orders require a positive price");
            }
        } else if (price != null) {
            throw new OrderValidationException("Market orders must not include a price");
        }
    }

    private void validateSide(OrderSide side) {
        if (side == null) {
            throw new OrderValidationException("Order side must be provided");
        }
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new OrderValidationException("Order quantity must be positive");
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            throw new OrderValidationException("Symbol must be provided");
        }
        String trimmed = symbol.trim();
        if (trimmed.isEmpty()) {
            throw new OrderValidationException("Symbol must be provided");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private Set<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Set.of();
        }
        return symbols.stream()
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    private OrderType resolveOrderType(OrderType orderType) {
        return orderType != null ? orderType : OrderType.LIMIT;
    }
}
