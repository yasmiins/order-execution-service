package io.github.yasmiins.orderexecutionservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Embedded
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 12)
    private OrderType orderType;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(precision = 18, scale = 6)
    private BigDecimal price;

    @Column(name = "filled_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal filledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(
        Instrument instrument,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal filledQuantity,
        OrderStatus status
    ) {
        this.instrument = instrument;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.filledQuantity = filledQuantity;
        this.status = status;
    }

    public Order(
        UUID id,
        Instrument instrument,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal filledQuantity,
        OrderStatus status
    ) {
        this.id = id;
        this.instrument = instrument;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.filledQuantity = filledQuantity;
        this.status = status;
    }

    @PrePersist
    void applyDefaults() {
        if (status == null) {
            status = OrderStatus.NEW;
        }
        if (orderType == null) {
            orderType = OrderType.LIMIT;
        }
        if (filledQuantity == null) {
            filledQuantity = BigDecimal.ZERO;
        }
    }

    public UUID getId() {
        return id;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getFilledQuantity() {
        return filledQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setFilledQuantity(BigDecimal filledQuantity) {
        this.filledQuantity = filledQuantity;
    }
}
