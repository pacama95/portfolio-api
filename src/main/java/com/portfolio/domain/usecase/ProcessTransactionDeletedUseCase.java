package com.portfolio.domain.usecase;

import com.portfolio.domain.model.Position;
import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Use case for processing transaction deleted events with rollback capability
 */
public interface ProcessTransactionDeletedUseCase {

    /**
     * Process a transaction deleted event by reversing the transaction
     */
    Uni<Result> execute(Command command);

    /**
     * Result of processing a transaction deleted event
     */
    sealed interface Result {
        record Success(Position position) implements Result {}
        record Ignored(String reason) implements Result {}
        record Error(com.portfolio.domain.exception.Error error, String message) implements Result {}
    }

    /**
     * Command for transaction deleted event with full transaction details for rollback
     */
    record Command(
        UUID transactionId,
        String ticker,
        String transactionType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fees,
        String currency,
        LocalDate transactionDate,
        Instant occurredAt
    ) {}
}
