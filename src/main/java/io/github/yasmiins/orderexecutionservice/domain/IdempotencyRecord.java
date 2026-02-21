package io.github.yasmiins.orderexecutionservice.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestFingerprint, UUID orderId) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.orderId = orderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
