package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionDeletedUseCase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for processing transaction deleted events with rollback capability
 * Reverses the transaction to restore the position to its previous state
 */
@ApplicationScoped
public class ProcessTransactionDeletedService implements ProcessTransactionDeletedUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessTransactionDeletedService.class);

    private final PositionRepository positionRepository;

    public ProcessTransactionDeletedService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Override
    @WithTransaction
    public Uni<Result> execute(Command command) {
        log.info("Processing transaction deleted (rollback): transactionId={}, ticker={}, type={}, quantity={}, occurredAt={}",
                command.transactionId(), command.ticker(), command.transactionType(), command.quantity(), command.occurredAt());

        return Uni.createFrom().deferred(() -> deletePosition(command))
                .onFailure(PersistenceException.class).retry().atMost(3)
                .onFailure().recoverWithItem(throwable -> {
                    log.error("Error processing transaction deleted event: {}", command.transactionId(), throwable);
                    return new Result.Error(
                            Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                            "Failed to process transaction deleted event: " + throwable.getMessage()
                    );
                });
    }

    private Uni<Result> deletePosition(Command command) {
        return positionRepository.findByTickerForUpdate(command.ticker())
                .flatMap(position -> {
                    if (position == null) {
                        return Uni.createFrom().item(
                                () -> new Result.Replay("Position not found for ticker", command.transactionId(), null));
                    }

                    return isProcessedTransaction(command, position)
                            .flatMap(processed -> {
                                if (processed) {
                                    return processTransaction(position, command);
                                }
                                log.info("The transaction does not exist for this position (yet): transactionId={}, ticker={}",
                                        command.transactionId(), command.ticker());

                                return Uni.createFrom().item(
                                        () -> new Result.Replay("Transaction has not been processed yet", command.transactionId(), position.getId()));
                            });
                });
    }

    private Uni<Boolean> isProcessedTransaction(Command command, Position position) {
        return position != null ? positionRepository.isTransactionProcessed(position.getId(), command.transactionId()) : Uni.createFrom().item(false);
    }

    private Uni<Result> processTransaction(Position existingPosition, Command command) {
        return applyTransaction(existingPosition, command)
                .flatMap(position -> positionRepository.updatePositionWithTransactions(position).map(saved -> (Result) new Result.Success(saved)))
                .onFailure(this::isReplayableException).recoverWithItem(throwable -> new Result.Replay(
                        "Cannot reverse sell transaction - average cost is zero. Waiting for buy transactions to be reversed first.",
                        command.transactionId(),
                        existingPosition.getId()
                ))
                .onFailure().recoverWithItem(throwable -> new Result.Error(
                        Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                        "Failed to process transaction deleted event: " + throwable.getMessage()));
    }

    private Uni<Position> applyTransaction(Position existingPosition, Command command) {
        return Uni.createFrom().item(() -> {
            log.info("Reversing transaction: type={}, quantity={}, price={}, fees={} for ticker={}",
                    command.transactionType(), command.quantity(), command.price(), command.fees(), command.ticker());

            try {
                existingPosition.reverseTransaction(
                        command.transactionId(),
                        command.transactionType(),
                        command.quantity(),
                        command.price(),
                        command.fees()
                );
                existingPosition.updateLastEventAppliedAt(command.occurredAt());

                log.info("Transaction reversed successfully. Position {} now has {} shares, invested amount: {}",
                        command.ticker(), existingPosition.getSharesOwned(), existingPosition.getTotalInvestedAmount());

                return existingPosition;
            } catch (ServiceException e) {
                if (e.getError() == Errors.Position.INVALID_TRANSACTION_REVERSAL) {
                    log.warn("Cannot reverse transaction yet - buy transactions may not be processed: {}", e.getMessage());
                    throw e; // Re-throw to be caught by processTransaction
                }
                throw e;
            }
        });
    }

    private boolean isReplayableException(Throwable throwable) {
        return throwable instanceof ServiceException serviceException
                && serviceException.getError() == Errors.Position.INVALID_TRANSACTION_REVERSAL;
    }
}
