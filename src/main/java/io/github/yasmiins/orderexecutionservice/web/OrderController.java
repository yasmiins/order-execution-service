package io.github.yasmiins.orderexecutionservice.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;
import io.github.yasmiins.orderexecutionservice.service.OrderService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
            request.symbol(),
            request.side(),
            request.quantity(),
            request.price(),
            request.orderType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
        @RequestParam(required = false) String symbol,
        @RequestParam(required = false) OrderStatus status
    ) {
        List<OrderResponse> orders = orderService.getOrders(symbol, status).stream()
            .map(OrderResponse::from)
            .toList();
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        Order order = orderService.cancelOrder(id);
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
