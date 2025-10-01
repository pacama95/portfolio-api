package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionDeletedUseCase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
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

        return positionRepository.findByTicker(command.ticker())
                .flatMap(existingPosition -> {
                    if (existingPosition == null) {
                        log.warn("Position not found for deleted transaction: ticker={}, cannot rollback", command.ticker());
                        return Uni.createFrom().item(new Result.Ignored(
                                "Position not found for ticker, nothing to rollback"
                        ));
                    }

                    // Check if event is out of order
                    if (existingPosition.shouldIgnoreEvent(command.occurredAt())) {
                        log.info("Ignoring out-of-order deleted event for ticker={}, eventTime={}, lastAppliedTime={}",
                                command.ticker(), command.occurredAt(), existingPosition.getLastEventAppliedAt());
                        return Uni.createFrom().item(new Result.Ignored(
                                "Event is older than last applied event"
                        ));
                    }

                    // Rollback: reverse the transaction to undo its effects
                    log.info("Reversing transaction: type={}, quantity={}, price={}, fees={} for ticker={}",
                            command.transactionType(), command.quantity(), command.price(), command.fees(), command.ticker());
                    
                    existingPosition.reverseTransaction(
                            command.transactionType(),
                            command.quantity(),
                            command.price(),
                            command.fees()
                    );
                    existingPosition.updateLastEventAppliedAt(command.occurredAt());

                    log.info("Transaction reversed successfully. Position {} now has {} shares, invested amount: {}",
                            command.ticker(), existingPosition.getSharesOwned(), existingPosition.getTotalInvestedAmount());

                    return positionRepository.update(existingPosition).map(saved -> (Result) new Result.Success(saved));
                })
                .onFailure().recoverWithItem(throwable -> {
                    log.error("Error processing transaction deleted event: {}", command.transactionId(), throwable);
                    return (Result) new Result.Error(
                            Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                            "Failed to process transaction deleted event: " + throwable.getMessage()
                    );
                });
    }
}
