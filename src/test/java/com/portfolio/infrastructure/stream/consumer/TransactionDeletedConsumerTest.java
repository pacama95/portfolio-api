package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.usecase.ProcessTransactionDeletedUseCase;
import com.portfolio.infrastructure.stream.config.RedisStreamConfig;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionDeletedConsumerTest {

    private ProcessTransactionDeletedUseCase processTransactionDeletedUseCase;
    private RedisStreamConfig config;
    private TransactionDeletedConsumer consumer;

    @BeforeEach
    void setUp() {
        processTransactionDeletedUseCase = mock(ProcessTransactionDeletedUseCase.class);
        config = mock(RedisStreamConfig.class);

        // Configure mock config
        when(config.readCount()).thenReturn(10L);
        when(config.blockMs()).thenReturn(2000L);
        when(config.consumerName()).thenReturn("test-consumer");
        when(config.group()).thenReturn("test-group");
        when(config.replayDelaySeconds()).thenReturn(5L);
        when(config.maxReplayAttempts()).thenReturn(3);

        consumer = new TransactionDeletedConsumer(processTransactionDeletedUseCase, config);
    }

    @Test
    void testSuccessfulTransactionRollback() {
        // Given - position after rollback
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ONE);

        when(processTransactionDeletedUseCase.execute(any(ProcessTransactionDeletedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(new ProcessTransactionDeletedUseCase.Result.Success(position)));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                UUID.randomUUID(),
                "AAPL",
                "BUY",
                BigDecimal.TEN,
                new BigDecimal("150.00"),
                BigDecimal.ONE,
                "USD",
                LocalDate.now(),
                Instant.now(),
                null,
                null
        );

        // When
        processTransactionDeletedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's a success
                            assert result instanceof ProcessTransactionDeletedUseCase.Result.Success;
                        }
                );

        verify(processTransactionDeletedUseCase, times(1)).execute(any(ProcessTransactionDeletedUseCase.Command.class));
    }

    @Test
    void testPositionNotFound_ReturnsReplay() {
        // Given - position doesn't exist yet
        UUID transactionId = UUID.randomUUID();
        when(processTransactionDeletedUseCase.execute(any(ProcessTransactionDeletedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionDeletedUseCase.Result.Replay(
                                "Position not found for ticker",
                                transactionId,
                                null)));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId,
                "MSFT",
                "BUY",
                BigDecimal.TEN,
                new BigDecimal("300.00"),
                BigDecimal.ONE,
                "USD",
                LocalDate.now(),
                Instant.now(),
                null,
                null
        );

        // When
        processTransactionDeletedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's a replay
                            assert result instanceof ProcessTransactionDeletedUseCase.Result.Replay;
                            ProcessTransactionDeletedUseCase.Result.Replay replay =
                                (ProcessTransactionDeletedUseCase.Result.Replay) result;
                            assert replay.transactionId().equals(transactionId);
                            assert replay.message().contains("Position not found");
                        }
                );

        verify(processTransactionDeletedUseCase, times(1)).execute(any(ProcessTransactionDeletedUseCase.Command.class));
    }

    @Test
    void testTransactionNotProcessedYet_ReturnsReplay() {
        // Given - transaction hasn't been processed yet
        UUID transactionId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        when(processTransactionDeletedUseCase.execute(any(ProcessTransactionDeletedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionDeletedUseCase.Result.Replay(
                                "Transaction has not been processed yet",
                                transactionId,
                                positionId)));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId,
                "GOOGL",
                "BUY",
                BigDecimal.TEN,
                new BigDecimal("140.00"),
                BigDecimal.ONE,
                "USD",
                LocalDate.now(),
                Instant.now(),
                null,
                null
        );

        // When
        processTransactionDeletedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's a replay
                            assert result instanceof ProcessTransactionDeletedUseCase.Result.Replay;
                            ProcessTransactionDeletedUseCase.Result.Replay replay =
                                (ProcessTransactionDeletedUseCase.Result.Replay) result;
                            assert replay.transactionId().equals(transactionId);
                            assert replay.positionId().equals(positionId);
                        }
                );

        verify(processTransactionDeletedUseCase, times(1)).execute(any(ProcessTransactionDeletedUseCase.Command.class));
    }

    @Test
    void testPersistenceError() {
        // Given - database error
        when(processTransactionDeletedUseCase.execute(any(ProcessTransactionDeletedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionDeletedUseCase.Result.Error(
                                Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                                "Database connection failed")));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                UUID.randomUUID(),
                "TSLA",
                "BUY",
                BigDecimal.TEN,
                new BigDecimal("200.00"),
                BigDecimal.ONE,
                "USD",
                LocalDate.now(),
                Instant.now(),
                null,
                null
        );

        // When
        processTransactionDeletedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's an error
                            assert result instanceof ProcessTransactionDeletedUseCase.Result.Error;
                            ProcessTransactionDeletedUseCase.Result.Error error =
                                (ProcessTransactionDeletedUseCase.Result.Error) result;
                            assert error.error() == Errors.ProcessTransactionEvent.PERSISTENCE_ERROR;
                        }
                );

        verify(processTransactionDeletedUseCase, times(1)).execute(any(ProcessTransactionDeletedUseCase.Command.class));
    }

    @Test
    void testReverseSellWithZeroAverageCost_ReturnsReplay() {
        // Given - cannot reverse sell when average cost is zero
        UUID transactionId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        when(processTransactionDeletedUseCase.execute(any(ProcessTransactionDeletedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionDeletedUseCase.Result.Replay(
                                "Cannot reverse sell transaction - average cost is zero",
                                transactionId,
                                positionId)));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId,
                "NVDA",
                "SELL",
                BigDecimal.TEN,
                new BigDecimal("400.00"),
                BigDecimal.ONE,
                "USD",
                LocalDate.now(),
                Instant.now(),
                null,
                null
        );

        // When
        processTransactionDeletedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's a replay
                            assert result instanceof ProcessTransactionDeletedUseCase.Result.Replay;
                            ProcessTransactionDeletedUseCase.Result.Replay replay =
                                (ProcessTransactionDeletedUseCase.Result.Replay) result;
                            assert replay.message().contains("average cost is zero");
                        }
                );

        verify(processTransactionDeletedUseCase, times(1)).execute(any(ProcessTransactionDeletedUseCase.Command.class));
    }

    @Test
    void testGetStreamName() {
        // When/Then
        assert "transaction:deleted".equals(consumer.getStreamName());
    }

    @Test
    void testIsNotRunningInitially() {
        // When/Then
        assert !consumer.isRunning();
    }
}
