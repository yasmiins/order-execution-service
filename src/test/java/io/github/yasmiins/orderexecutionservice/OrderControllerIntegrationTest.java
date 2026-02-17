package io.github.yasmiins.orderexecutionservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderSide;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.domain.OrderType;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;
import io.github.yasmiins.orderexecutionservice.web.ApiError;
import io.github.yasmiins.orderexecutionservice.web.CreateOrderRequest;
import io.github.yasmiins.orderexecutionservice.web.OrderResponse;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final RestTemplate restTemplate = new RestTemplate();

    @LocalServerPort
    private int port;

    @Autowired
    private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createOrder_persistsAndReturnsOrder() {
        CreateOrderRequest request = new CreateOrderRequest(
            "AAPL",
            OrderSide.BUY,
            null,
            new BigDecimal("10"),
            new BigDecimal("100.50")
        );

        String url = "http://localhost:" + port + "/orders";
        ResponseEntity<OrderResponse> response =
            restTemplate.postForEntity(url, request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.status()).isEqualTo(OrderStatus.NEW);
        assertThat(body.symbol()).isEqualTo("AAPL");
        assertThat(body.orderType()).isEqualTo(OrderType.LIMIT);
        assertThat(body.filledQuantity()).isEqualByComparingTo("0");

        UUID orderId = body.id();
        Optional<Order> saved = orderRepository.findById(orderId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getInstrument().getSymbol()).isEqualTo("AAPL");
        assertThat(saved.get().getQuantity()).isEqualByComparingTo("10");
        assertThat(saved.get().getPrice()).isEqualByComparingTo("100.50");
        assertThat(saved.get().getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(saved.get().getFilledQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void createMarketOrder_withoutPrice_persistsOrder() {
        CreateOrderRequest request = new CreateOrderRequest(
            "AAPL",
            OrderSide.SELL,
            OrderType.MARKET,
            new BigDecimal("5"),
            null
        );

        String url = "http://localhost:" + port + "/orders";
        ResponseEntity<OrderResponse> response =
            restTemplate.postForEntity(url, request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.orderType()).isEqualTo(OrderType.MARKET);
        assertThat(body.price()).isNull();
        assertThat(body.filledQuantity()).isEqualByComparingTo("0");

        Optional<Order> saved = orderRepository.findById(body.id());
        assertThat(saved).isPresent();
        assertThat(saved.get().getOrderType()).isEqualTo(OrderType.MARKET);
        assertThat(saved.get().getPrice()).isNull();
    }

    @Test
    void createLimitOrder_withoutPrice_returnsBadRequest() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
            "AAPL",
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("5"),
            null
        );

        String url = "http://localhost:" + port + "/orders";
        try {
            restTemplate.postForEntity(url, request, ApiError.class);
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = objectMapper.readValue(ex.getResponseBodyAsByteArray(), ApiError.class);
            assertThat(error.message()).isEqualTo("Limit orders require a positive price");
            return;
        }
        throw new AssertionError("Expected 400 Bad Request");
    }
}
