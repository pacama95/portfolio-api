package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionUpdatedUseCase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for processing transaction updated events
 * Handles both same-ticker updates and ticker changes (corrections)
 */
@ApplicationScoped
public class ProcessTransactionUpdatedService implements ProcessTransactionUpdatedUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessTransactionUpdatedService.class);

    private final PositionRepository positionRepository;

    public ProcessTransactionUpdatedService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Override
    @WithTransaction
    public Uni<Result> execute(Command command) {
        log.info("Processing transaction updated: transactionId={}, previousTicker={}, newTicker={}, occurredAt={}",
                command.previousTransaction().id(),
                command.previousTransaction().ticker(),
                command.newTransaction().ticker(),
                command.occurredAt());

        // Determine if this is a same-ticker update or a ticker change
        boolean isSameTicker = command.previousTransaction().ticker().equals(command.newTransaction().ticker());

        return Uni.createFrom().deferred(() ->
                        isSameTicker
                                ? processSameTickerUpdate(command)
                                : processTickerChange(command)
                )
                .onFailure(PersistenceException.class).retry().atMost(3)
                .onFailure().recoverWithItem(throwable -> {
                    log.error("Error processing transaction updated event: {}", command.previousTransaction().id(), throwable);
                    return new Result.Error(
                            Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                            "Failed to process transaction updated event: " + throwable.getMessage()
                    );
                });
    }

    /**
     * Process an update where the ticker remains the same
     * (quantity, price, fees, or type changes)
     */
    private Uni<Result> processSameTickerUpdate(Command command) {
        String ticker = command.previousTransaction().ticker();

        return positionRepository.findByTickerForUpdate(ticker)
                .flatMap(position -> {
                    if (position == null) {
                        log.warn("Position not found for ticker: {}", ticker);
                        return Uni.createFrom().item(new Result.Ignored("Position not found for ticker: " + ticker));
                    }

                    // Check for out-of-order events
                    if (position.shouldIgnoreEvent(command.occurredAt())) {
                        log.info("Ignoring out-of-order event for ticker {}, occurredAt: {}, lastEventAppliedAt: {}",
                                ticker, command.occurredAt(), position.getLastEventAppliedAt());
                        return Uni.createFrom().item(new Result.Ignored("Out-of-order event"));
                    }

                    return updatePosition(position, command);
                });
    }

    /**
     * Process an update where the ticker changes (ticker correction)
     * Reverses transaction from old ticker and applies to new ticker
     */
    private Uni<Result> processTickerChange(Command command) {
        String oldTicker = command.previousTransaction().ticker();
        String newTicker = command.newTransaction().ticker();

        log.info("Processing ticker change: {} -> {}", oldTicker, newTicker);

        // First, remove transaction from old position
        return positionRepository.findByTickerForUpdate(oldTicker)
                .flatMap(oldPosition -> {
                    if (oldPosition == null) {
                        log.error("Old position not found for ticker: {}", oldTicker);
                        return Uni.createFrom().item(new Result.Error(
                                Errors.ProcessTransactionEvent.INVALID_INPUT,
                                "Old position not found for ticker: " + oldTicker
                        ));
                    }

                    // Check for out-of-order events on old position
                    if (oldPosition.shouldIgnoreEvent(command.occurredAt())) {
                        log.info("Ignoring out-of-order event for old ticker {}", oldTicker);
                        return Uni.createFrom().item(new Result.Ignored("Out-of-order event on old position"));
                    }

                    // Reverse transaction from old position
                    return reverseFromOldPosition(oldPosition, command)
                            .flatMap(savedOldPosition ->
                                    // Then add transaction to new position
                                    addToNewPosition(command)
                            );
                });
    }

    /**
     * Reverse transaction from the old ticker's position
     */
    private Uni<Position> reverseFromOldPosition(Position oldPosition, Command command) {
        return Uni.createFrom().item(() -> {
            log.info("Reversing transaction from old position: ticker={}, type={}, quantity={}",
                    oldPosition.getTicker(),
                    command.previousTransaction().transactionType(),
                    command.previousTransaction().quantity());

            TransactionData prevTxn = command.previousTransaction();
            oldPosition.reverseTransaction(
                    prevTxn.id(),
                    prevTxn.transactionType(),
                    prevTxn.quantity(),
                    prevTxn.price(),
                    prevTxn.fees()
            );
            oldPosition.updateLastEventAppliedAt(command.occurredAt());

            log.info("Reversed transaction from old position. Position {} now has {} shares",
                    oldPosition.getTicker(), oldPosition.getSharesOwned());

            return oldPosition;
        }).flatMap(position -> positionRepository.updatePositionWithTransactions(position));
    }

    /**
     * Add transaction to the new ticker's position (create if needed)
     */
    private Uni<Result> addToNewPosition(Command command) {
        String newTicker = command.newTransaction().ticker();

        return positionRepository.findByTickerForUpdate(newTicker)
                .flatMap(newPosition -> {
                    // Check for out-of-order events on existing new position
                    if (newPosition != null && newPosition.shouldIgnoreEvent(command.occurredAt())) {
                        log.info("Ignoring out-of-order event for new ticker {}", newTicker);
                        return Uni.createFrom().item(new Result.Ignored("Out-of-order event on new position"));
                    }

                    return applyToNewPosition(newPosition, command);
                });
    }

    /**
     * Apply transaction to new position
     */
    private Uni<Result> applyToNewPosition(Position existingNewPosition, Command command) {
        return Uni.createFrom().item(() -> {
            TransactionData newTxn = command.newTransaction();
            Position position = existingNewPosition != null
                    ? existingNewPosition
                    : new Position(newTxn.ticker(), Currency.valueOf(newTxn.currency()));

            log.info("Applying transaction to {} position: ticker={}, type={}, quantity={}",
                    existingNewPosition != null ? "existing" : "new",
                    newTxn.ticker(),
                    newTxn.transactionType(),
                    newTxn.quantity());

            position.applyTransaction(
                    newTxn.id(),
                    newTxn.transactionType(),
                    newTxn.quantity(),
                    newTxn.price(),
                    newTxn.fees()
            );
            position.updateLastEventAppliedAt(command.occurredAt());

            // Update exchange and country if provided
            if (newTxn.exchange() != null) {
                position.updateExchange(newTxn.exchange());
            }
            if (newTxn.country() != null) {
                position.updateCountry(newTxn.country());
            }

            log.info("Applied transaction to new position. Position {} now has {} shares",
                    position.getTicker(), position.getSharesOwned());

            return position;
        }).flatMap(position -> {
            Uni<Position> saveOperation = existingNewPosition != null
                    ? positionRepository.updatePositionWithTransactions(position)
                    : positionRepository.save(position);

            return saveOperation.map(saved -> (Result) new Result.Success(saved));
        });
    }

    /**
     * Update position with reverse + apply for same ticker updates
     */
    private Uni<Result> updatePosition(Position position, Command command) {
        return Uni.createFrom().item(() -> {
            TransactionData prevTxn = command.previousTransaction();
            TransactionData newTxn = command.newTransaction();

            log.info("Updating position {}: reversing previous transaction (type={}, qty={}, price={}, fees={})",
                    position.getTicker(),
                    prevTxn.transactionType(),
                    prevTxn.quantity(),
                    prevTxn.price(),
                    prevTxn.fees());

            // Reverse the previous transaction
            position.reverseTransaction(
                    prevTxn.id(),
                    prevTxn.transactionType(),
                    prevTxn.quantity(),
                    prevTxn.price(),
                    prevTxn.fees()
            );

            log.info("Applying new transaction (type={}, qty={}, price={}, fees={})",
                    newTxn.transactionType(),
                    newTxn.quantity(),
                    newTxn.price(),
                    newTxn.fees());

            // Apply the new transaction
            position.applyTransaction(
                    newTxn.id(),
                    newTxn.transactionType(),
                    newTxn.quantity(),
                    newTxn.price(),
                    newTxn.fees()
            );

            position.updateLastEventAppliedAt(command.occurredAt());

            // Update exchange and country if provided in new transaction
            if (newTxn.exchange() != null) {
                position.updateExchange(newTxn.exchange());
            }
            if (newTxn.country() != null) {
                position.updateCountry(newTxn.country());
            }

            log.info("Transaction updated successfully. Position {} now has {} shares, invested amount: {}",
                    position.getTicker(), position.getSharesOwned(), position.getTotalInvestedAmount());

            return position;
        }).flatMap(position ->
                positionRepository.updatePositionWithTransactions(position)
                        .map(saved -> (Result) new Result.Success(saved))
        );
    }
}
