package com.portfolio.infrastructure.persistence.adapter;

import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import com.portfolio.infrastructure.persistence.mapper.PositionEntityMapper;
import com.portfolio.infrastructure.persistence.repository.PositionPanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Adapter for PositionRepository port implementation
 */
@ApplicationScoped
public class PositionRepositoryAdapter implements PositionRepository {

    private final PositionPanacheRepository panacheRepository;
    private final PositionEntityMapper positionEntityMapper;

    public PositionRepositoryAdapter(PositionPanacheRepository panacheRepository, PositionEntityMapper positionEntityMapper) {
        this.panacheRepository = panacheRepository;
        this.positionEntityMapper = positionEntityMapper;
    }

    @Override
    public Uni<Position> findById(UUID id) {
        return panacheRepository.findById(id)
                .map(entity -> entity != null ? positionEntityMapper.toDomain(entity) : null);
    }

    @Override
    public Uni<Position> findByTicker(String ticker) {
        return panacheRepository.findByTicker(ticker)
                .map(entity -> entity != null ? positionEntityMapper.toDomain(entity) : null);
    }

    @Override
    public Uni<List<Position>> findAllWithShares() {
        return panacheRepository.findAllWithShares()
                .map(entities -> entities.stream()
                        .map(positionEntityMapper::toDomain)
                        .toList());
    }

    @Override
    public Uni<List<Position>> findAll() {
        return panacheRepository.findAllActive()
                .map(entities -> entities.stream()
                        .map(positionEntityMapper::toDomain)
                        .toList());
    }

    @Override
    public Uni<Position> updateMarketPrice(String ticker, BigDecimal newPrice) {
        return panacheRepository.updateMarketPrice(ticker, newPrice)
                .map(entity -> entity != null ? positionEntityMapper.toDomain(entity) : null);
    }

    @Override
    public Uni<Position> save(Position position) {
        PositionEntity entity = positionEntityMapper.toEntity(position);
        return panacheRepository.save(entity)
                .map(positionEntityMapper::toDomain);
    }

    @Override
    public Uni<Position> update(Position position) {
        return Uni.createFrom().item(() -> positionEntityMapper.toEntity(position))
                .flatMap(positionEntity -> panacheRepository.getSession().flatMap(session -> session.merge(positionEntity)))
                .flatMap(panacheRepository::persistAndFlush)
                .map(positionEntityMapper::toDomain);
    }

    @Override
    public Uni<Boolean> existsByTicker(String ticker) {
        return panacheRepository.existsByTicker(ticker);
    }

    @Override
    public Uni<Long> countAll() {
        return panacheRepository.count();
    }

    @Override
    public Uni<Long> countWithShares() {
        return panacheRepository.countWithShares();
    }
} 