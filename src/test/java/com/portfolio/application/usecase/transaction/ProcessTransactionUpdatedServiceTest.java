package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionUpdatedUseCase;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessTransactionUpdatedServiceTest {

    private PositionRepository positionRepository;
    private ProcessTransactionUpdatedService service;

    @BeforeEach
    void setUp() {
        positionRepository = mock(PositionRepository.class);
        service = new ProcessTransactionUpdatedService(positionRepository);
    }

    // ========================================
    // SAME TICKER UPDATE TESTS
    // ========================================

    @Test
    void testUpdateQuantity_SameTicker_Success() {
        // Given: Existing position with BUY 10 shares @ $250, updating to BUY 15 shares @ $250
        UUID transactionId = UUID.randomUUID();
        String ticker = "AAPL";
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(ticker, Currency.USD);
        existingPosition.applyBuy(BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("2.00"));
        existingPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("2.00"),
                "BUY", new BigDecimal("15"), new BigDecimal("250.00"), new BigDecimal("2.00"),
                "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Success.class, result);
        ProcessTransactionUpdatedUseCase.Result.Success successResult = (ProcessTransactionUpdatedUseCase.Result.Success) result;
        Position position = successResult.position();

        // After reversing 10 shares and adding 15, should have 15 shares
        assertEquals(new BigDecimal("15").setScale(6), position.getSharesOwned().setScale(6));
        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testUpdatePrice_SameTicker_Success() {
        // Given: Updating price from $250 to $260
        UUID transactionId = UUID.randomUUID();
        String ticker = "MSFT";
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(ticker, Currency.USD);
        existingPosition.applyBuy(BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("2.00"));
        existingPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("2.00"),
                "BUY", BigDecimal.TEN, new BigDecimal("260.00"), new BigDecimal("2.00"),
                "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Success.class, result);
        ProcessTransactionUpdatedUseCase.Result.Success successResult = (ProcessTransactionUpdatedUseCase.Result.Success) result;
        Position position = successResult.position();

        // Shares remain 10, but average cost should be updated
        assertEquals(BigDecimal.TEN.setScale(6), position.getSharesOwned().setScale(6));
        // Average cost = (10 * 260 + 2) / 10 = 260.2
        assertEquals(new BigDecimal("260.200000"), position.getAverageCostPerShare());
        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testUpdateFees_SameTicker_Success() {
        // Given: Updating fees from $2.00 to $3.50
        UUID transactionId = UUID.randomUUID();
        String ticker = "GOOGL";
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(ticker, Currency.USD);
        existingPosition.applyBuy(BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("2.00"));
        existingPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("2.00"),
                "BUY", BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("3.50"),
                "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Success.class, result);
        ProcessTransactionUpdatedUseCase.Result.Success successResult = (ProcessTransactionUpdatedUseCase.Result.Success) result;
        Position position = successResult.position();

        // Fees should be correctly updated: subtract $2.00, add $3.50 = $3.50 total
        assertEquals(new BigDecimal("3.50").setScale(6), position.getTotalTransactionFees().setScale(6));
        // Invested amount should be 10 * 250 + 3.50 = 2503.50
        assertEquals(new BigDecimal("2503.50").setScale(6), position.getTotalInvestedAmount().setScale(6));
        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testUpdateType_BuyToSell_SameTicker_Success() {
        // Given: Changing from BUY to SELL
        UUID transactionId = UUID.randomUUID();
        String ticker = "TSLA";
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(ticker, Currency.USD);
        // Start with 20 shares, then apply the BUY that we'll later change to SELL
        existingPosition.applyBuy(new BigDecimal("20"), new BigDecimal("240.00"), BigDecimal.ZERO);
        existingPosition.applyBuy(BigDecimal.TEN, new BigDecimal("250.00"), BigDecimal.ZERO);
        existingPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("250.00"), BigDecimal.ZERO,
                "SELL", BigDecimal.TEN, new BigDecimal("250.00"), BigDecimal.ZERO,
                "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Success.class, result);
        ProcessTransactionUpdatedUseCase.Result.Success successResult = (ProcessTransactionUpdatedUseCase.Result.Success) result;
        Position position = successResult.position();

        // Started with 30 shares, reverse BUY 10 (-> 20 shares), apply SELL 10 (-> 10 shares)
        assertEquals(BigDecimal.TEN.setScale(6), position.getSharesOwned().setScale(6));
        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testUpdateTransaction_PositionNotFound_ReturnsIgnored() {
        // Given: No position exists
        UUID transactionId = UUID.randomUUID();
        String ticker = "NVDA";
        Instant occurredAt = Instant.now();

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().nullItem());

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("400.00"), BigDecimal.ONE,
                "BUY", new BigDecimal("15"), new BigDecimal("400.00"), BigDecimal.ONE,
                "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Ignored.class, result);
        ProcessTransactionUpdatedUseCase.Result.Ignored ignoredResult = (ProcessTransactionUpdatedUseCase.Result.Ignored) result;
        assertTrue(ignoredResult.reason().contains("Position not found"));

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository, never()).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testUpdateTransaction_OutOfOrderEvent_ReturnsIgnored() {
        // Given: Event is older than last applied event
        UUID transactionId = UUID.randomUUID();
        String ticker = "INTC";
        Instant oldOccurredAt = Instant.now().minusSeconds(200);
        Instant lastEventApplied = Instant.now().minusSeconds(100);

        Position existingPosition = new Position(ticker, Currency.USD);
        existingPosition.applyBuy(BigDecimal.TEN, new BigDecimal("45.00"), BigDecimal.ZERO);
        existingPosition.updateLastEventAppliedAt(lastEventApplied);

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("45.00"), BigDecimal.ZERO,
                "BUY", new BigDecimal("15"), new BigDecimal("45.00"), BigDecimal.ZERO,
                "USD", oldOccurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Ignored.class, result);
        ProcessTransactionUpdatedUseCase.Result.Ignored ignoredResult = (ProcessTransactionUpdatedUseCase.Result.Ignored) result;
        assertEquals("Out-of-order event", ignoredResult.reason());

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository, never()).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testUpdateTransaction_PersistenceError_ReturnsError() {
        // Given: Repository update fails
        UUID transactionId = UUID.randomUUID();
        String ticker = "AMD";
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(ticker, Currency.USD);
        existingPosition.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ONE);
        existingPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        RuntimeException dbException = new RuntimeException("Database connection failed");

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenReturn(Uni.createFrom().failure(dbException));

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ONE,
                "BUY", new BigDecimal("15"), new BigDecimal("100.00"), BigDecimal.ONE,
                "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Error.class, result);
        ProcessTransactionUpdatedUseCase.Result.Error errorResult = (ProcessTransactionUpdatedUseCase.Result.Error) result;
        assertEquals(Errors.ProcessTransactionEvent.PERSISTENCE_ERROR, errorResult.error());
        assertTrue(errorResult.message().contains("Failed to process transaction updated event"));

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
    }

    // ========================================
    // TICKER CHANGE TESTS
    // ========================================

    @Test
    void testTickerChange_MovesTransactionToNewPosition_Success() {
        // Given: Transaction moves from APPL (wrong) to AAPL (correct)
        UUID transactionId = UUID.randomUUID();
        String oldTicker = "APPL";
        String newTicker = "AAPL";
        Instant occurredAt = Instant.now();

        // Old position has the transaction
        Position oldPosition = new Position(oldTicker, Currency.USD);
        oldPosition.applyBuy(BigDecimal.TEN, new BigDecimal("250.00"), new BigDecimal("2.00"));
        oldPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        // New position doesn't exist yet
        when(positionRepository.findByTickerForUpdate(oldTicker))
                .thenReturn(Uni.createFrom().item(oldPosition));
        when(positionRepository.findByTickerForUpdate(newTicker))
                .thenReturn(Uni.createFrom().nullItem());
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));
        when(positionRepository.save(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createTickerChangeCommand(
                transactionId, oldTicker, newTicker, "BUY", BigDecimal.TEN,
                new BigDecimal("250.00"), new BigDecimal("2.00"), "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Success.class, result);
        ProcessTransactionUpdatedUseCase.Result.Success successResult = (ProcessTransactionUpdatedUseCase.Result.Success) result;
        Position newPosition = successResult.position();

        // New position should have the transaction
        assertEquals(newTicker, newPosition.getTicker());
        assertEquals(BigDecimal.TEN.setScale(6), newPosition.getSharesOwned().setScale(6));

        // Verify both positions were processed
        verify(positionRepository).findByTickerForUpdate(oldTicker);
        verify(positionRepository).findByTickerForUpdate(newTicker);
        verify(positionRepository).updatePositionWithTransactions(any(Position.class)); // old position
        verify(positionRepository).save(any(Position.class)); // new position created
    }

    @Test
    void testTickerChange_AddsToExistingNewPosition_Success() {
        // Given: New position already exists with other transactions
        UUID transactionId = UUID.randomUUID();
        String oldTicker = "GOOG";
        String newTicker = "GOOGL";
        Instant occurredAt = Instant.now();

        // Old position
        Position oldPosition = new Position(oldTicker, Currency.USD);
        oldPosition.applyBuy(BigDecimal.TEN, new BigDecimal("140.00"), BigDecimal.ONE);
        oldPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        // New position already exists
        Position existingNewPosition = new Position(newTicker, Currency.USD);
        existingNewPosition.applyBuy(new BigDecimal("20"), new BigDecimal("145.00"), BigDecimal.ONE);
        existingNewPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(oldTicker))
                .thenReturn(Uni.createFrom().item(oldPosition));
        when(positionRepository.findByTickerForUpdate(newTicker))
                .thenReturn(Uni.createFrom().item(existingNewPosition));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createTickerChangeCommand(
                transactionId, oldTicker, newTicker, "BUY", BigDecimal.TEN,
                new BigDecimal("140.00"), BigDecimal.ONE, "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Success.class, result);
        ProcessTransactionUpdatedUseCase.Result.Success successResult = (ProcessTransactionUpdatedUseCase.Result.Success) result;
        Position newPosition = successResult.position();

        // New position should now have 30 shares (20 existing + 10 moved)
        assertEquals(new BigDecimal("30").setScale(6), newPosition.getSharesOwned().setScale(6));

        verify(positionRepository).findByTickerForUpdate(oldTicker);
        verify(positionRepository).findByTickerForUpdate(newTicker);
        verify(positionRepository, times(2)).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testTickerChange_OldPositionNotFound_ReturnsError() {
        // Given: Old position doesn't exist
        UUID transactionId = UUID.randomUUID();
        String oldTicker = "INVALID";
        String newTicker = "VALID";
        Instant occurredAt = Instant.now();

        when(positionRepository.findByTickerForUpdate(oldTicker))
                .thenReturn(Uni.createFrom().nullItem());

        ProcessTransactionUpdatedUseCase.Command command = createTickerChangeCommand(
                transactionId, oldTicker, newTicker, "BUY", BigDecimal.TEN,
                new BigDecimal("100.00"), BigDecimal.ONE, "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Error.class, result);
        ProcessTransactionUpdatedUseCase.Result.Error errorResult = (ProcessTransactionUpdatedUseCase.Result.Error) result;
        assertEquals(Errors.ProcessTransactionEvent.INVALID_INPUT, errorResult.error());
        assertTrue(errorResult.message().contains("Old position not found"));

        verify(positionRepository).findByTickerForUpdate(oldTicker);
        verify(positionRepository, never()).findByTickerForUpdate(newTicker);
    }

    @Test
    void testTickerChange_OutOfOrderOnOldPosition_ReturnsIgnored() {
        // Given: Event is out of order for old position
        UUID transactionId = UUID.randomUUID();
        String oldTicker = "OLD";
        String newTicker = "NEW";
        Instant oldOccurredAt = Instant.now().minusSeconds(200);

        Position oldPosition = new Position(oldTicker, Currency.USD);
        oldPosition.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);
        oldPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(oldTicker))
                .thenReturn(Uni.createFrom().item(oldPosition));

        ProcessTransactionUpdatedUseCase.Command command = createTickerChangeCommand(
                transactionId, oldTicker, newTicker, "BUY", BigDecimal.TEN,
                new BigDecimal("100.00"), BigDecimal.ZERO, "USD", oldOccurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Ignored.class, result);
        ProcessTransactionUpdatedUseCase.Result.Ignored ignoredResult = (ProcessTransactionUpdatedUseCase.Result.Ignored) result;
        assertEquals("Out-of-order event on old position", ignoredResult.reason());

        verify(positionRepository).findByTickerForUpdate(oldTicker);
        verify(positionRepository, never()).findByTickerForUpdate(newTicker);
    }

    @Test
    void testTickerChange_OutOfOrderOnNewPosition_ReturnsIgnored() {
        // Given: Event is out of order for new position
        UUID transactionId = UUID.randomUUID();
        String oldTicker = "OLD";
        String newTicker = "NEW";
        Instant oldOccurredAt = Instant.now().minusSeconds(200);

        Position oldPosition = new Position(oldTicker, Currency.USD);
        oldPosition.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);
        oldPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(500));

        Position newPosition = new Position(newTicker, Currency.USD);
        newPosition.applyBuy(new BigDecimal("20"), new BigDecimal("100.00"), BigDecimal.ZERO);
        newPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(oldTicker))
                .thenReturn(Uni.createFrom().item(oldPosition));
        when(positionRepository.findByTickerForUpdate(newTicker))
                .thenReturn(Uni.createFrom().item(newPosition));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createTickerChangeCommand(
                transactionId, oldTicker, newTicker, "BUY", BigDecimal.TEN,
                new BigDecimal("100.00"), BigDecimal.ZERO, "USD", oldOccurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Ignored.class, result);
        ProcessTransactionUpdatedUseCase.Result.Ignored ignoredResult = (ProcessTransactionUpdatedUseCase.Result.Ignored) result;
        assertEquals("Out-of-order event on new position", ignoredResult.reason());

        verify(positionRepository).findByTickerForUpdate(oldTicker);
        verify(positionRepository).findByTickerForUpdate(newTicker);
        verify(positionRepository).updatePositionWithTransactions(any(Position.class)); // old position was updated before checking new position
    }

    @Test
    void testPersistenceExceptionRetry_ThenSuccess() {
        // Given: First attempts fail, third succeeds
        UUID transactionId = UUID.randomUUID();
        String ticker = "RETRY";
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(ticker, Currency.USD);
        existingPosition.applyBuy(BigDecimal.TEN, new BigDecimal("50.00"), BigDecimal.ZERO);
        existingPosition.updateLastEventAppliedAt(Instant.now().minusSeconds(100));

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(
                        Uni.createFrom().item(existingPosition),
                        Uni.createFrom().item(existingPosition),
                        Uni.createFrom().item(existingPosition)
                );

        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenReturn(Uni.createFrom().failure(new PersistenceException("db error 1")))
                .thenReturn(Uni.createFrom().failure(new PersistenceException("db error 2")))
                .thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));

        ProcessTransactionUpdatedUseCase.Command command = createSameTickerCommand(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("50.00"), BigDecimal.ZERO,
                "BUY", new BigDecimal("15"), new BigDecimal("50.00"), BigDecimal.ZERO,
                "USD", occurredAt
        );

        // When
        ProcessTransactionUpdatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionUpdatedUseCase.Result.Success.class, result);
        verify(positionRepository, times(3)).findByTickerForUpdate(ticker);
        verify(positionRepository, times(3)).updatePositionWithTransactions(any(Position.class));
    }

    // Helper methods to create commands

    private ProcessTransactionUpdatedUseCase.Command createSameTickerCommand(
            UUID txnId, String ticker,
            String prevType, BigDecimal prevQty, BigDecimal prevPrice, BigDecimal prevFees,
            String newType, BigDecimal newQty, BigDecimal newPrice, BigDecimal newFees,
            String currency, Instant occurredAt) {

        return new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        txnId, ticker, prevType, prevQty, prevPrice, prevFees, currency,
                        LocalDate.now(), null, null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        txnId, ticker, newType, newQty, newPrice, newFees, currency,
                        LocalDate.now(), null, null
                ),
                occurredAt
        );
    }

    private ProcessTransactionUpdatedUseCase.Command createTickerChangeCommand(
            UUID txnId, String oldTicker, String newTicker,
            String type, BigDecimal qty, BigDecimal price, BigDecimal fees,
            String currency, Instant occurredAt) {

        return new ProcessTransactionUpdatedUseCase.Command(
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        txnId, oldTicker, type, qty, price, fees, currency,
                        LocalDate.now(), null, null
                ),
                new ProcessTransactionUpdatedUseCase.TransactionData(
                        txnId, newTicker, type, qty, price, fees, currency,
                        LocalDate.now(), null, null
                ),
                occurredAt
        );
    }
}
