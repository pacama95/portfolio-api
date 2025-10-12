package com.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(
        name = "position_transactions",
        indexes = {
                @Index(name = "idx_position_transactions_position_id", columnList = "position_id"),
                @Index(name = "idx_position_transactions_composite", columnList = "position_id, transaction_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_position_transactions_transaction_id", columnNames = "transaction_id")
        }
)
@Getter
@Setter
public class PositionTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false, foreignKey = @ForeignKey(name = "fk_position_transactions_position"))
    private PositionEntity position;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public PositionTransactionEntity() {
    }

    public PositionTransactionEntity(PositionEntity position, UUID transactionId) {
        this.position = position;
        this.transactionId = transactionId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
