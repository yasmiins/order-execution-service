package io.github.yasmiins.orderexecutionservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yasmiins.orderexecutionservice.domain.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
