package com.portfolio.infrastructure.persistence.repository;

import com.portfolio.infrastructure.persistence.entity.PositionTransactionEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PositionTransactionRepository implements PanacheRepository<PositionTransactionEntity> {

    @WithSession
    public Uni<PositionTransactionEntity> findById(UUID id) {
        return find("id = ?1", id).firstResult();
    }

    @WithSession
    public Uni<PositionTransactionEntity> findByTransactionId(UUID transactionId) {
        return find("transactionId = ?1", transactionId).firstResult();
    }

    @WithSession
    public Uni<List<PositionTransactionEntity>> findByPositionId(UUID positionId) {
        return find("position.id = ?1 ORDER BY createdAt DESC", positionId).list();
    }

    @WithSession
    public Uni<Boolean> existsByTransactionId(UUID transactionId) {
        return find("transactionId = ?1", transactionId)
                .count()
                .map(count -> count > 0);
    }

    @WithSession
    public Uni<Boolean> existsByPositionIdAndTransactionId(UUID positionId, UUID transactionId) {
        return find("position.id = ?1 AND transactionId = ?2", positionId, transactionId)
                .count()
                .map(count -> count > 0);
    }

    @WithSession
    public Uni<Long> countByPositionId(UUID positionId) {
        return find("position.id = ?1", positionId).count();
    }

    @WithTransaction
    public Uni<PositionTransactionEntity> save(PositionTransactionEntity positionTransaction) {
        return persistAndFlush(positionTransaction);
    }

    @WithTransaction
    public Uni<Void> deleteByTransactionId(UUID transactionId) {
        return delete("transactionId = ?1", transactionId)
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Void> deleteByPositionId(UUID positionId) {
        return delete("position.id = ?1", positionId)
                .replaceWithVoid();
    }
}
