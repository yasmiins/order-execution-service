package io.github.yasmiins.orderexecutionservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yasmiins.orderexecutionservice.domain.Execution;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
}
