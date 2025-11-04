package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.usecase.ProcessTransactionUpdatedUseCase;
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

class TransactionUpdatedConsumerTest {

    private ProcessTransactionUpdatedUseCase processTransactionUpdatedUseCase;
    private RedisStreamConfig config;
    private TransactionUpdatedConsumer consumer;

    @BeforeEach
    void setUp() {
        processTransactionUpdatedUseCase = mock(ProcessTransactionUpdatedUseCase.class);
        config = mock(RedisStreamConfig.class);

        // Configure mock config
        when(config.readCount()).thenReturn(10L);
        when(config.blockMs()).thenReturn(2000L);
        when(config.consumerName()).thenReturn("test-consumer");
        when(config.group()).thenReturn("test-group");
        when(config.replayDelaySeconds()).thenReturn(5L);
        when(config.maxReplayAttempts()).thenReturn(3);

        consumer = new TransactionUpdatedConsumer(processTransactionUpdatedUseCase, config);
    }

    @Test
    void testSuccessfulTransactionUpdate_SameTicker() {
        // Given - successful update
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(new BigDecimal("15"), new BigDecimal("150.00"), BigDecimal.ONE);

        when(processTransactionUpdatedUseCase.execute(any(ProcessTransactionUpdatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(new ProcessTransactionUpdatedUseCase.Result.Success(position)));

        ProcessTransactionUpdatedUseCase.Command command = new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "AAPL",
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("150.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "AAPL",
                        "BUY",
                        new BigDecimal("15"),
                        new BigDecimal("150.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                Instant.now()
        );

        // When
        processTransactionUpdatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's a success
                            assert result instanceof ProcessTransactionUpdatedUseCase.Result.Success;
                            ProcessTransactionUpdatedUseCase.Result.Success success =
                                (ProcessTransactionUpdatedUseCase.Result.Success) result;
                            assert success.position().getSharesOwned().compareTo(new BigDecimal("15")) == 0;
                        }
                );

        verify(processTransactionUpdatedUseCase, times(1)).execute(any(ProcessTransactionUpdatedUseCase.Command.class));
    }

    @Test
    void testSuccessfulTransactionUpdate_TickerChange() {
        // Given - ticker correction from APPL to AAPL
        Position newPosition = new Position("AAPL", Currency.USD);
        newPosition.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ONE);

        when(processTransactionUpdatedUseCase.execute(any(ProcessTransactionUpdatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(new ProcessTransactionUpdatedUseCase.Result.Success(newPosition)));

        ProcessTransactionUpdatedUseCase.Command command = new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "APPL",  // Wrong ticker
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("150.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "AAPL",  // Correct ticker
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("150.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                Instant.now()
        );

        // When
        processTransactionUpdatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify success with correct ticker
                            assert result instanceof ProcessTransactionUpdatedUseCase.Result.Success;
                            ProcessTransactionUpdatedUseCase.Result.Success success =
                                (ProcessTransactionUpdatedUseCase.Result.Success) result;
                            assert "AAPL".equals(success.position().getTicker());
                        }
                );

        verify(processTransactionUpdatedUseCase, times(1)).execute(any(ProcessTransactionUpdatedUseCase.Command.class));
    }

    @Test
    void testPositionNotFound_ReturnsIgnored() {
        // Given - position doesn't exist
        when(processTransactionUpdatedUseCase.execute(any(ProcessTransactionUpdatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionUpdatedUseCase.Result.Ignored("Position not found for ticker")));

        ProcessTransactionUpdatedUseCase.Command command = new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "MSFT",
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("300.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "MSFT",
                        "BUY",
                        new BigDecimal("15"),
                        new BigDecimal("300.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                Instant.now()
        );

        // When
        processTransactionUpdatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's ignored
                            assert result instanceof ProcessTransactionUpdatedUseCase.Result.Ignored;
                            ProcessTransactionUpdatedUseCase.Result.Ignored ignored =
                                (ProcessTransactionUpdatedUseCase.Result.Ignored) result;
                            assert ignored.reason().contains("Position not found");
                        }
                );

        verify(processTransactionUpdatedUseCase, times(1)).execute(any(ProcessTransactionUpdatedUseCase.Command.class));
    }

    @Test
    void testOutOfOrderEvent_ReturnsIgnored() {
        // Given - out-of-order event
        when(processTransactionUpdatedUseCase.execute(any(ProcessTransactionUpdatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionUpdatedUseCase.Result.Ignored("Out-of-order event")));

        ProcessTransactionUpdatedUseCase.Command command = new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "GOOGL",
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("140.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "GOOGL",
                        "BUY",
                        new BigDecimal("15"),
                        new BigDecimal("140.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                Instant.now().minusSeconds(300)
        );

        // When
        processTransactionUpdatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's ignored
                            assert result instanceof ProcessTransactionUpdatedUseCase.Result.Ignored;
                            ProcessTransactionUpdatedUseCase.Result.Ignored ignored =
                                (ProcessTransactionUpdatedUseCase.Result.Ignored) result;
                            assert ignored.reason().contains("Out-of-order");
                        }
                );

        verify(processTransactionUpdatedUseCase, times(1)).execute(any(ProcessTransactionUpdatedUseCase.Command.class));
    }

    @Test
    void testPersistenceError() {
        // Given - database error
        when(processTransactionUpdatedUseCase.execute(any(ProcessTransactionUpdatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionUpdatedUseCase.Result.Error(
                                Errors.ProcessTransactionEvent.PERSISTENCE_ERROR,
                                "Database connection failed")));

        ProcessTransactionUpdatedUseCase.Command command = new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "TSLA",
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("200.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "TSLA",
                        "BUY",
                        new BigDecimal("15"),
                        new BigDecimal("200.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                Instant.now()
        );

        // When
        processTransactionUpdatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's an error
                            assert result instanceof ProcessTransactionUpdatedUseCase.Result.Error;
                            ProcessTransactionUpdatedUseCase.Result.Error error =
                                (ProcessTransactionUpdatedUseCase.Result.Error) result;
                            assert error.error() == Errors.ProcessTransactionEvent.PERSISTENCE_ERROR;
                        }
                );

        verify(processTransactionUpdatedUseCase, times(1)).execute(any(ProcessTransactionUpdatedUseCase.Command.class));
    }

    @Test
    void testTickerChangeError_OldPositionNotFound() {
        // Given - old position doesn't exist when attempting ticker change
        when(processTransactionUpdatedUseCase.execute(any(ProcessTransactionUpdatedUseCase.Command.class)))
                .thenReturn(Uni.createFrom().item(
                        new ProcessTransactionUpdatedUseCase.Result.Error(
                                Errors.ProcessTransactionEvent.INVALID_INPUT,
                                "Old position not found for ticker: INVALID")));

        ProcessTransactionUpdatedUseCase.Command command = new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "INVALID",
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("100.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        UUID.randomUUID(),
                        "VALID",
                        "BUY",
                        BigDecimal.TEN,
                        new BigDecimal("100.00"),
                        BigDecimal.ONE,
                        "USD",
                        LocalDate.now(),
                        null,
                        null
                ),
                Instant.now()
        );

        // When
        processTransactionUpdatedUseCase.execute(command)
                .subscribe().with(
                        result -> {
                            // Then - verify it's an error
                            assert result instanceof ProcessTransactionUpdatedUseCase.Result.Error;
                            ProcessTransactionUpdatedUseCase.Result.Error error =
                                (ProcessTransactionUpdatedUseCase.Result.Error) result;
                            assert error.message().contains("Old position not found");
                        }
                );

        verify(processTransactionUpdatedUseCase, times(1)).execute(any(ProcessTransactionUpdatedUseCase.Command.class));
    }

    @Test
    void testGetStreamName() {
        // When/Then
        assert "transaction:updated".equals(consumer.getStreamName());
    }

    @Test
    void testIsNotRunningInitially() {
        // When/Then
        assert !consumer.isRunning();
    }
}
