package com.portfolio.domain.usecase;

import com.portfolio.domain.exception.Error;
import com.portfolio.domain.model.Position;
import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Use case for processing transaction created events
 */
public interface ProcessTransactionCreatedUseCase {

    /**
     * Process a transaction created event
     */
    Uni<Result> execute(Command command);

    /**
     * Result of processing a transaction created event
     */
    sealed interface Result {
        record Success(Position position) implements Result {}
        record Ignored(String reason) implements Result {}
        record Error(com.portfolio.domain.exception.Error error, String message) implements Result {}
    }

    /**
     * Command for transaction created event
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
