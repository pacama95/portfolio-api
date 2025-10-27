package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionCreatedUseCase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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

        return Uni.createFrom().deferred(() -> upsertPosition(command))
                .onFailure(this::isDuplicatedPositionException).retry().withBackOff(Duration.ofMillis(100)).atMost(2)
                .onFailure(this::isAlreadyProcessedException).recoverWithItem(ignored -> new Result.Ignored("Transaction already processed"))
                .onFailure(this::isReplayableException).recoverWithUni(throwable -> Uni.createFrom().item(() -> new Result.Replay(throwable.getMessage(), command.transactionId())))
                .onFailure(PersistenceException.class).retry().withBackOff(Duration.ofMillis(100)).atMost(3)
                .onFailure().recoverWithItem(throwable -> {
                    log.error("Error processing transaction created event: {}", command.transactionId(), throwable);
                    return new Result.Error(
                            Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                            "Failed to process transaction created event: " + throwable.getMessage()
                    );
                });
    }

    private Uni<Result> upsertPosition(Command command) {
        return positionRepository.findByTickerForUpdate(command.ticker())
                .flatMap(position -> isProcessedTransaction(command, position)
                        .flatMap(processed -> {
                            if (processed) {
                                log.info("Transaction already processed: transactionId={}, ticker={}",
                                        command.transactionId(), command.ticker());
                                return Uni.createFrom().item(new Result.Ignored("Transaction already processed"));
                            }
                            return processTransaction(position, command);
                        }));
    }

    private Uni<Boolean> isProcessedTransaction(Command command, Position position) {
        return position != null
                ? positionRepository.isTransactionProcessed(position.getId(), command.transactionId())
                : Uni.createFrom().item(false);
    }

    private Uni<Result> processTransaction(Position existingPosition, Command command) {
        return applyTransaction(existingPosition, command)
                .flatMap(position -> {
                    Uni<Position> saveOperation = existingPosition != null
                            ? positionRepository.updatePositionWithTransactions(position)
                            : positionRepository.save(position);

                    return saveOperation.map(saved -> (Result) new Result.Success(saved));
                });
    }

    private Uni<Position> applyTransaction(Position existingPosition, Command command) {
        return Uni.createFrom().item(() -> {
            Position position = existingPosition != null ? existingPosition : createNewPosition(command);
            position.applyTransaction(command.transactionId(), command.transactionType(), command.quantity(), command.price(), command.fees());
            position.updateLastEventAppliedAt(command.occurredAt());

            // Update exchange and country if provided
            if (command.exchange() != null) {
                position.updateExchange(command.exchange());
            }
            if (command.country() != null) {
                position.updateCountry(command.country());
            }

            return position;
        });
    }

    private Position createNewPosition(Command command) {
        return new Position(command.ticker(), Currency.valueOf(command.currency()));
    }

    private boolean isReplayableException(Throwable throwable) {
        return throwable instanceof ServiceException serviceException
                && serviceException.getError() == Errors.Position.OVERSELL;
    }

    private boolean isAlreadyProcessedException(Throwable throwable) {
        return throwable instanceof ServiceException serviceException
                && serviceException.getError() == Errors.ProcessTransactionEvent.ALREADY_PROCESSED;
    }

    private boolean isDuplicatedPositionException(Throwable throwable) {
        return throwable instanceof ServiceException serviceException
                && serviceException.getError() == Errors.ProcessTransactionEvent.DUPLICATED_POSITION;
    }
}
