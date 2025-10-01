package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionUpdatedUseCase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for processing transaction updated events
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
        TransactionData previousTx = command.previousTransaction();
        TransactionData newTx = command.newTransaction();
        
        log.info("Processing transaction updated: transactionId={}, ticker={}, occurredAt={}",
                newTx.id(), newTx.ticker(), command.occurredAt());
        log.info("Previous transaction: type={}, quantity={}, price={}",
                previousTx.transactionType(), previousTx.quantity(), previousTx.price());
        log.info("New transaction: type={}, quantity={}, price={}",
                newTx.transactionType(), newTx.quantity(), newTx.price());

        // Check if ticker is being changed (ticker correction scenario)
        if (!previousTx.ticker().equals(newTx.ticker())) {
            log.info("Ticker change detected in transaction update: {} -> {}",
                    previousTx.ticker(), newTx.ticker());
            return handleTickerChange(command, previousTx, newTx);
        }

        // Same ticker update - reverse and reapply to the same position
        return positionRepository.findByTicker(newTx.ticker())
                .flatMap(existingPosition -> {
                    if (existingPosition == null) {
                        log.warn("Position not found for updated transaction, this should not happen: ticker={}",
                                newTx.ticker());
                        return Uni.createFrom().item((Result) new Result.Error(
                                Errors.ProcessTransactionEvent.INVALID_INPUT,
                                "Position not found for transaction update"
                        ));
                    }

                    // Check if event is out of order
                    if (existingPosition.shouldIgnoreEvent(command.occurredAt())) {
                        log.info("Ignoring out-of-order updated event for ticker={}, eventTime={}, lastAppliedTime={}",
                                newTx.ticker(), command.occurredAt(), existingPosition.getLastEventAppliedAt());
                        return Uni.createFrom().item(new Result.Ignored("Event is older than last applied event"));
                    }

                    // Apply the difference: reverse previous transaction, then apply new transaction
                    log.info("Reversing previous transaction for ticker={}", previousTx.ticker());
                    existingPosition.reverseTransaction(
                            previousTx.transactionType(),
                            previousTx.quantity(),
                            previousTx.price(),
                            previousTx.fees()
                    );

                    log.info("Applying new transaction for ticker={}", newTx.ticker());
                    existingPosition.applyTransaction(
                            newTx.transactionType(),
                            newTx.quantity(),
                            newTx.price(),
                            newTx.fees()
                    );

                    existingPosition.updateLastEventAppliedAt(command.occurredAt());

                    return positionRepository.update(existingPosition).map(saved -> (Result) new Result.Success(saved));
                })
                .onFailure().recoverWithItem(throwable -> {
                    log.error("Error processing transaction updated event: {}", newTx.id(), throwable);
                    return (Result) new Result.Error(
                            Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                            "Failed to process transaction updated event: " + throwable.getMessage()
                    );
                });
    }

    /**
     * Handle ticker change scenario (e.g., correcting a wrong ticker)
     * This requires updating TWO positions:
     * 1. Remove transaction from the old (incorrect) ticker's position
     * 2. Add transaction to the new (correct) ticker's position
     */
    private Uni<Result> handleTickerChange(Command command, TransactionData previousTx, TransactionData newTx) {
        log.info("Handling ticker change from {} to {}", previousTx.ticker(), newTx.ticker());

        // Step 1: Find and update the old position (remove the transaction)
        return positionRepository.findByTicker(previousTx.ticker())
                .flatMap(oldPosition -> {
                    if (oldPosition == null) {
                        log.warn("Old position not found for ticker={}, cannot reverse transaction",
                                previousTx.ticker());
                        return Uni.createFrom().item((Result) new Result.Error(
                                Errors.ProcessTransactionEvent.INVALID_INPUT,
                                "Position not found for previous ticker: " + previousTx.ticker()
                        ));
                    }

                    // Check if event is out of order for old position
                    if (oldPosition.shouldIgnoreEvent(command.occurredAt())) {
                        log.info("Ignoring out-of-order ticker change for old ticker={}, eventTime={}, lastAppliedTime={}",
                                previousTx.ticker(), command.occurredAt(), oldPosition.getLastEventAppliedAt());
                        return Uni.createFrom().item(new Result.Ignored("Event is older than last applied event for old ticker"));
                    }

                    // Reverse the transaction from the old position
                    log.info("Reversing transaction from old ticker: {}", previousTx.ticker());
                    oldPosition.reverseTransaction(
                            previousTx.transactionType(),
                            previousTx.quantity(),
                            previousTx.price(),
                            previousTx.fees()
                    );
                    oldPosition.updateLastEventAppliedAt(command.occurredAt());

                    // Step 2: Find or create the new position and apply the transaction
                    return positionRepository.update(oldPosition)
                            .flatMap(updatedOldPosition -> positionRepository.findByTicker(newTx.ticker())
                                    .flatMap(newPosition -> {
                                        if (newPosition == null) {
                                            // Create new position if it doesn't exist
                                            log.info("Creating new position for corrected ticker: {}", newTx.ticker());
                                            Position createdPosition = new Position(
                                                    newTx.ticker(),
                                                    Currency.valueOf(newTx.currency())
                                            );
                                            createdPosition.applyTransaction(
                                                    newTx.transactionType(),
                                                    newTx.quantity(),
                                                    newTx.price(),
                                                    newTx.fees()
                                            );
                                            createdPosition.updateLastEventAppliedAt(command.occurredAt());
                                            return positionRepository.save(createdPosition)
                                                    .map(saved -> (Result) new Result.Success(saved));
                                        } else {
                                            // Update existing position
                                            log.info("Applying transaction to existing new ticker: {}", newTx.ticker());
                                            
                                            // Check if event is out of order for new position
                                            if (newPosition.shouldIgnoreEvent(command.occurredAt())) {
                                                log.info("Ignoring out-of-order ticker change for new ticker={}, eventTime={}, lastAppliedTime={}",
                                                        newTx.ticker(), command.occurredAt(), newPosition.getLastEventAppliedAt());
                                                return Uni.createFrom().item(new Result.Ignored("Event is older than last applied event for new ticker"));
                                            }
                                            
                                            newPosition.applyTransaction(
                                                    newTx.transactionType(),
                                                    newTx.quantity(),
                                                    newTx.price(),
                                                    newTx.fees()
                                            );
                                            newPosition.updateLastEventAppliedAt(command.occurredAt());
                                            return positionRepository.update(newPosition)
                                                    .map(saved -> (Result) new Result.Success(saved));
                                        }
                                    })
                            );
                })
                .onFailure().recoverWithItem(throwable -> {
                    log.error("Error handling ticker change from {} to {}: {}",
                            previousTx.ticker(), newTx.ticker(), throwable.getMessage(), throwable);
                    return (Result) new Result.Error(
                            Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                            "Failed to handle ticker change: " + throwable.getMessage()
                    );
                });
    }
}
