package io.github.yasmiins.orderexecutionservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
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
import io.github.yasmiins.orderexecutionservice.repository.IdempotencyRecordRepository;
import io.github.yasmiins.orderexecutionservice.repository.OrderRepository;
import io.github.yasmiins.orderexecutionservice.web.ApiError;
import io.github.yasmiins.orderexecutionservice.web.CreateOrderRequest;
import io.github.yasmiins.orderexecutionservice.web.OrderResponse;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "simulator.enabled=false"
)
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

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void cleanDatabase() {
        idempotencyRecordRepository.deleteAll();
        orderRepository.deleteAll();
    }

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

    @Test
    void getOrder_returnsOrder() {
        OrderResponse created = createLimitOrder("AAPL");

        String url = ordersUrl() + "/" + created.id();
        ResponseEntity<OrderResponse> response =
            restTemplate.getForEntity(url, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(created.id());
        assertThat(body.symbol()).isEqualTo("AAPL");
    }

    @Test
    void cancelOrder_updatesStatus() {
        OrderResponse created = createLimitOrder("AAPL");

        String url = ordersUrl() + "/" + created.id() + "/cancel";
        ResponseEntity<OrderResponse> response =
            restTemplate.postForEntity(url, null, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(OrderStatus.CANCELED);

        Optional<Order> saved = orderRepository.findById(created.id());
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cancelOrder_twice_isIdempotent() {
        OrderResponse created = createLimitOrder("AAPL");

        String url = ordersUrl() + "/" + created.id() + "/cancel";
        ResponseEntity<OrderResponse> first =
            restTemplate.postForEntity(url, null, OrderResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().status()).isEqualTo(OrderStatus.CANCELED);

        ResponseEntity<OrderResponse> second =
            restTemplate.postForEntity(url, null, OrderResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cancelOrder_filled_returnsConflict() {
        OrderResponse created = createLimitOrder("AAPL");
        Order order = orderRepository.findById(created.id()).orElseThrow();
        order.setFilledQuantity(order.getQuantity());
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);

        String url = ordersUrl() + "/" + created.id() + "/cancel";
        try {
            restTemplate.postForEntity(url, null, ApiError.class);
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            return;
        }
        throw new AssertionError("Expected 409 Conflict");
    }

    @Test
    void cancelOrder_nonexistent_returnsNotFound() {
        String url = ordersUrl() + "/" + UUID.randomUUID() + "/cancel";
        try {
            restTemplate.postForEntity(url, null, ApiError.class);
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            return;
        }
        throw new AssertionError("Expected 404 Not Found");
    }

    @Test
    void getOrders_invalidStatus_returnsBadRequest() throws Exception {
        String url = ordersUrl() + "?status=notastatus";
        try {
            restTemplate.getForEntity(url, ApiError.class);
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = objectMapper.readValue(ex.getResponseBodyAsByteArray(), ApiError.class);
            assertThat(error.message()).contains("status");
            return;
        }
        throw new AssertionError("Expected 400 Bad Request");
    }

    @Test
    void getOrders_blankSymbol_returnsBadRequest() throws Exception {
        String url = ordersUrl() + "?symbol=";
        try {
            restTemplate.getForEntity(url, ApiError.class);
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = objectMapper.readValue(ex.getResponseBodyAsByteArray(), ApiError.class);
            assertThat(error.message()).contains("Symbol must be provided");
            return;
        }
        throw new AssertionError("Expected 400 Bad Request");
    }

    @Test
    void getOrders_withFilters_returnsMatching() {
        OrderResponse aapl = createLimitOrder("AAPL");
        OrderResponse msft = createLimitOrder("MSFT");

        String cancelUrl = ordersUrl() + "/" + aapl.id() + "/cancel";
        restTemplate.postForEntity(cancelUrl, null, OrderResponse.class);

        List<OrderResponse> canceled = fetchOrders("?status=CANCELED");
        assertThat(canceled).hasSize(1);
        assertThat(canceled.get(0).id()).isEqualTo(aapl.id());

        List<OrderResponse> msftOrders = fetchOrders("?symbol=MSFT");
        assertThat(msftOrders).hasSize(1);
        assertThat(msftOrders.get(0).id()).isEqualTo(msft.id());

        List<OrderResponse> aaplCanceled = fetchOrders("?symbol=AAPL&status=CANCELED");
        assertThat(aaplCanceled).hasSize(1);
        assertThat(aaplCanceled.get(0).id()).isEqualTo(aapl.id());
    }

    @Test
    void createOrder_idempotencyKey_reusesOrder() {
        CreateOrderRequest request = new CreateOrderRequest(
            "AAPL",
            OrderSide.BUY,
            null,
            new BigDecimal("10"),
            new BigDecimal("100.50")
        );

        String url = ordersUrl();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "order-123");
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OrderResponse> first = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            OrderResponse.class
        );
        ResponseEntity<OrderResponse> second = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            OrderResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void createOrder_idempotencyKey_mismatch_returnsConflict() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
            "AAPL",
            OrderSide.BUY,
            null,
            new BigDecimal("10"),
            new BigDecimal("100.50")
        );
        CreateOrderRequest different = new CreateOrderRequest(
            "AAPL",
            OrderSide.BUY,
            null,
            new BigDecimal("12"),
            new BigDecimal("100.50")
        );

        String url = ordersUrl();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "order-456");
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);
        HttpEntity<CreateOrderRequest> differentEntity = new HttpEntity<>(different, headers);

        ResponseEntity<OrderResponse> first = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            OrderResponse.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        try {
            restTemplate.exchange(url, HttpMethod.POST, differentEntity, ApiError.class);
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ApiError error = objectMapper.readValue(ex.getResponseBodyAsByteArray(), ApiError.class);
            assertThat(error.message()).contains("Idempotency");
            return;
        }
        throw new AssertionError("Expected 409 Conflict");
    }

    private OrderResponse createLimitOrder(String symbol) {
        CreateOrderRequest request = new CreateOrderRequest(
            symbol,
            OrderSide.BUY,
            null,
            new BigDecimal("10"),
            new BigDecimal("100.50")
        );

        String url = ordersUrl();
        ResponseEntity<OrderResponse> response =
            restTemplate.postForEntity(url, request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private List<OrderResponse> fetchOrders(String query) {
        String url = ordersUrl() + query;
        ResponseEntity<List<OrderResponse>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {
            }
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<OrderResponse> body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private String ordersUrl() {
        return "http://localhost:" + port + "/orders";
    }
}
