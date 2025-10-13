package com.portfolio.application.usecase.transaction;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.domain.port.PositionRepository;
import com.portfolio.domain.usecase.ProcessTransactionCreatedUseCase;
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

class ProcessTransactionCreatedServiceTest {

    private PositionRepository positionRepository;
    private ProcessTransactionCreatedService service;

    @BeforeEach
    void setUp() {
        positionRepository = mock(PositionRepository.class);
        service = new ProcessTransactionCreatedService(positionRepository);
    }

    @Test
    void testProcessBuyTransaction_NewPosition_Success() {
        // Given: No existing position for the ticker
        UUID transactionId = UUID.randomUUID();
        String ticker = "AAPL";
        BigDecimal quantity = BigDecimal.TEN;
        BigDecimal price = new BigDecimal("150.00");
        BigDecimal fees = new BigDecimal("1.50");
        Instant occurredAt = Instant.now();

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().nullItem());
        when(positionRepository.save(any(Position.class)))
                .thenAnswer(invocation -> {
                    Position pos = invocation.getArgument(0);
                    return Uni.createFrom().item(pos);
                });

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                transactionId, ticker, "BUY", quantity, price, fees, "USD", LocalDate.now(), occurredAt, "NASDAQ", "US"
        );

        // When
        ProcessTransactionCreatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionCreatedUseCase.Result.Success.class, result);
        ProcessTransactionCreatedUseCase.Result.Success successResult = (ProcessTransactionCreatedUseCase.Result.Success) result;

        Position position = successResult.position();
        assertEquals(ticker, position.getTicker());
        assertEquals(quantity.setScale(6), position.getSharesOwned().setScale(6));
        assertEquals(Currency.USD, position.getCurrency());
        assertEquals("NASDAQ", position.getExchange());
        assertEquals("US", position.getCountry());
        assertTrue(position.getIsActive());

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository).save(any(Position.class));
        verify(positionRepository, never()).update(any(Position.class));
    }

    @Test
    void testProcessBuyTransaction_ExistingPosition_Success() {
        // Given: Existing position with 10 shares
        UUID transactionId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        String ticker = "MSFT";
        BigDecimal quantity = new BigDecimal("5");
        BigDecimal price = new BigDecimal("300.00");
        BigDecimal fees = new BigDecimal("2.00");
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(
                positionId, ticker, BigDecimal.TEN, new BigDecimal("280.00"), new BigDecimal("280.00"),
                new BigDecimal("2800.00"), BigDecimal.ZERO, Currency.USD, LocalDate.now(),
                LocalDate.now(), true, Instant.now().minusSeconds(100), null, null, new java.util.ArrayList<>()
        );

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.isTransactionProcessed(positionId, transactionId))
                .thenReturn(Uni.createFrom().item(false));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> {
                    Position pos = invocation.getArgument(0);
                    return Uni.createFrom().item(pos);
                });

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                transactionId, ticker, "BUY", quantity, price, fees, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionCreatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionCreatedUseCase.Result.Success.class, result);
        ProcessTransactionCreatedUseCase.Result.Success successResult = (ProcessTransactionCreatedUseCase.Result.Success) result;

        Position position = successResult.position();
        assertEquals(new BigDecimal("15").setScale(6), position.getSharesOwned().setScale(6));

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository).isTransactionProcessed(positionId, transactionId);
        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
        verify(positionRepository, never()).save(any(Position.class));
    }

    @Test
    void testProcessSellTransaction_ExistingPosition_Success() {
        // Given: Existing position with 20 shares
        UUID transactionId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        String ticker = "GOOGL";
        BigDecimal quantity = new BigDecimal("5");
        BigDecimal price = new BigDecimal("140.00");
        BigDecimal fees = new BigDecimal("1.00");
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(
                positionId, ticker, new BigDecimal("20"), new BigDecimal("130.00"), new BigDecimal("130.00"),
                new BigDecimal("2600.00"), BigDecimal.ZERO, Currency.USD, LocalDate.now(),
                LocalDate.now(), true, Instant.now().minusSeconds(100), null, null, new java.util.ArrayList<>()
        );

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.isTransactionProcessed(positionId, transactionId))
                .thenReturn(Uni.createFrom().item(false));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> {
                    Position pos = invocation.getArgument(0);
                    return Uni.createFrom().item(pos);
                });

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                transactionId, ticker, "SELL", quantity, price, fees, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionCreatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionCreatedUseCase.Result.Success.class, result);
        ProcessTransactionCreatedUseCase.Result.Success successResult = (ProcessTransactionCreatedUseCase.Result.Success) result;

        Position position = successResult.position();
        assertEquals(new BigDecimal("15").setScale(6), position.getSharesOwned().setScale(6));
        assertTrue(position.getIsActive());

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository).isTransactionProcessed(positionId, transactionId);
        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
    }

    @Test
    void testProcessTransaction_AlreadyProcessed_ReturnsIgnored() {
        // Given: Transaction already processed
        UUID transactionId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        String ticker = "TSLA";
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(
                positionId, ticker, BigDecimal.TEN, new BigDecimal("200.00"), new BigDecimal("200.00"),
                new BigDecimal("2000.00"), BigDecimal.ZERO, Currency.USD, LocalDate.now(),
                LocalDate.now(), true, Instant.now().minusSeconds(100), null, null, new java.util.ArrayList<>()
        );

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.isTransactionProcessed(positionId, transactionId))
                .thenReturn(Uni.createFrom().item(true));

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("200.00"),
                BigDecimal.ONE, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionCreatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionCreatedUseCase.Result.Ignored.class, result);
        ProcessTransactionCreatedUseCase.Result.Ignored ignoredResult = (ProcessTransactionCreatedUseCase.Result.Ignored) result;
        assertEquals("Transaction already processed", ignoredResult.reason());

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository).isTransactionProcessed(positionId, transactionId);
        verify(positionRepository, never()).updatePositionWithTransactions(any(Position.class));
        verify(positionRepository, never()).save(any(Position.class));
    }

    @Test
    void testProcessTransaction_PersistenceError_ReturnsError() {
        // Given: Repository save fails
        UUID transactionId = UUID.randomUUID();
        String ticker = "NVDA";
        Instant occurredAt = Instant.now();

        RuntimeException dbException = new RuntimeException("Database connection failed");

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().nullItem());
        when(positionRepository.save(any(Position.class)))
                .thenReturn(Uni.createFrom().failure(dbException));

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                transactionId, ticker, "BUY", BigDecimal.TEN, new BigDecimal("400.00"),
                BigDecimal.ONE, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionCreatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionCreatedUseCase.Result.Error.class, result);
        ProcessTransactionCreatedUseCase.Result.Error errorResult = (ProcessTransactionCreatedUseCase.Result.Error) result;
        assertEquals(Errors.ProcessTransactionEvent.PERSISTENCE_ERROR, errorResult.error());
        assertTrue(errorResult.message().contains("Failed to process transaction created event"));

        verify(positionRepository).findByTickerForUpdate(ticker);
        verify(positionRepository).save(any(Position.class));
    }

    @Test
    void testProcessSellTransaction_ClosesPosition_WhenAllSharesSold() {
        // Given: Position with 10 shares, selling all 10
        UUID transactionId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        String ticker = "INTC";
        BigDecimal quantity = BigDecimal.TEN;
        BigDecimal price = new BigDecimal("45.00");
        BigDecimal fees = BigDecimal.ZERO;
        Instant occurredAt = Instant.now();

        Position existingPosition = new Position(
                positionId, ticker, quantity, new BigDecimal("40.00"), new BigDecimal("40.00"),
                new BigDecimal("400.00"), BigDecimal.ZERO, Currency.USD, LocalDate.now(),
                LocalDate.now(), true, Instant.now().minusSeconds(100), null, null, new java.util.ArrayList<>()
        );

        when(positionRepository.findByTickerForUpdate(ticker))
                .thenReturn(Uni.createFrom().item(existingPosition));
        when(positionRepository.isTransactionProcessed(positionId, transactionId))
                .thenReturn(Uni.createFrom().item(false));
        when(positionRepository.updatePositionWithTransactions(any(Position.class)))
                .thenAnswer(invocation -> {
                    Position pos = invocation.getArgument(0);
                    return Uni.createFrom().item(pos);
                });

        ProcessTransactionCreatedUseCase.Command command = new ProcessTransactionCreatedUseCase.Command(
                transactionId, ticker, "SELL", quantity, price, fees, "USD", LocalDate.now(), occurredAt, null, null
        );

        // When
        ProcessTransactionCreatedUseCase.Result result = service.execute(command)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertInstanceOf(ProcessTransactionCreatedUseCase.Result.Success.class, result);
        ProcessTransactionCreatedUseCase.Result.Success successResult = (ProcessTransactionCreatedUseCase.Result.Success) result;

        Position position = successResult.position();
        assertEquals(BigDecimal.ZERO.setScale(6), position.getSharesOwned().setScale(6));
        assertFalse(position.getIsActive());

        verify(positionRepository).updatePositionWithTransactions(any(Position.class));
    }
}
