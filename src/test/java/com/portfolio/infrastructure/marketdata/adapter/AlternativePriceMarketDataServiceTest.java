package com.portfolio.infrastructure.marketdata.adapter;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import com.portfolio.infrastructure.marketdata.client.AlternativePriceClient;
import com.portfolio.infrastructure.marketdata.dto.AlternativePriceResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlternativePriceMarketDataServiceTest {

    private AlternativePriceClient alternativePriceClient;
    private AlternativePriceMarketDataService service;

    @BeforeEach
    void setUp() {
        alternativePriceClient = mock(AlternativePriceClient.class);
        service = new AlternativePriceMarketDataService();
        service.alternativePriceClient = alternativePriceClient;
    }

    @Test
    void testGetCurrentPrice_Success() {
        // Given
        String ticker = "ITX.MC";
        String exchange = "BME";
        BigDecimal expectedPrice = new BigDecimal("47.80");
        AlternativePriceResponse response = new AlternativePriceResponse(
                ticker,
                expectedPrice,
                "EUR",
                "2025-10-02T18:14:37.116Z",
                new BigDecimal("-0.2"),
                new BigDecimal("-0.42")
        );

        when(alternativePriceClient.getPrice(ticker, exchange)).thenReturn(Uni.createFrom().item(response));

        // When
        BigDecimal result = service.getCurrentPrice(ticker, exchange)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertNotNull(result);
        assertEquals(expectedPrice, result);
        verify(alternativePriceClient).getPrice(ticker, exchange);
    }

    @Test
    void testGetCurrentPrice_InvalidInput_NullTicker() {
        // When
        Throwable failure = service.getCurrentPrice(null, "BME")
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.INVALID_INPUT, ex.getError());
        assertTrue(ex.getMessage().contains("Ticker cannot be null or empty"));
        verifyNoInteractions(alternativePriceClient);
    }

    @Test
    void testGetCurrentPrice_InvalidInput_EmptyTicker() {
        // When
        Throwable failure = service.getCurrentPrice("  ", "BME")
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.INVALID_INPUT, ex.getError());
        assertTrue(ex.getMessage().contains("Ticker cannot be null or empty"));
        verifyNoInteractions(alternativePriceClient);
    }

    @Test
    void testGetCurrentPrice_NullResponse() {
        // Given
        String ticker = "NULLTEST.MC";
        String exchange = "BME";

        when(alternativePriceClient.getPrice(ticker, exchange)).thenReturn(Uni.createFrom().nullItem());

        // When
        Throwable failure = service.getCurrentPrice(ticker, exchange)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.PRICE_NOT_FOUND, ex.getError());
        assertTrue(ex.getMessage().contains("Failed to get price from alternative provider"));
        verify(alternativePriceClient).getPrice(ticker, exchange);
    }

    @Test
    void testGetCurrentPrice_NullPriceInResponse() {
        // Given
        String ticker = "NOPRICE.MC";
        String exchange = "BME";
        AlternativePriceResponse response = new AlternativePriceResponse(
                ticker,
                null,
                "EUR",
                "2025-10-02T18:14:37.116Z",
                new BigDecimal("-0.2"),
                new BigDecimal("-0.42")
        );

        when(alternativePriceClient.getPrice(ticker, exchange)).thenReturn(Uni.createFrom().item(response));

        // When
        Throwable failure = service.getCurrentPrice(ticker, exchange)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.PRICE_NOT_FOUND, ex.getError());
        assertTrue(ex.getMessage().contains("Failed to get price from alternative provider"));
        verify(alternativePriceClient).getPrice(ticker, exchange);
    }

    @Test
    void testGetCurrentPrice_ClientFailure() {
        // Given
        String ticker = "NETERR.MC";
        String exchange = "BME";
        RuntimeException clientException = new RuntimeException("Connection timeout");

        when(alternativePriceClient.getPrice(ticker, exchange)).thenReturn(Uni.createFrom().failure(clientException));

        // When
        Throwable failure = service.getCurrentPrice(ticker, exchange)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof RuntimeException);
        assertEquals("Connection timeout", failure.getMessage());
        verify(alternativePriceClient).getPrice(ticker, exchange);
    }

    @Test
    void testGetCurrentPrice_TrimsAndUpperCasesTicker() {
        // Given
        String ticker = " itx.mc ";
        String normalizedTicker = "ITX.MC";
        String exchange = "BME";
        BigDecimal expectedPrice = new BigDecimal("47.80");
        AlternativePriceResponse response = new AlternativePriceResponse(
                normalizedTicker,
                expectedPrice,
                "EUR",
                "2025-10-02T18:14:37.116Z",
                new BigDecimal("-0.2"),
                new BigDecimal("-0.42")
        );

        when(alternativePriceClient.getPrice(normalizedTicker, exchange)).thenReturn(Uni.createFrom().item(response));

        // When
        BigDecimal result = service.getCurrentPrice(ticker, exchange)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertEquals(expectedPrice, result);
        verify(alternativePriceClient).getPrice(normalizedTicker, exchange);
    }

    @Test
    void testGetCurrentPrice_WithDifferentExchanges() {
        // Given
        String ticker = "AAPL";
        String exchange = "NYSE";
        BigDecimal expectedPrice = new BigDecimal("175.50");
        AlternativePriceResponse response = new AlternativePriceResponse(
                ticker,
                expectedPrice,
                "USD",
                "2025-10-02T18:14:37.116Z",
                new BigDecimal("2.5"),
                new BigDecimal("1.45")
        );

        when(alternativePriceClient.getPrice(ticker, exchange)).thenReturn(Uni.createFrom().item(response));

        // When
        BigDecimal result = service.getCurrentPrice(ticker, exchange)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertEquals(expectedPrice, result);
        verify(alternativePriceClient).getPrice(ticker, exchange);
    }
}

