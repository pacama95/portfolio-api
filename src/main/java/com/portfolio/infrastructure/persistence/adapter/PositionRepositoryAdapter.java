package com.portfolio.infrastructure.persistence.adapter;

import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import com.portfolio.infrastructure.persistence.mapper.PositionEntityMapper;
import com.portfolio.infrastructure.persistence.repository.PositionPanacheRepository;
import com.portfolio.infrastructure.persistence.repository.PositionTransactionRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PositionRepositoryAdapter implements PositionRepository {

    private final PositionPanacheRepository panacheRepository;
    private final PositionTransactionRepository positionTransactionPanacheRepository;
    private final PositionEntityMapper positionEntityMapper;

    public PositionRepositoryAdapter(PositionPanacheRepository panacheRepository,
                                     PositionTransactionRepository positionTransactionPanacheRepository,
                                     PositionEntityMapper positionEntityMapper) {
        this.panacheRepository = panacheRepository;
        this.positionTransactionPanacheRepository = positionTransactionPanacheRepository;
        this.positionEntityMapper = positionEntityMapper;
    }

    @Override
    @WithSession
    public Uni<Position> findById(UUID id) {
        return panacheRepository.findById(id)
                .map(entity -> entity != null ? positionEntityMapper.toDomain(entity) : null);
    }

    @Override
    @WithSession
    public Uni<Position> findByTicker(String ticker) {
        return panacheRepository.findByTicker(ticker)
                .map(entity -> entity != null ? positionEntityMapper.toDomain(entity) : null);
    }

    @Override
    @WithSession
    public Uni<Position> findByTickerForUpdate(String ticker) {
        return panacheRepository.findByTickerWithTransactions(ticker)
                .map(entity -> entity != null ? positionEntityMapper.toDomainWithTransactions(entity) : null);
    }

    @Override
    @WithSession
    public Uni<List<Position>> findAllWithShares() {
        return panacheRepository.findAllWithShares()
                .map(entities -> entities.stream()
                        .map(positionEntityMapper::toDomain)
                        .toList());
    }

    @Override
    @WithSession
    public Uni<List<Position>> findAll() {
        return panacheRepository.findAllActive()
                .map(entities -> entities.stream()
                        .map(positionEntityMapper::toDomain)
                        .toList());
    }

    @Override
    @WithTransaction
    public Uni<Position> updateMarketPrice(String ticker, BigDecimal newPrice) {
        return panacheRepository.updateMarketPrice(ticker, newPrice)
                .map(entity -> entity != null ? positionEntityMapper.toDomain(entity) : null);
    }

    @Override
    @WithTransaction
    public Uni<Position> save(Position position) {
        PositionEntity entity = positionEntityMapper.toEntity(position);
        return panacheRepository.save(entity)
                .map(positionEntityMapper::toDomain);
    }

    @Override
    @WithTransaction
    public Uni<Position> update(Position position) {
        return Uni.createFrom().item(() -> positionEntityMapper.toEntity(position))
                .flatMap(positionEntity -> panacheRepository
                        .getSession()
                        .flatMap(session -> session.merge(positionEntity)))
                .flatMap(panacheRepository::persistAndFlush)
                .map(positionEntityMapper::toDomain);
    }

    @Override
    @WithTransaction
    public Uni<Position> updatePositionWithTransactions(Position position) {
        return panacheRepository.getSession()
                .flatMap(session -> session.find(PositionEntity.class, position.getId())
                        .call(positionEntity -> session.fetch(positionEntity.getTransactions())
                                .onItem().transformToUni(positionTransactionEntities -> {
                                    positionEntity.update(position);
                                    return session.merge(positionEntity);
                                })))
                .map(positionEntityMapper::toDomainWithTransactions);

    }

    @Override
    @WithSession
    public Uni<Boolean> existsByTicker(String ticker) {
        return panacheRepository.existsByTicker(ticker);
    }

    @Override
    @WithSession
    public Uni<Long> countAll() {
        return panacheRepository.count();
    }

    @Override
    @WithSession
    public Uni<Long> countWithShares() {
        return panacheRepository.countWithShares();
    }

    @Override
    @WithSession
    public Uni<Boolean> isTransactionProcessed(UUID positionId, UUID transactionId) {
        return positionTransactionPanacheRepository.existsByPositionIdAndTransactionId(positionId, transactionId);
    }
} 