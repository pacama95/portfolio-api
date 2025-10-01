package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionCreatedUseCase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for processing transaction created events
 */
@ApplicationScoped
public class ProcessTransactionCreatedService implements ProcessTransactionCreatedUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessTransactionCreatedService.class);

    private final PositionRepository positionRepository;

    public ProcessTransactionCreatedService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Override
    @WithTransaction
    public Uni<Result> execute(Command command) {
        log.info("Processing transaction created: transactionId={}, ticker={}, occurredAt={}",
                command.transactionId(), command.ticker(), command.occurredAt());

        return positionRepository.findByTicker(command.ticker())
                .flatMap(existingPosition -> {
                    // Check if event is out of order
                    if (existingPosition != null && existingPosition.shouldIgnoreEvent(command.occurredAt())) {
                        log.info("Ignoring out-of-order created event for ticker={}, eventTime={}, lastAppliedTime={}",
                                command.ticker(), command.occurredAt(), existingPosition.getLastEventAppliedAt());
                        return Uni.createFrom().item(new Result.Ignored(
                                "Event is older than last applied event"
                        ));
                    }

                    // Apply transaction to position (upsert behavior)
                    Position position = existingPosition != null ? existingPosition : createNewPosition(command);
                    position.applyTransaction(command.transactionType(), command.quantity(), command.price(), command.fees());
                    position.updateLastEventAppliedAt(command.occurredAt());

                    Uni<Position> saveOperation = existingPosition != null
                            ? positionRepository.update(position)
                            : positionRepository.save(position);

                    return saveOperation.map(saved -> (Result) new Result.Success(saved));
                })
                .onFailure().recoverWithItem(throwable -> {
                    log.error("Error processing transaction created event: {}", command.transactionId(), throwable);
                    return (Result) new Result.Error(
                            Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                            "Failed to process transaction created event: " + throwable.getMessage()
                    );
                });
    }

    private Position createNewPosition(Command command) {
        return new Position(command.ticker(), Currency.valueOf(command.currency()));
    }

}
