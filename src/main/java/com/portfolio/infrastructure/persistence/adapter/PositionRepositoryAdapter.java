package com.portfolio.infrastructure.persistence.adapter;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
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
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class PositionRepositoryAdapter implements PositionRepository {

    public static final String POSTGRES_UNIQUE_VIOLATION = "23505";
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
        return Uni.createFrom().item(() -> positionEntityMapper.toEntity(position))
                .flatMap(panacheRepository::save)
                .onFailure(this::isDuplicatePositionUniqueViolation)
                .transform(t -> {
                    log.warn("Position with ticker {} already exists", position.getTicker());
                    return new ServiceException(Errors.ProcessTransactionEvent.DUPLICATED_POSITION, "Position already exists", t);
                })
                .onFailure(this::isDuplicateTransactionUniqueViolation)
                .transform(t -> {
                    log.warn("Transaction for position with ticker {} already exists", position.getTicker());
                    return new ServiceException(Errors.ProcessTransactionEvent.ALREADY_PROCESSED, "Transaction already processed", t);
                })
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
                                    return session.merge(positionEntity)
                                            .call(session::flush);
                                })))
                .onFailure(this::isDuplicateTransactionUniqueViolation)
                .transform(t -> {
                    log.warn("Transaction for position with ticker {} already exists", position.getTicker());
                    return new ServiceException(Errors.ProcessTransactionEvent.ALREADY_PROCESSED, "Transaction already processed", t);
                })
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

    private boolean isDuplicatePositionUniqueViolation(Throwable t) {
        ConstraintViolationException cve = findConstraintViolation(t);
        if (cve == null) return false;
        if (!POSTGRES_UNIQUE_VIOLATION.equals(cve.getSQLState())) return false;
        String constraint = cve.getConstraintName();
        String detail = cve.getMessage();
        boolean constraintMatch = constraint != null && constraint.toLowerCase().contains("ticker");
        boolean detailMatch = detail != null && detail.toLowerCase().contains("ticker");
        return constraintMatch || detailMatch;
    }

    private boolean isDuplicateTransactionUniqueViolation(Throwable t) {
        ConstraintViolationException cve = findConstraintViolation(t);
        if (cve == null) return false;
        if (!POSTGRES_UNIQUE_VIOLATION.equals(cve.getSQLState())) return false;
        String constraint = cve.getConstraintName();
        String detail = cve.getMessage();
        boolean constraintMatch = constraint != null && constraint.toLowerCase().contains("transaction");
        boolean detailMatch = detail != null && detail.toLowerCase().contains("transaction_id");
        return constraintMatch || detailMatch;
    }

    private ConstraintViolationException findConstraintViolation(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof ConstraintViolationException cve) return cve;
            cur = cur.getCause();
        }
        return null;
    }
} 