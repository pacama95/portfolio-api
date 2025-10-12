package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionDeletedUseCase;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessTransactionDeletedService
 */
class ProcessTransactionDeletedServiceTest {

    private PositionRepository positionRepository;
    private ProcessTransactionDeletedService service;

    @BeforeEach
    void setUp() {
        positionRepository = mock(PositionRepository.class);
        service = new ProcessTransactionDeletedService(positionRepository);
    }

    @Test
    void testRollbackBuyTransaction_Success() {
        // Given: A position with 20 shares from a previous BUY
        UUID transactionId = UUID.randomUUID();
        String ticker = "AAPL";
        BigDecimal quantity = BigDecimal.TEN;
        BigDecimal price = new BigDecimal("150.00");
        BigDecimal fees = new BigDecimal("1.50");
        Instant occurredAt = Instant.now();

        Position position = new Position(ticker, Currency.USD);
        // Simulate the position already has the transaction applied
        position.applyBuy(new BigDecimal("20"), new BigDecimal("150.00"), new BigDecimal("2.00"));
        position.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTicker(ticker))
                .thenReturn(Uni.createFrom().item(position));
        when(positionRepository.isTransactionProcessed(any(), eq(transactionId)))
                .thenReturn(Uni.createFrom().item(true));
        when(positionRepository.update(any(Position.class)))
                .thenReturn(Uni.createFrom().item(position));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId, ticker, "BUY", quantity, price, fees, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionDeletedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionDeletedUseCase.Result.Success.class, result);
        ProcessTransactionDeletedUseCase.Result.Success successResult = (ProcessTransactionDeletedUseCase.Result.Success) result;

        // Verify the transaction was reversed (20 shares - 10 shares = 10 shares)
        assertEquals(BigDecimal.TEN.setScale(6), successResult.position().getSharesOwned().setScale(6));

        // Verify repository interactions
        verify(positionRepository).findByTicker(ticker);
        verify(positionRepository).isTransactionProcessed(any(), eq(transactionId));
        verify(positionRepository).update(any(Position.class));
    }

    @Test
    void testRollbackSellTransaction_Success() {
        // Given: A position that previously had a SELL transaction
        UUID transactionId = UUID.randomUUID();
        String ticker = "MSFT";
        BigDecimal quantity = new BigDecimal("5");
        BigDecimal price = new BigDecimal("300.00");
        BigDecimal fees = new BigDecimal("2.00");
        Instant occurredAt = Instant.now();

        Position position = new Position(ticker, Currency.USD);
        // Start with 20 shares, then simulate a SELL of 5 shares
        position.applyBuy(new BigDecimal("20"), new BigDecimal("280.00"), BigDecimal.ZERO);
        position.applySell(quantity, price, fees); // Now has 15 shares
        position.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTicker(ticker))
                .thenReturn(Uni.createFrom().item(position));
        when(positionRepository.isTransactionProcessed(any(), eq(transactionId)))
                .thenReturn(Uni.createFrom().item(true));
        when(positionRepository.update(any(Position.class)))
                .thenReturn(Uni.createFrom().item(position));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId, ticker, "SELL", quantity, price, fees, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionDeletedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionDeletedUseCase.Result.Success.class, result);
        ProcessTransactionDeletedUseCase.Result.Success successResult = (ProcessTransactionDeletedUseCase.Result.Success) result;

        // Verify the SELL was reversed (15 shares + 5 shares = 20 shares)
        assertEquals(new BigDecimal("20").setScale(6), successResult.position().getSharesOwned().setScale(6));

        verify(positionRepository).findByTicker(ticker);
        verify(positionRepository).isTransactionProcessed(any(), eq(transactionId));
        verify(positionRepository).update(any(Position.class));
    }

    @Test
    void testRollback_PositionNotFound_ReturnsReplay() {
        // Given: No position exists for the ticker
        UUID transactionId = UUID.randomUUID();
        String ticker = "TSLA";
        Instant occurredAt = Instant.now();

        when(positionRepository.findByTicker(ticker))
                .thenReturn(Uni.createFrom().nullItem());

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("200.00"),
                BigDecimal.ONE, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionDeletedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionDeletedUseCase.Result.Replay.class, result);
        ProcessTransactionDeletedUseCase.Result.Replay replayResult = (ProcessTransactionDeletedUseCase.Result.Replay) result;
        assertEquals("Position not found for ticker", replayResult.message());
        assertEquals(transactionId, replayResult.transactionId());
        assertNull(replayResult.positionId());

        verify(positionRepository).findByTicker(ticker);
        verify(positionRepository, never()).update(any(Position.class));
    }

    @Test
    void testRollback_TransactionNotProcessed_ReturnsReplay() {
        // Given: Transaction has not been processed yet
        UUID transactionId = UUID.randomUUID();
        String ticker = "GOOGL";
        Instant occurredAt = Instant.now();

        Position position = new Position(ticker, Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ONE);
        position.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTicker(ticker))
                .thenReturn(Uni.createFrom().item(position));
        when(positionRepository.isTransactionProcessed(any(), eq(transactionId)))
                .thenReturn(Uni.createFrom().item(false));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("150.00"),
                BigDecimal.ONE, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionDeletedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionDeletedUseCase.Result.Replay.class, result);
        ProcessTransactionDeletedUseCase.Result.Replay replayResult = (ProcessTransactionDeletedUseCase.Result.Replay) result;
        assertEquals("Transaction has not been processed yet", replayResult.message());
        assertEquals(transactionId, replayResult.transactionId());
        assertEquals(position.getId(), replayResult.positionId());

        verify(positionRepository).findByTicker(ticker);
        verify(positionRepository).isTransactionProcessed(any(), eq(transactionId));
        verify(positionRepository, never()).update(any(Position.class));
    }

    @Test
    void testRollback_PersistenceError_ReturnsError() {
        // Given: Repository update fails
        UUID transactionId = UUID.randomUUID();
        String ticker = "NVDA";
        Instant occurredAt = Instant.now();

        Position position = new Position(ticker, Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("400.00"), BigDecimal.ONE);
        position.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        RuntimeException dbException = new RuntimeException("Database connection failed");

        when(positionRepository.findByTicker(ticker))
                .thenReturn(Uni.createFrom().item(position));
        when(positionRepository.isTransactionProcessed(any(), eq(transactionId)))
                .thenReturn(Uni.createFrom().item(true));
        when(positionRepository.update(any(Position.class)))
                .thenReturn(Uni.createFrom().failure(dbException));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("400.00"),
                BigDecimal.ONE, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionDeletedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionDeletedUseCase.Result.Error.class, result);
        ProcessTransactionDeletedUseCase.Result.Error errorResult = (ProcessTransactionDeletedUseCase.Result.Error) result;
        assertEquals(Errors.ProcessTransactionEvent.PERSISTENCE_ERROR, errorResult.error());
        assertTrue(errorResult.message().contains("Failed to process transaction deleted event"));

        verify(positionRepository).findByTicker(ticker);
        verify(positionRepository).isTransactionProcessed(any(), eq(transactionId));
        verify(positionRepository).update(any(Position.class));
    }

    @Test
    void testRollback_UpdatesLastEventAppliedAt() {
        // Given: A position with a transaction to rollback
        UUID transactionId = UUID.randomUUID();
        String ticker = "AMZN";
        Instant occurredAt = Instant.now();

        Position position = new Position(ticker, Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("180.00"), BigDecimal.ONE);
        Instant oldEventTime = Instant.now().minusSeconds(100);
        position.updateLastEventAppliedAt(oldEventTime);

        when(positionRepository.findByTicker(ticker))
                .thenReturn(Uni.createFrom().item(position));
        when(positionRepository.isTransactionProcessed(any(), eq(transactionId)))
                .thenReturn(Uni.createFrom().item(true));
        when(positionRepository.update(any(Position.class)))
                .thenReturn(Uni.createFrom().item(position));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("180.00"),
                BigDecimal.ONE, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted();

        // Then - Verify lastEventAppliedAt was updated
        assertEquals(occurredAt, position.getLastEventAppliedAt());
    }

    @Test
    void testRollback_MarksPositionInactiveWhenNoSharesLeft() {
        // Given: A position with exactly the shares being rolled back
        UUID transactionId = UUID.randomUUID();
        String ticker = "META";
        BigDecimal quantity = BigDecimal.TEN;
        BigDecimal price = new BigDecimal("350.00");
        BigDecimal fees = BigDecimal.ZERO;
        Instant occurredAt = Instant.now();

        Position position = new Position(ticker, Currency.USD);
        position.applyBuy(quantity, price, fees); // Position has exactly 10 shares
        position.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTicker(ticker))
                .thenReturn(Uni.createFrom().item(position));
        when(positionRepository.isTransactionProcessed(any(), eq(transactionId)))
                .thenReturn(Uni.createFrom().item(true));
        when(positionRepository.update(any(Position.class)))
                .thenReturn(Uni.createFrom().item(position));

        ProcessTransactionDeletedUseCase.Command command = new ProcessTransactionDeletedUseCase.Command(
                transactionId, ticker, "BUY", quantity, price, fees, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionDeletedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionDeletedUseCase.Result.Success.class, result);
        ProcessTransactionDeletedUseCase.Result.Success successResult = (ProcessTransactionDeletedUseCase.Result.Success) result;

        // After reversing the only BUY, position should have 0 shares and be inactive
        assertEquals(BigDecimal.ZERO.setScale(6), successResult.position().getSharesOwned().setScale(6));
        assertFalse(successResult.position().getIsActive());
    }

}

