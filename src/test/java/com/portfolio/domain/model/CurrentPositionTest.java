package com.portfolio.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CurrentPositionTest {

    @Test
    void testDefaultConstructor() {
        CurrentPosition position = new CurrentPosition();

        assertTrue(position.getIsActive());
        assertNotNull(position.getCurrentPriceTimestamp());
        assertTrue(position.getCurrentPriceTimestamp().isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void testConstructorFromPositionWithRealTimePrice() {
        // Given
        Position originalPosition = createTestPosition();
        BigDecimal realTimePrice = new BigDecimal("175.50");

        // When
        CurrentPosition currentPosition = new CurrentPosition(originalPosition, realTimePrice);

        // Then
        assertEquals(originalPosition.getId(), currentPosition.getId());
        assertEquals(originalPosition.getTicker(), currentPosition.getTicker());
        assertEquals(originalPosition.getSharesOwned(), currentPosition.getSharesOwned());
        assertEquals(originalPosition.getAverageCostPerShare(), currentPosition.getAverageCostPerShare());
        assertEquals(realTimePrice, currentPosition.getLatestMarketPrice());
        assertEquals(originalPosition.getTotalInvestedAmount(), currentPosition.getTotalInvestedAmount());
        assertEquals(originalPosition.getCurrency(), currentPosition.getCurrency());
        assertEquals(originalPosition.getLastUpdated(), currentPosition.getLastUpdated());
        assertEquals(originalPosition.getIsActive(), currentPosition.getIsActive());
        assertNotNull(currentPosition.getCurrentPriceTimestamp());
        assertTrue(currentPosition.getCurrentPriceTimestamp().isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void testConstructorFromPositionWithCustomTimestamp() {
        // Given
        Position originalPosition = createTestPosition();
        BigDecimal realTimePrice = new BigDecimal("175.50");
        LocalDateTime customTimestamp = LocalDateTime.now().minusHours(2);

        // When
        CurrentPosition currentPosition = new CurrentPosition(originalPosition, realTimePrice, customTimestamp);

        // Then
        assertEquals(originalPosition.getId(), currentPosition.getId());
        assertEquals(originalPosition.getTicker(), currentPosition.getTicker());
        assertEquals(originalPosition.getSharesOwned(), currentPosition.getSharesOwned());
        assertEquals(originalPosition.getAverageCostPerShare(), currentPosition.getAverageCostPerShare());
        assertEquals(realTimePrice, currentPosition.getLatestMarketPrice());
        assertEquals(originalPosition.getTotalInvestedAmount(), currentPosition.getTotalInvestedAmount());
        assertEquals(originalPosition.getCurrency(), currentPosition.getCurrency());
        assertEquals(originalPosition.getLastUpdated(), currentPosition.getLastUpdated());
        assertEquals(originalPosition.getIsActive(), currentPosition.getIsActive());
        assertEquals(customTimestamp, currentPosition.getCurrentPriceTimestamp());
    }

    @Test
    void testConstructorWithRequiredFields() {
        // Given
        String ticker = "MSFT";
        Currency currency = Currency.USD;
        BigDecimal currentPrice = new BigDecimal("300.00");

        // When
        CurrentPosition position = new CurrentPosition(ticker, currency, currentPrice);

        // Then
        assertEquals(ticker, position.getTicker());
        assertEquals(currency, position.getCurrency());
        assertEquals(currentPrice, position.getLatestMarketPrice());
        assertEquals(BigDecimal.ZERO, position.getSharesOwned());
        assertEquals(BigDecimal.ZERO, position.getAverageCostPerShare());
        assertEquals(BigDecimal.ZERO, position.getTotalInvestedAmount());
        assertTrue(position.getIsActive());
        assertNotNull(position.getLastUpdated());
        assertNotNull(position.getCurrentPriceTimestamp());
    }

    @ParameterizedTest
    @MethodSource("hasSharesTestData")
    void testHasShares(BigDecimal totalQuantity, boolean expectedResult) {
        // Given
        CurrentPosition position = new CurrentPosition();
        position.setSharesOwned(totalQuantity);

        // When & Then
        assertEquals(expectedResult, position.hasShares());
    }

    static Stream<Arguments> hasSharesTestData() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of(BigDecimal.ZERO, false),
                Arguments.of(new BigDecimal("-1.0"), false),
                Arguments.of(new BigDecimal("0.1"), true),
                Arguments.of(new BigDecimal("100.0"), true)
        );
    }

    @Test
    void testGetMarketValue() {
        // Given
        CurrentPosition position = new CurrentPosition();
        position.setSharesOwned(new BigDecimal("100"));
        position.setLatestMarketPrice(new BigDecimal("150.50"));

        // When
        BigDecimal marketValue = position.getTotalMarketValue();

        // Then
        assertEquals(new BigDecimal("15050.00"), marketValue);
    }

    @Test
    void testGetMarketValueWithNullValues() {
        // Given
        CurrentPosition position = new CurrentPosition();

        // When - Test with null quantity
        position.setSharesOwned(null);
        position.setLatestMarketPrice(new BigDecimal("150.50"));
        BigDecimal marketValue1 = position.getTotalMarketValue();

        // When - Test with null price
        position.setSharesOwned(new BigDecimal("100"));
        position.setLatestMarketPrice(null);
        BigDecimal marketValue2 = position.getTotalMarketValue();

        // Then
        assertEquals(BigDecimal.ZERO, marketValue1);
        assertEquals(BigDecimal.ZERO, marketValue2);
    }

    @Test
    void testGetUnrealizedGainLoss() {
        // Given
        CurrentPosition position = new CurrentPosition();
        position.setSharesOwned(new BigDecimal("100"));
        position.setLatestMarketPrice(new BigDecimal("150.50"));
        position.setTotalInvestedAmount(new BigDecimal("14000.00"));

        // When
        BigDecimal gainLoss = position.getUnrealizedGainLoss();

        // Then
        assertEquals(new BigDecimal("1050.00"), gainLoss);
    }

    @Test
    void testGetUnrealizedGainLossWithNullTotalCost() {
        // Given
        CurrentPosition position = new CurrentPosition();
        position.setSharesOwned(new BigDecimal("100"));
        position.setLatestMarketPrice(new BigDecimal("150.50"));
        position.setTotalInvestedAmount(null);

        // When
        BigDecimal gainLoss = position.getUnrealizedGainLoss();

        // Then
        assertEquals(new BigDecimal("15050.00"), gainLoss);
    }

    @ParameterizedTest
    @MethodSource("unrealizedGainLossPercentageTestData")
    void testGetUnrealizedGainLossPercentage(BigDecimal totalCost, BigDecimal marketValue, BigDecimal expectedPercentage) {
        // Given
        CurrentPosition position = new CurrentPosition();
        position.setSharesOwned(new BigDecimal("100"));
        position.setLatestMarketPrice(marketValue != null ? marketValue.divide(new BigDecimal("100")) : BigDecimal.ZERO);
        position.setTotalInvestedAmount(totalCost);

        // When
        BigDecimal percentage = position.getUnrealizedGainLossPercentage();

        // Then
        assertEquals(expectedPercentage, percentage);
    }

    static Stream<Arguments> unrealizedGainLossPercentageTestData() {
        return Stream.of(
                Arguments.of(null, new BigDecimal("15000"), BigDecimal.ZERO),
                Arguments.of(BigDecimal.ZERO, new BigDecimal("15000"), BigDecimal.ZERO),
                Arguments.of(new BigDecimal("10000"), new BigDecimal("15000"), new BigDecimal("50.000000")),
                Arguments.of(new BigDecimal("15000"), new BigDecimal("12000"), new BigDecimal("-20.000000"))
        );
    }

    @Test
    void testIsCurrentPriceFresh() {
        // Given - Fresh timestamp (within 30 minutes)
        CurrentPosition position1 = new CurrentPosition();
        position1.setCurrentPriceTimestamp(LocalDateTime.now().minusMinutes(15));

        // Given - Stale timestamp (older than 30 minutes)
        CurrentPosition position2 = new CurrentPosition();
        position2.setCurrentPriceTimestamp(LocalDateTime.now().minusMinutes(45));

        // Given - Null timestamp
        CurrentPosition position3 = new CurrentPosition();
        position3.setCurrentPriceTimestamp(null);

        // When & Then
        assertTrue(position1.isCurrentPriceFresh());
        assertFalse(position2.isCurrentPriceFresh());
        assertFalse(position3.isCurrentPriceFresh());
    }

    @Test
    void testSetCurrentPriceUpdatesTimestamp() {
        // Given
        CurrentPosition position = new CurrentPosition();
        LocalDateTime initialTimestamp = position.getCurrentPriceTimestamp();

        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        position.setLatestMarketPrice(new BigDecimal("200.00"));

        // Then
        assertEquals(new BigDecimal("200.00"), position.getLatestMarketPrice());
        assertTrue(position.getCurrentPriceTimestamp().isAfter(initialTimestamp));
    }

    private Position createTestPosition() {
        return new Position(
                UUID.randomUUID(),
                "AAPL",
                new BigDecimal("100"),
                new BigDecimal("150.00"),
                new BigDecimal("160.00"),
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                Currency.USD,
                LocalDate.now().minusDays(1),
                LocalDate.now().minusDays(10),
                true,
                Instant.now(),
                null,
                null,
                List.of()
        );
    }
}
