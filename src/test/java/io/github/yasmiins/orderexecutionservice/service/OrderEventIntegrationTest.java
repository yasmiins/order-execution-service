package io.github.yasmiins.orderexecutionservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.yasmiins.orderexecutionservice.config.SimulatedFillProperties;
import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import io.github.yasmiins.orderexecutionservice.repository.ExecutionRepository;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;
import io.github.yasmiins.orderexecutionservice.service.event.OrderAccepted;
import io.github.yasmiins.orderexecutionservice.service.event.OrderCanceled;
import io.github.yasmiins.orderexecutionservice.service.event.OrderFilled;
import io.github.yasmiins.orderexecutionservice.service.event.OrderPartiallyFilled;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "simulator.enabled=false"
)
class OrderEventIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private SimulatedFillProcessor fillProcessor;

    @Autowired
    private SimulatedFillProperties properties;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private EventCollector eventCollector;

    @BeforeEach
    void cleanDatabase() {
        executionRepository.deleteAll();
        orderRepository.deleteAll();
        eventCollector.reset();
    }

    @Test
    void orderAccepted_emittedOnce() {
        orderService.createOrder(
            "AAPL",
            OrderSide.BUY,
            new BigDecimal("10"),
            new BigDecimal("150"),
            OrderType.LIMIT
        );

        assertThat(eventCollector.acceptedCount()).isEqualTo(1);
    }

    @Test
    void orderCanceled_emittedOncePerTransition() {
        Order order = orderService.createOrder(
            "AAPL",
            OrderSide.BUY,
            new BigDecimal("10"),
            new BigDecimal("150"),
            OrderType.LIMIT
        );

        orderService.cancelOrder(order.getId());
        orderService.cancelOrder(order.getId());

        assertThat(eventCollector.canceledCount()).isEqualTo(1);
    }

    @Test
    void orderFilled_emittedWhenFilled() {
        properties.setMinFillPercent(BigDecimal.ONE);
        properties.setMaxFillPercent(BigDecimal.ONE);

        Order order = orderService.createOrder(
            "AAPL",
            OrderSide.BUY,
            new BigDecimal("10"),
            new BigDecimal("150"),
            OrderType.LIMIT
        );

        fillProcessor.processOrder(order.getId(), new BigDecimal("100"));

        assertThat(eventCollector.filledCount()).isEqualTo(1);
    }

    @Test
    void orderPartiallyFilled_emittedWhenTransitioned() {
        properties.setMinFillPercent(new BigDecimal("0.50"));
        properties.setMaxFillPercent(new BigDecimal("0.50"));

        Order order = orderService.createOrder(
            "AAPL",
            OrderSide.BUY,
            new BigDecimal("10"),
            new BigDecimal("150"),
            OrderType.LIMIT
        );

        fillProcessor.processOrder(order.getId(), new BigDecimal("100"));

        assertThat(eventCollector.partiallyFilledCount()).isEqualTo(1);
        assertThat(eventCollector.filledCount()).isEqualTo(0);
    }

    @TestConfiguration
    static class EventTestConfig {
        @Bean
        EventCollector eventCollector() {
            return new EventCollector();
        }
    }

    static class EventCollector {
        private final AtomicInteger accepted = new AtomicInteger();
        private final AtomicInteger canceled = new AtomicInteger();
        private final AtomicInteger filled = new AtomicInteger();
        private final AtomicInteger partiallyFilled = new AtomicInteger();

        @EventListener
        public void onAccepted(OrderAccepted event) {
            accepted.incrementAndGet();
        }

        @EventListener
        public void onCanceled(OrderCanceled event) {
            canceled.incrementAndGet();
        }

        @EventListener
        public void onFilled(OrderFilled event) {
            filled.incrementAndGet();
        }

        @EventListener
        public void onPartiallyFilled(OrderPartiallyFilled event) {
            partiallyFilled.incrementAndGet();
        }

        int acceptedCount() {
            return accepted.get();
        }

        int canceledCount() {
            return canceled.get();
        }

        int filledCount() {
            return filled.get();
        }

        int partiallyFilledCount() {
            return partiallyFilled.get();
        }

        void reset() {
            accepted.set(0);
            canceled.set(0);
            filled.set(0);
            partiallyFilled.set(0);
        }
    }
}
