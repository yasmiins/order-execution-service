package io.github.yasmiins.orderexecutionservice.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import io.github.yasmiins.orderexecutionservice.config.SimulatedFillProperties;
import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;

@Service
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true", matchIfMissing = true)
public class SimulatedFillEngine {

    private static final List<OrderStatus> OPEN_STATUSES = List.of(
        OrderStatus.NEW,
        OrderStatus.PARTIALLY_FILLED
    );

    private final OrderRepository orderRepository;
    private final SimulatedFillProperties properties;
    private final SimulatedFillProcessor processor;
    private final Map<String, ReentrantLock> symbolLocks = new ConcurrentHashMap<>();

    public SimulatedFillEngine(
        OrderRepository orderRepository,
        SimulatedFillProperties properties,
        SimulatedFillProcessor processor
    ) {
        this.orderRepository = orderRepository;
        this.properties = properties;
        this.processor = processor;
    }

    public void processOpenOrders() {
        List<Order> openOrders = orderRepository.findByStatusIn(
            OPEN_STATUSES,
            Sort.by(Sort.Direction.ASC, "createdAt")
        );
        if (openOrders.isEmpty()) {
            return;
        }

        Map<String, List<Order>> ordersBySymbol = openOrders.stream()
            .collect(Collectors.groupingBy(order -> order.getInstrument().getSymbol()));

        ordersBySymbol.forEach(this::processWithLock);
    }

    void processWithLock(String symbol, List<Order> orders) {
        ReentrantLock lock = symbolLocks.computeIfAbsent(symbol, key -> new ReentrantLock());
        lock.lock();
        try {
            processOrdersForSymbol(symbol, orders);
        } finally {
            lock.unlock();
        }
    }

    void processOrdersForSymbol(String symbol, List<Order> orders) {
        var price = resolvePrice(symbol);
        for (Order order : orders) {
            try {
                processor.processOrder(order.getId(), price);
            } catch (ObjectOptimisticLockingFailureException ex) {
                // Another concurrent update won the race; skip this order for now.
            }
        }
    }

    private BigDecimal resolvePrice(String symbol) {
        Map<String, BigDecimal> prices = properties.getPrices();
        if (prices != null && prices.containsKey(symbol)) {
            return prices.get(symbol);
        }
        return properties.getDefaultPrice();
    }
}
