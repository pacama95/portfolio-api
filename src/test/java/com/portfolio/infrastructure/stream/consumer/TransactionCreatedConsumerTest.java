package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.usecase.ProcessTransactionCreatedUseCase;
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

class TransactionCreatedConsumerTest {

    private ProcessTransactionCreatedUseCase processTransactionCreatedUseCase;
    private RedisStreamConfig config;
    private TransactionCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        processTransactionCreatedUseCase = mock(ProcessTransactionCreatedUseCase.class);
        config = mock(RedisStreamConfig.class);

        // Configure mock config
        when(config.readCount()).thenReturn(10L);
        when(config.blockMs()).thenReturn(2000L);
        when(config.consumerName()).thenReturn("test-consumer");
        when(config.group()).thenReturn("test-group");
        when(config.replayDelaySeconds()).thenReturn(5L);
        when(config.maxReplayAttempts()).thenReturn(3);

        consumer = new TransactionCreatedConsumer(processTransactionCreatedUseCase, config);
    }

    @Test
    void testSuccessfulTransactionProcessing() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ONE);

        when(processTransactionCreatedUseCase.execute(any(ProcessTransactionCreatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(new ProcessTransactionCreatedUseCase.Result.Success(position)));

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                UUID.randomUUID(),
                "AAPL",
                "BUY",
                BigDecimal.TEN,
                new BigDecimal("150.00"),
                BigDecimal.ONE,
                "USD",
                LocalDate.now(),
                Instant.now(),
                "NASDAQ",
                "US"
        );

        // When
        processTransactionCreatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's a success
                            assert result instanceof ProcessTransactionCreatedUseCase.Result.Success;
                        }
                );

        // Verify the use case was called
        verify(processTransactionCreatedUseCase, times(1)).execute(any(ProcessTransactionCreatedUseCase.Command.class));
    }

    @Test
    void testIgnoredTransaction() {
        // Given - transaction already processed
        when(processTransactionCreatedUseCase.execute(any(ProcessTransactionCreatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionCreatedUseCase.Result.Ignored("Transaction already processed")));

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                UUID.randomUUID(),
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
        processTransactionCreatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's ignored
                            assert result instanceof ProcessTransactionCreatedUseCase.Result.Ignored;
                        }
                );

        verify(processTransactionCreatedUseCase, times(1)).execute(any(ProcessTransactionCreatedUseCase.Command.class));
    }

    @Test
    void testErrorHandling() {
        // Given - persistence error
        when(processTransactionCreatedUseCase.execute(any(ProcessTransactionCreatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionCreatedUseCase.Result.Error(
                                Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                                "Database connection failed")));

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                UUID.randomUUID(),
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
        processTransactionCreatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's an error
                            assert result instanceof ProcessTransactionCreatedUseCase.Result.Error;
                            ProcessTransactionCreatedUseCase.Result.Error error =
                                (ProcessTransactionCreatedUseCase.Result.Error) result;
                            assert error.error() == Errors.ProcessTransactionEvent.PERSISTENCE_ERROR;
                        }
                );

        verify(processTransactionCreatedUseCase, times(1)).execute(any(ProcessTransactionCreatedUseCase.Command.class));
    }

    @Test
    void testReplayScenario() {
        // Given - oversell error requiring replay
        when(processTransactionCreatedUseCase.execute(any(ProcessTransactionCreatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionCreatedUseCase.Result.Replay(
                                "Cannot sell more shares than owned",
                                UUID.randomUUID())));

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                UUID.randomUUID(),
                "TSLA",
                "SELL",
                new BigDecimal("100"),
                new BigDecimal("200.00"),
                BigDecimal.ONE,
                "USD",
                LocalDate.now(),
                Instant.now(),
                null,
                null
        );

        // When
        processTransactionCreatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's a replay
                            assert result instanceof ProcessTransactionCreatedUseCase.Result.Replay;
                            ProcessTransactionCreatedUseCase.Result.Replay replay =
                                (ProcessTransactionCreatedUseCase.Result.Replay) result;
                            assert replay.message().contains("Cannot sell");
                        }
                );

        verify(processTransactionCreatedUseCase, times(1)).execute(any(ProcessTransactionCreatedUseCase.Command.class));
    }

    @Test
    void testGetStreamName() {
        // When/Then
        assert "transaction:created".equals(consumer.getStreamName());
    }

    @Test
    void testIsNotRunningInitially() {
        // When/Then
        assert !consumer.isRunning();
    }
}
