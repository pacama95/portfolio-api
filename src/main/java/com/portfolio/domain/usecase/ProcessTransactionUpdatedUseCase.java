package com.portfolio.domain.usecase;

import com.portfolio.domain.model.Position;
import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Use case for processing transaction updated events
 */
public interface ProcessTransactionUpdatedUseCase {

    /**
     * Process a transaction updated event
     */
    Uni<Result> execute(Command command);

    /**
     * Result of processing a transaction updated event
     */
    sealed interface Result {
        record Success(Position position) implements Result {}
        record Ignored(String reason) implements Result {}
        record Error(com.portfolio.domain.exception.Error error, String message) implements Result {}
    }

    /**
     * Command for transaction updated event containing both previous and new transaction data
     */
    record Command(
        TransactionData previousTransaction,
        TransactionData newTransaction,
        Instant occurredAt
    ) {}

    /**
     * Transaction data representing a single transaction
     */
    record TransactionData(
        UUID id,
        String ticker,
        String transactionType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fees,
        String currency,
        LocalDate transactionDate,
        String exchange,
        String country
    ) {}
}
