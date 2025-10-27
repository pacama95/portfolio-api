package com.portfolio.domain.port;

import com.portfolio.domain.model.Position;
import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PositionRepository {

    Uni<Position> findById(UUID id);

    Uni<Position> findByTicker(String ticker);

    Uni<Position> findByTickerForUpdate(String ticker);

    Uni<List<Position>> findAllWithShares();

    Uni<List<Position>> findAll();

    Uni<Position> updateMarketPrice(String ticker, BigDecimal newPrice);

    Uni<Position> save(Position position);

    Uni<Position> updatePositionWithTransactions(Position position);

    Uni<Boolean> existsByTicker(String ticker);

    Uni<Long> countAll();

    Uni<Long> countWithShares();

    Uni<Boolean> isTransactionProcessed(UUID positionId, UUID transactionId);
} 