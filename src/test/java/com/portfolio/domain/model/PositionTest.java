package com.portfolio.domain.model;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Position domain model
 */
class PositionTest {

    @Test
    void testConstructor_CreatesNewPosition() {
        // When
        Position position = new Position("AAPL", Currency.USD);

        // Then
        assertEquals("AAPL", position.getTicker());
        assertEquals(Currency.USD, position.getCurrency());
        assertEquals(BigDecimal.ZERO, position.getSharesOwned());
        assertEquals(BigDecimal.ZERO, position.getAverageCostPerShare());
        assertEquals(BigDecimal.ZERO, position.getTotalInvestedAmount());
        assertEquals(BigDecimal.ZERO, position.getTotalTransactionFees());
        assertTrue(position.getIsActive());
        assertNotNull(position.getTransactions());
        assertTrue(position.getTransactions().isEmpty());
    }

    @Test
    void testHasShares_ReturnsTrueWhenSharesGreaterThanZero() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // When/Then
        assertTrue(position.hasShares());
    }

    @Test
    void testHasShares_ReturnsFalseWhenNoShares() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        assertFalse(position.hasShares());
    }

    @Test
    void testGetTotalMarketValue_CalculatesCorrectly() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // When
        BigDecimal marketValue = position.getTotalMarketValue();

        // Then
        assertEquals(new BigDecimal("1500.00"), marketValue);
    }

    @Test
    void testGetUnrealizedGainLoss_CalculatesProfit() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);
        // Buy 1 more share at higher price to update market price
        position.applyBuy(BigDecimal.ONE, new BigDecimal("150.00"), BigDecimal.ZERO);

        // When
        BigDecimal gainLoss = position.getUnrealizedGainLoss();

        // Then: Market value (11 * 150) - Invested (1000 + 150) = 1650 - 1150 = 500
        assertEquals(new BigDecimal("500.00"), gainLoss);
    }

    @Test
    void testGetUnrealizedGainLossPercentage_CalculatesCorrectly() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);
        // Buy 1 more share at 50% higher price
        position.applyBuy(BigDecimal.ONE, new BigDecimal("150.00"), BigDecimal.ZERO);

        // When
        BigDecimal percentage = position.getUnrealizedGainLossPercentage();

        // Then: Market value = 11 * 150 = 1650, Invested = 1150, Gain = 500, % = (500/1150)*100 = 43.478300%
        assertEquals(new BigDecimal("43.478300"), percentage);
    }

    @Test
    void testGetUnrealizedGainLossPercentage_ReturnsZeroWhenNoInvestment() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When
        BigDecimal percentage = position.getUnrealizedGainLossPercentage();

        // Then
        assertEquals(BigDecimal.ZERO, percentage);
    }

    @Test
    void testApplyBuy_FirstPurchase() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), new BigDecimal("5.00"));

        // Then
        assertEquals(BigDecimal.TEN.setScale(6), position.getSharesOwned().setScale(6));
        assertEquals(new BigDecimal("150.500000"), position.getAverageCostPerShare());
        assertEquals(new BigDecimal("1505.00"), position.getTotalInvestedAmount());
        assertEquals(new BigDecimal("5.00"), position.getTotalTransactionFees());
        assertEquals(new BigDecimal("150.00"), position.getLatestMarketPrice());
        assertTrue(position.getIsActive());
    }

    @Test
    void testApplyBuy_AdditionalPurchase_UpdatesAverageCost() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);

        // When: Buy 10 more shares at $150
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // Then: Average cost = (1000 + 1500) / 20 = 125
        assertEquals(new BigDecimal("20").setScale(6), position.getSharesOwned().setScale(6));
        assertEquals(new BigDecimal("125.000000"), position.getAverageCostPerShare());
        assertEquals(new BigDecimal("2500.00"), position.getTotalInvestedAmount());
    }

    @Test
    void testApplyBuy_WithNullFees_TreatsAsZero() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), null);

        // Then
        assertEquals(new BigDecimal("1500.00"), position.getTotalInvestedAmount());
        assertEquals(BigDecimal.ZERO, position.getTotalTransactionFees());
    }

    @Test
    void testApplyBuy_ThrowsExceptionForNullQuantity() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                position.applyBuy(null, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception.getError());
    }

    @Test
    void testApplyBuy_ThrowsExceptionForZeroQuantity() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                position.applyBuy(BigDecimal.ZERO, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception.getError());
    }

    @Test
    void testApplyBuy_ThrowsExceptionForNegativeQuantity() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                position.applyBuy(new BigDecimal("-10"), new BigDecimal("150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception.getError());
    }

    @Test
    void testApplyBuy_ThrowsExceptionForNullPrice() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                position.applyBuy(BigDecimal.TEN, null, BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception.getError());
    }

    @Test
    void testApplyBuy_ThrowsExceptionForNegativePrice() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                position.applyBuy(BigDecimal.TEN, new BigDecimal("-150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception.getError());
    }

    @Test
    void testApplySell_ReducesSharesAndInvestedAmount() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(new BigDecimal("20"), new BigDecimal("100.00"), BigDecimal.ZERO);

        // When: Sell 5 shares at $150 with $2 fees
        position.applySell(new BigDecimal("5"), new BigDecimal("150.00"), new BigDecimal("2.00"));

        // Then: 20 - 5 = 15 shares, invested = 2000 - (5 * 100) = 1500
        assertEquals(new BigDecimal("15").setScale(6), position.getSharesOwned().setScale(6));
        assertEquals(0, new BigDecimal("1500.00").compareTo(position.getTotalInvestedAmount()));
        assertEquals(new BigDecimal("2.00"), position.getTotalTransactionFees());
        assertEquals(new BigDecimal("150.00"), position.getLatestMarketPrice());
        assertTrue(position.getIsActive());
    }

    @Test
    void testApplySell_MarksInactiveWhenAllSharesSold() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);

        // When: Sell all shares
        position.applySell(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // Then
        assertEquals(BigDecimal.ZERO.setScale(6), position.getSharesOwned().setScale(6));
        assertFalse(position.getIsActive());
    }

    @Test
    void testApplySell_ThrowsExceptionWhenOverselling() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(new BigDecimal("5"), new BigDecimal("100.00"), BigDecimal.ZERO);

        // When/Then: Try to sell more than owned
        ServiceException exception = assertThrows(ServiceException.class, () ->
                position.applySell(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.OVERSELL, exception.getError());
    }

    @Test
    void testApplySell_WithNullFees_TreatsAsZero() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);

        // When
        position.applySell(new BigDecimal("5"), new BigDecimal("150.00"), null);

        // Then
        assertEquals(0, new BigDecimal("500.00").compareTo(position.getTotalInvestedAmount()));
    }

    @Test
    void testApplySell_ThrowsExceptionForInvalidInputs() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);

        // When/Then
        ServiceException exception1 = assertThrows(ServiceException.class, () ->
                position.applySell(null, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception1.getError());

        ServiceException exception2 = assertThrows(ServiceException.class, () ->
                position.applySell(BigDecimal.ZERO, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception2.getError());

        ServiceException exception3 = assertThrows(ServiceException.class, () ->
                position.applySell(BigDecimal.TEN, null, BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception3.getError());

        ServiceException exception4 = assertThrows(ServiceException.class, () ->
                position.applySell(BigDecimal.TEN, new BigDecimal("-150.00"), BigDecimal.ZERO)
        );
        assertEquals(Errors.Position.INVALID_INPUT, exception4.getError());
    }

    @Test
    void testApplyTransaction_BuyType_AddsToTransactionList() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        UUID transactionId = UUID.randomUUID();

        // When
        position.applyTransaction(transactionId, "BUY", BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // Then
        assertEquals(1, position.getTransactions().size());
        assertTrue(position.getTransactions().contains(transactionId));
        assertEquals(BigDecimal.TEN.setScale(6), position.getSharesOwned().setScale(6));
    }

    @Test
    void testApplyTransaction_SellType_AddsToTransactionList() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(new BigDecimal("20"), new BigDecimal("100.00"), BigDecimal.ZERO);
        UUID transactionId = UUID.randomUUID();

        // When
        position.applyTransaction(transactionId, "SELL", BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // Then
        assertEquals(1, position.getTransactions().size());
        assertTrue(position.getTransactions().contains(transactionId));
        assertEquals(BigDecimal.TEN.setScale(6), position.getSharesOwned().setScale(6));
    }

    @Test
    void testApplyTransaction_CaseInsensitive() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        UUID transactionId = UUID.randomUUID();

        // When
        position.applyTransaction(transactionId, "buy", BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // Then
        assertEquals(BigDecimal.TEN.setScale(6), position.getSharesOwned().setScale(6));
    }

    @Test
    void testApplyTransaction_ThrowsExceptionForNullType() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                position.applyTransaction(UUID.randomUUID(), null, BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
    }

    @Test
    void testApplyTransaction_ThrowsExceptionForUnknownType() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                position.applyTransaction(UUID.randomUUID(), "TRANSFER", BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
    }

    @Test
    void testReverseTransaction_ReverseBuy_RestoresState() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        UUID transactionId = UUID.randomUUID();
        position.applyTransaction(transactionId, "BUY", BigDecimal.TEN, new BigDecimal("150.00"), new BigDecimal("5.00"));

        // When
        position.reverseTransaction(transactionId, "BUY", BigDecimal.TEN, new BigDecimal("150.00"), new BigDecimal("5.00"));

        // Then
        assertEquals(BigDecimal.ZERO.setScale(6), position.getSharesOwned().setScale(6));
        assertEquals(0, BigDecimal.ZERO.compareTo(position.getTotalInvestedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(position.getTotalTransactionFees()));
        assertFalse(position.getIsActive());
        assertFalse(position.getTransactions().contains(transactionId));
    }

    @Test
    void testReverseTransaction_ReverseSell_RestoresShares() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(new BigDecimal("20"), new BigDecimal("100.00"), BigDecimal.ZERO);
        UUID transactionId = UUID.randomUUID();
        position.applyTransaction(transactionId, "SELL", new BigDecimal("5"), new BigDecimal("150.00"), new BigDecimal("2.00"));

        // When
        position.reverseTransaction(transactionId, "SELL", new BigDecimal("5"), new BigDecimal("150.00"), new BigDecimal("2.00"));

        // Then
        assertEquals(new BigDecimal("20").setScale(6), position.getSharesOwned().setScale(6));
        assertTrue(position.getIsActive());
        assertFalse(position.getTransactions().contains(transactionId));
    }

    @Test
    void testReverseTransaction_PartialReverse_MaintainsCorrectState() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        position.applyTransaction(tx1, "BUY", BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.ZERO);
        position.applyTransaction(tx2, "BUY", BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // When: Reverse the second transaction
        position.reverseTransaction(tx2, "BUY", BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // Then: Should be back to first transaction state
        assertEquals(BigDecimal.TEN.setScale(6), position.getSharesOwned().setScale(6));
        assertEquals(new BigDecimal("1000.00"), position.getTotalInvestedAmount());
        assertEquals(new BigDecimal("100.000000"), position.getAverageCostPerShare());
        assertTrue(position.getTransactions().contains(tx1));
        assertFalse(position.getTransactions().contains(tx2));
    }

    @Test
    void testReverseTransaction_ThrowsExceptionForNullType() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                position.reverseTransaction(UUID.randomUUID(), null, BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
    }

    @Test
    void testReverseTransaction_ThrowsExceptionForUnknownType() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                position.reverseTransaction(UUID.randomUUID(), "UNKNOWN", BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO)
        );
    }

    @Test
    void testMarkAsInactive_SetsIsActiveToFalse() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), BigDecimal.ZERO);

        // When
        position.markAsInactive();

        // Then
        assertFalse(position.getIsActive());
    }

    @Test
    void testUpdateLastEventAppliedAt_UpdatesTimestamp() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        Instant timestamp = Instant.now();

        // When
        position.updateLastEventAppliedAt(timestamp);

        // Then
        assertEquals(timestamp, position.getLastEventAppliedAt());
    }

    @Test
    void testShouldIgnoreEvent_ReturnsTrueForOlderEvent() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        Instant now = Instant.now();
        position.updateLastEventAppliedAt(now);

        // When
        boolean shouldIgnore = position.shouldIgnoreEvent(now.minusSeconds(100));

        // Then
        assertTrue(shouldIgnore);
    }

    @Test
    void testShouldIgnoreEvent_ReturnsTrueForSameTimestamp() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        Instant now = Instant.now();
        position.updateLastEventAppliedAt(now);

        // When
        boolean shouldIgnore = position.shouldIgnoreEvent(now);

        // Then
        assertTrue(shouldIgnore);
    }

    @Test
    void testShouldIgnoreEvent_ReturnsFalseForNewerEvent() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        Instant now = Instant.now();
        position.updateLastEventAppliedAt(now);

        // When
        boolean shouldIgnore = position.shouldIgnoreEvent(now.plusSeconds(100));

        // Then
        assertFalse(shouldIgnore);
    }

    @Test
    void testShouldIgnoreEvent_ReturnsFalseWhenNoLastEventApplied() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When
        boolean shouldIgnore = position.shouldIgnoreEvent(Instant.now());

        // Then
        assertFalse(shouldIgnore);
    }

    @Test
    void testUpdateExchange_SetsExchange() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When
        position.updateExchange("NASDAQ");

        // Then
        assertEquals("NASDAQ", position.getExchange());
    }

    @Test
    void testUpdateCountry_SetsCountry() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When
        position.updateCountry("US");

        // Then
        assertEquals("US", position.getCountry());
    }

    @Test
    void testComplexScenario_MultipleBuysAndSells() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When: Buy 10 shares at $100
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), new BigDecimal("1.00"));
        // Buy 10 more shares at $150
        position.applyBuy(BigDecimal.TEN, new BigDecimal("150.00"), new BigDecimal("1.00"));
        // Sell 5 shares at $200
        position.applySell(new BigDecimal("5"), new BigDecimal("200.00"), new BigDecimal("1.00"));

        // Then
        assertEquals(new BigDecimal("15").setScale(6), position.getSharesOwned().setScale(6));
        // Average cost = (1001 + 1501) / 20 = 125.1
        assertEquals(new BigDecimal("125.100000"), position.getAverageCostPerShare());
        // Invested = 2502 - (5 * 125.1) = 1876.5
        assertEquals(0, new BigDecimal("1876.50").compareTo(position.getTotalInvestedAmount()));
        // Total fees = 1 + 1 + 1 = 3
        assertEquals(new BigDecimal("3.00"), position.getTotalTransactionFees());
        assertTrue(position.getIsActive());
    }

    @Test
    void testAverageCostCalculation_WithFees() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When: Buy 10 shares at $100 with $10 fees
        position.applyBuy(BigDecimal.TEN, new BigDecimal("100.00"), BigDecimal.TEN);

        // Then: Average cost = (1000 + 10) / 10 = 101
        assertEquals(new BigDecimal("101.000000"), position.getAverageCostPerShare());
        assertEquals(new BigDecimal("1010.00"), position.getTotalInvestedAmount());
    }

    @Test
    void testReverseBuy_RecalculatesAverageCostCorrectly() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        position.applyTransaction(tx1, "BUY", BigDecimal.TEN, new BigDecimal("100.00"), new BigDecimal("5.00"));
        position.applyTransaction(tx2, "BUY", new BigDecimal("5"), new BigDecimal("120.00"), new BigDecimal("3.00"));

        // When: Reverse the second buy
        position.reverseTransaction(tx2, "BUY", new BigDecimal("5"), new BigDecimal("120.00"), new BigDecimal("3.00"));

        // Then: Should be back to first transaction's average cost
        assertEquals(new BigDecimal("100.500000"), position.getAverageCostPerShare());
        assertEquals(new BigDecimal("1005.00"), position.getTotalInvestedAmount());
        assertEquals(new BigDecimal("5.00"), position.getTotalTransactionFees());
    }

    @Test
    void testReverseSell_RestoresProportionalCost() {
        // Given
        Position position = new Position("AAPL", Currency.USD);
        position.applyBuy(new BigDecimal("20"), new BigDecimal("100.00"), BigDecimal.ZERO);
        UUID sellTxId = UUID.randomUUID();
        position.applyTransaction(sellTxId, "SELL", new BigDecimal("5"), new BigDecimal("150.00"), new BigDecimal("2.00"));

        // When: Reverse the sell
        position.reverseTransaction(sellTxId, "SELL", new BigDecimal("5"), new BigDecimal("150.00"), new BigDecimal("2.00"));

        // Then: Should restore to original state
        assertEquals(new BigDecimal("20").setScale(6), position.getSharesOwned().setScale(6));
        assertEquals(0, new BigDecimal("2000.00").compareTo(position.getTotalInvestedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(position.getTotalTransactionFees()));
    }
}
