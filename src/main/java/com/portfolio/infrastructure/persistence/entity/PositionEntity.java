package com.portfolio.infrastructure.persistence.entity;

import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the positions table
 */
@Entity
@Table(
        name = "positions",
        indexes = {
                @Index(name = "idx_positions_ticker", columnList = "ticker")
        }
)
@Getter
@Setter
public class PositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "ticker", nullable = false, unique = true, length = 20)
    private String ticker;

    @Column(name = "shares_owned", nullable = false, precision = 18, scale = 6)
    private BigDecimal sharesOwned;

    @Column(name = "average_cost_per_share", nullable = false, precision = 18, scale = 4)
    private BigDecimal averageCostPerShare;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, columnDefinition = "currency_type")
    private Currency currency;

    @Column(name = "total_invested_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalInvestedAmount;

    @Column(name = "total_transaction_fees", precision = 18, scale = 4)
    private BigDecimal totalTransactionFees;

    @Column(name = "first_purchase_date")
    private LocalDate firstPurchaseDate;

    @Column(name = "unrealized_gain_loss", precision = 18, scale = 4)
    private BigDecimal unrealizedGainLoss;

    @Column(name = "total_market_value", precision = 18, scale = 4)
    private BigDecimal totalMarketValue;

    @Column(name = "latest_market_price", precision = 18, scale = 4)
    private BigDecimal latestMarketPrice;

    @Column(name = "market_price_last_updated")
    private OffsetDateTime marketPriceLastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_event_applied_at")
    private Instant lastEventAppliedAt;

    @Column(name = "exchange", length = 100)
    private String exchange;

    @Column(name = "country", length = 100)
    private String country;

    @OneToMany(
            mappedBy = "position",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<PositionTransactionEntity> transactions = new ArrayList<>();

    // Default constructor
    public PositionEntity() {
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

    public void addTransaction(PositionTransactionEntity tx) {
        tx.setPosition(this);
        this.transactions.add(tx);
    }

    public void removeTransaction(PositionTransactionEntity tx) {
        tx.setPosition(null);
        this.transactions.remove(tx);
    }

    public void update(Position position) {
        this.ticker = position.getTicker();
        this.sharesOwned = position.getSharesOwned();
        this.averageCostPerShare = position.getAverageCostPerShare();
        this.currency = position.getCurrency();
        this.totalInvestedAmount = position.getTotalInvestedAmount();
        this.totalTransactionFees = position.getTotalTransactionFees();
        this.firstPurchaseDate = position.getFirstPurchaseDate();
        this.unrealizedGainLoss = position.getUnrealizedGainLoss();
        this.totalMarketValue = position.getTotalMarketValue();
        this.latestMarketPrice = position.getLatestMarketPrice();
        this.lastEventAppliedAt = position.getLastEventAppliedAt();
        this.exchange = position.getExchange();
        this.country = position.getCountry();

        // Synchronize transactions collection
        List<UUID> domainTransactionIds = position.getTransactions();

        // Remove transactions that are no longer in the domain model
        this.transactions.removeIf(txEntity ->
                !domainTransactionIds.contains(txEntity.getTransactionId())
        );

        // Add new transactions from domain model that aren't in the entity yet
        List<UUID> existingTransactionIds = this.transactions.stream()
                .map(PositionTransactionEntity::getTransactionId)
                .toList();

        for (UUID transactionId : domainTransactionIds) {
            if (!existingTransactionIds.contains(transactionId)) {
                PositionTransactionEntity newTx = new PositionTransactionEntity(this, transactionId);
                this.transactions.add(newTx);
            }
        }
    }
}