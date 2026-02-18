package io.github.yasmiins.orderexecutionservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.yasmiins.orderexecutionservice.domain.Execution;
import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import io.github.yasmiins.orderexecutionservice.repository.ExecutionRepository;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "simulator.enabled=true",
        "simulator.scheduling.enabled=false",
        "simulator.min-fill-percent=0.25",
        "simulator.max-fill-percent=0.25"
    }
)
class SimulatedFillEngineIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private SimulatedFillEngine engine;

    @BeforeEach
    void cleanDatabase() {
        executionRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void fillThenCancel_keepsExecutions() {
        Order order = orderService.createOrder(
            "AAPL",
            OrderSide.BUY,
            new BigDecimal("10"),
            new BigDecimal("150"),
            OrderType.LIMIT
        );

        engine.processOpenOrders();

        Order afterFill = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(afterFill.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(afterFill.getFilledQuantity()).isGreaterThan(BigDecimal.ZERO);

        List<Execution> executions = executionRepository.findByOrderId(order.getId());
        assertThat(executions).hasSize(1);

        Order canceled = orderService.cancelOrder(order.getId());
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);

        List<Execution> stillThere = executionRepository.findByOrderId(order.getId());
        assertThat(stillThere).hasSize(1);
    }
}
