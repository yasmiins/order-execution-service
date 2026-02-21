package io.github.yasmiins.orderexecutionservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yasmiins.orderexecutionservice.domain.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    @Modifying
    @Query(
        value = """
            INSERT INTO idempotency_records (idempotency_key, request_fingerprint, order_id, created_at)
            VALUES (:key, :fingerprint, :orderId, NOW())
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("key") String key,
        @Param("fingerprint") String fingerprint,
        @Param("orderId") UUID orderId
    );
}
