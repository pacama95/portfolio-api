package com.portfolio.infrastructure.persistence.repository;

import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Panache reactive repository for PositionEntity
 */
@ApplicationScoped
public class PositionPanacheRepository implements PanacheRepository<PositionEntity> {

    @WithSession
    public Uni<PositionEntity> findById(UUID id) {
        return find("id = ?1", id).firstResult();
    }

    @WithSession
    public Uni<PositionEntity> findByTicker(String ticker) {
        return find("ticker = ?1", ticker).firstResult();
    }

    @WithSession
    public Uni<List<PositionEntity>> findAllWithShares() {
        return find("sharesOwned > 0 ORDER BY ticker").list();
    }

    @WithSession
    public Uni<List<PositionEntity>> findAllActive() {
        return find("ORDER BY ticker").list();
    }

    @WithSession
    public Uni<Boolean> existsByTicker(String ticker) {
        return find("ticker = ?1", ticker)
                .count()
                .map(count -> count > 0);
    }

    @WithSession
    public Uni<Long> countWithShares() {
        return find("sharesOwned > 0").count();
    }

    @WithTransaction
    public Uni<PositionEntity> updateMarketPrice(String ticker, BigDecimal newPrice) {
        return findByTicker(ticker)
                .flatMap(position -> {
                    if (position == null) {
                        return Uni.createFrom().nullItem();
                    }

                    position.setLatestMarketPrice(newPrice);
                    position.setMarketPriceLastUpdated(OffsetDateTime.now());

                    // Recalculate market value and unrealized gain/loss
                    BigDecimal marketValue = position.getSharesOwned().multiply(newPrice);
                    position.setTotalMarketValue(marketValue);

                    BigDecimal costBasis = position.getTotalInvestedAmount();
                    position.setUnrealizedGainLoss(marketValue.subtract(costBasis));

                    return persistAndFlush(position);
                });
    }

    @WithTransaction
    public Uni<PositionEntity> save(PositionEntity position) {
        return persistAndFlush(position);
    }

    public Uni<PositionEntity> update(PositionEntity position) {
        // Calculate derived fields
        if (position.getLatestMarketPrice() != null && position.getSharesOwned() != null) {
            BigDecimal marketValue = position.getSharesOwned().multiply(position.getLatestMarketPrice());
            position.setTotalMarketValue(marketValue);
            position.setUnrealizedGainLoss(marketValue.subtract(position.getTotalInvestedAmount()));
        }
        return persistAndFlush(position);
    }
} 