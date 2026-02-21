package io.github.yasmiins.orderexecutionservice.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yasmiins.orderexecutionservice.config.OrderValidationProperties;
import io.github.yasmiins.orderexecutionservice.domain.Instrument;
import io.github.yasmiins.orderexecutionservice.domain.IdempotencyRecord;
import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import io.github.yasmiins.orderexecutionservice.repository.IdempotencyRecordRepository;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;
import io.github.yasmiins.orderexecutionservice.service.event.OrderAccepted;
import io.github.yasmiins.orderexecutionservice.service.event.OrderCanceled;

@Service
public class OrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String NULL_VALUE = "<null>";

    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final DomainEventPublisher eventPublisher;
    private final Set<String> supportedSymbols;
    private final BigDecimal maxOrderSize;

    public OrderService(
        OrderRepository orderRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        OrderValidationProperties validationProperties,
        DomainEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.eventPublisher = eventPublisher;
        this.supportedSymbols = normalizeSymbols(validationProperties.getSupportedSymbols());
        this.maxOrderSize = validationProperties.getMaxOrderSize();
    }

    @Transactional
    public Order createOrder(
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        OrderType orderType
    ) {
        OrderCreationData data = validateAndPrepare(symbol, side, quantity, price, orderType);
        Order order = buildOrder(UUID.randomUUID(), data);
        Order saved = orderRepository.save(order);
        eventPublisher.publishAfterCommit(new OrderAccepted(saved.getId()));
        return saved;
    }

    @Transactional
    public Order createOrderWithIdempotency(
        String idempotencyKey,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        OrderType orderType
    ) {
        String trimmedKey = trimKey(idempotencyKey);
        if (trimmedKey == null) {
            return createOrder(symbol, side, quantity, price, orderType);
        }

        String fingerprint = fingerprint(symbol, side, quantity, price, orderType);
        IdempotencyRecord existing = idempotencyRecordRepository.findById(trimmedKey).orElse(null);
        if (existing != null) {
            return resolveIdempotentReplay(existing, fingerprint);
        }

        OrderCreationData data = validateAndPrepare(symbol, side, quantity, price, orderType);
        UUID orderId = UUID.randomUUID();
        int inserted = idempotencyRecordRepository.insertIfAbsent(trimmedKey, fingerprint, orderId);
        if (inserted == 0) {
            IdempotencyRecord current = idempotencyRecordRepository.findById(trimmedKey)
                .orElseThrow(() -> new IllegalStateException("Missing idempotency record for key " + trimmedKey));
            return resolveIdempotentReplay(current, fingerprint);
        }

        Order order = buildOrder(orderId, data);
        Order saved = orderRepository.save(order);
        eventPublisher.publishAfterCommit(new OrderAccepted(saved.getId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrders(String symbol, OrderStatus status) {
        String normalizedSymbol = normalizeSymbolFilter(symbol);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (normalizedSymbol != null && status != null) {
            return orderRepository.findByInstrumentSymbolAndStatus(normalizedSymbol, status, sort);
        }
        if (normalizedSymbol != null) {
            return orderRepository.findByInstrumentSymbol(normalizedSymbol, sort);
        }
        if (status != null) {
            return orderRepository.findByStatus(status, sort);
        }
        return orderRepository.findAll(sort);
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = getOrder(orderId);
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELED) {
            return order;
        }
        if (status == OrderStatus.FILLED || status == OrderStatus.REJECTED) {
            throw new OrderStateException("Order in status " + status + " cannot be canceled");
        }
        order.setStatus(OrderStatus.CANCELED);
        Order saved = orderRepository.save(order);
        eventPublisher.publishAfterCommit(new OrderCanceled(saved.getId()));
        return saved;
    }

    private Order resolveIdempotentReplay(IdempotencyRecord record, String fingerprint) {
        if (!record.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                "Idempotency key already used with different request payload"
            );
        }
        return getOrder(record.getOrderId());
    }

    private OrderCreationData validateAndPrepare(
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
        return new OrderCreationData(normalizedSymbol, side, resolvedType, quantity, price);
    }

    private Order buildOrder(UUID id, OrderCreationData data) {
        if (id == null) {
            return new Order(
                new Instrument(data.symbol()),
                data.side(),
                data.orderType(),
                data.quantity(),
                data.price(),
                ZERO,
                OrderStatus.NEW
            );
        }
        return new Order(
            id,
            new Instrument(data.symbol()),
            data.side(),
            data.orderType(),
            data.quantity(),
            data.price(),
            ZERO,
            OrderStatus.NEW
        );
    }

    private String trimKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String fingerprint(
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        OrderType orderType
    ) {
        String normalizedSymbol = normalizeSymbolForFingerprint(symbol);
        OrderType resolvedOrderType = resolveOrderType(orderType);
        String normalizedQuantity = normalizeNumberForFingerprint(quantity);
        String normalizedPrice = normalizeNumberForFingerprint(price);
        StringBuilder builder = new StringBuilder();
        appendFingerprint(builder, normalizedSymbol);
        appendFingerprint(builder, side == null ? null : side.name());
        appendFingerprint(builder, normalizedQuantity);
        appendFingerprint(builder, normalizedPrice);
        appendFingerprint(builder, resolvedOrderType == null ? null : resolvedOrderType.name());
        return sha256Hex(builder.toString());
    }

    private void appendFingerprint(StringBuilder builder, String value) {
        builder.append(value == null ? NULL_VALUE : value);
        builder.append('|');
    }

    private String normalizeSymbolForFingerprint(String symbol) {
        if (symbol == null) {
            return null;
        }
        String trimmed = symbol.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeNumberForFingerprint(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String sha256Hex(String value) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
        byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hashed.length * 2);
        for (byte b : hashed) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
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

    private String normalizeSymbolFilter(String symbol) {
        if (symbol == null) {
            return null;
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

    private record OrderCreationData(
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal price
    ) {
    }
}
