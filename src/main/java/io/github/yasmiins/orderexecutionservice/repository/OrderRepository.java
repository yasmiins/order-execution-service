package io.github.yasmiins.orderexecutionservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yasmiins.orderexecutionservice.domain.Order;
import io.github.yasmiins.orderexecutionservice.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatusIn(List<OrderStatus> statuses, Sort sort);

    List<Order> findByInstrumentSymbol(String symbol, Sort sort);

    List<Order> findByStatus(OrderStatus status, Sort sort);

    List<Order> findByInstrumentSymbolAndStatus(String symbol, OrderStatus status, Sort sort);
}
