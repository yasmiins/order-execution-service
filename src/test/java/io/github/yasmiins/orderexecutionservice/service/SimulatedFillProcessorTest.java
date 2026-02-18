package io.github.yasmiins.orderexecutionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.yasmiins.orderexecutionservice.config.SimulatedFillProperties;
import io.github.yasmiins.orderexecutionservice.domain.Execution;
import io.github.yasmiins.orderexecutionservice.domain.Instrument;
import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import io.github.yasmiins.orderexecutionservice.repository.ExecutionRepository;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class SimulatedFillProcessorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExecutionRepository executionRepository;

    private SimulatedFillProperties properties;
    private SimulatedFillProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new SimulatedFillProperties();
        properties.setMinFillPercent(new BigDecimal("0.25"));
        properties.setMaxFillPercent(new BigDecimal("0.50"));
        properties.setDefaultPrice(new BigDecimal("100"));
        properties.setPrices(Map.of("AAPL", new BigDecimal("100")));

        processor = new SimulatedFillProcessor(orderRepository, executionRepository, properties);
    }

    @Test
    void processOrder_appliesPartialFillInRange() throws Exception {
        Order order = buildOrder(
            new BigDecimal("10"),
            new BigDecimal("150"),
            OrderSide.BUY
        );
        UUID id = assignId(order);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        processor.processOrder(id, new BigDecimal("100"));

        BigDecimal filled = order.getFilledQuantity();
        assertThat(filled).isGreaterThan(BigDecimal.ZERO);
        assertThat(filled).isBetween(new BigDecimal("2.5"), new BigDecimal("5.0"));
        assertThat(order.getStatus()).isIn(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED);
        verify(executionRepository, times(1)).save(any(Execution.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void processOrder_nonMarketableLimitBuy_skips() throws Exception {
        Order order = buildOrder(
            new BigDecimal("10"),
            new BigDecimal("50"),
            OrderSide.BUY
        );
        UUID id = assignId(order);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        processor.processOrder(id, new BigDecimal("100"));

        assertThat(order.getFilledQuantity()).isEqualByComparingTo("0");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        verify(executionRepository, never()).save(any(Execution.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void processOrder_smallRemaining_fillsCompletely() throws Exception {
        Order order = buildOrder(
            new BigDecimal("0.000001"),
            new BigDecimal("80"),
            OrderSide.SELL
        );
        UUID id = assignId(order);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        processor.processOrder(id, new BigDecimal("100"));

        assertThat(order.getFilledQuantity()).isEqualByComparingTo("0.000001");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        verify(executionRepository, times(1)).save(any(Execution.class));
    }

    private Order buildOrder(BigDecimal quantity, BigDecimal price, OrderSide side) {
        return new Order(
            new Instrument("AAPL"),
            side,
            OrderType.LIMIT,
            quantity,
            price,
            BigDecimal.ZERO,
            OrderStatus.NEW
        );
    }

    private UUID assignId(Order order) throws Exception {
        Field field = Order.class.getDeclaredField("id");
        field.setAccessible(true);
        UUID id = UUID.randomUUID();
        field.set(order, id);
        return id;
    }
}
