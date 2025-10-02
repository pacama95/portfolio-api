package com.portfolio.infrastructure.marketdata.adapter;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import com.portfolio.infrastructure.marketdata.client.TwelveDataClient;
import com.portfolio.infrastructure.marketdata.dto.TwelveDataPriceResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwelveDataMarketDataServiceTest {

    private TwelveDataClient twelveDataClient;
    private TwelveDataMarketDataService service;
    private static final String API_KEY = "test-api-key";

    @BeforeEach
    void setUp() {
        twelveDataClient = mock(TwelveDataClient.class);
        service = new TwelveDataMarketDataService();
        service.twelveDataClient = twelveDataClient;
        service.apiKey = API_KEY;
    }

    @Test
    void testGetCurrentPrice_Success() {
        // Given
        String ticker = "AAPL";
        String exchange = null;
        BigDecimal expectedPrice = new BigDecimal("175.50");
        TwelveDataPriceResponse response = new TwelveDataPriceResponse(
                expectedPrice,
                ticker,
                "1234567890",
                "2023-12-01 15:30:00",
                "ok",
                null
        );

        when(twelveDataClient.getPrice(ticker, API_KEY)).thenReturn(Uni.createFrom().item(response));

        // When
        BigDecimal result = service.getCurrentPrice(ticker, exchange)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertNotNull(result);
        assertEquals(expectedPrice, result);
        verify(twelveDataClient).getPrice(ticker, API_KEY);
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
        verifyNoInteractions(twelveDataClient);
    }

    @Test
    void testGetCurrentPrice_InvalidInput_EmptyTicker() {
        // When
        Throwable failure = service.getCurrentPrice("", "BME")
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.INVALID_INPUT, ex.getError());
        assertTrue(ex.getMessage().contains("Ticker cannot be null or empty"));
        verifyNoInteractions(twelveDataClient);
    }

    @Test
    void testGetCurrentPrice_ErrorResponse_WithErrorStatus() {
        // Given
        String ticker = "INVALID";
        TwelveDataPriceResponse response = new TwelveDataPriceResponse(
                null,
                ticker,
                null,
                null,
                "error",
                "404"
        );

        when(twelveDataClient.getPrice(ticker, API_KEY)).thenReturn(Uni.createFrom().item(response));

        // When
        Throwable failure = service.getCurrentPrice(ticker, null)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.PRICE_NOT_FOUND, ex.getError());
        assertTrue(ex.getMessage().contains("TwelveData returned error"));
        verify(twelveDataClient).getPrice(ticker, API_KEY);
    }

    @Test
    void testGetCurrentPrice_NullResponse() {
        // Given
        String ticker = "NULLTEST";

        when(twelveDataClient.getPrice(ticker, API_KEY)).thenReturn(Uni.createFrom().nullItem());

        // When
        Throwable failure = service.getCurrentPrice(ticker, null)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.API_ERROR, ex.getError());
        assertTrue(ex.getMessage().contains("Invalid response from TwelveData"));
        verify(twelveDataClient).getPrice(ticker, API_KEY);
    }

    @Test
    void testGetCurrentPrice_NullPriceInResponse() {
        // Given
        String ticker = "NOPRICE";
        TwelveDataPriceResponse response = new TwelveDataPriceResponse(
                null,
                ticker,
                "1234567890",
                "2023-12-01 15:30:00",
                "ok",
                null
        );

        when(twelveDataClient.getPrice(ticker, API_KEY)).thenReturn(Uni.createFrom().item(response));

        // When
        Throwable failure = service.getCurrentPrice(ticker, null)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof ServiceException);
        ServiceException ex = (ServiceException) failure;
        assertEquals(Errors.MarketData.PRICE_NOT_FOUND, ex.getError());
        assertTrue(ex.getMessage().contains("No price available from TwelveData"));
        verify(twelveDataClient).getPrice(ticker, API_KEY);
    }

    @Test
    void testGetCurrentPrice_ClientFailure() {
        // Given
        String ticker = "NETERR";
        RuntimeException clientException = new RuntimeException("Network error");

        when(twelveDataClient.getPrice(ticker, API_KEY)).thenReturn(Uni.createFrom().failure(clientException));

        // When
        Throwable failure = service.getCurrentPrice(ticker, null)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertFailed()
                .getFailure();

        // Then
        assertTrue(failure instanceof RuntimeException);
        assertEquals("Network error", failure.getMessage());
        verify(twelveDataClient).getPrice(ticker, API_KEY);
    }

    @Test
    void testGetCurrentPrice_NotConfiguredApiKey() {
        // Given
        String ticker = "NOCONF";
        BigDecimal fallbackPrice = new BigDecimal("0.0");

        // Create a new service with not-configured API key
        TwelveDataMarketDataService testService = new TwelveDataMarketDataService();
        testService.twelveDataClient = twelveDataClient;
        testService.apiKey = "not-configured";

        // When
        BigDecimal result = testService.getCurrentPrice(ticker, null)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertEquals(fallbackPrice, result);
        verifyNoInteractions(twelveDataClient);
    }

    @Test
    void testGetCurrentPrice_TrimsAndUpperCasesTicker() {
        // Given
        String ticker = " aapl ";
        String normalizedTicker = "AAPL";
        BigDecimal expectedPrice = new BigDecimal("175.50");
        TwelveDataPriceResponse response = new TwelveDataPriceResponse(
                expectedPrice,
                normalizedTicker,
                "1234567890",
                "2023-12-01 15:30:00",
                "ok",
                null
        );

        when(twelveDataClient.getPrice(normalizedTicker, API_KEY)).thenReturn(Uni.createFrom().item(response));

        // When
        BigDecimal result = service.getCurrentPrice(ticker, null)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .getItem();

        // Then
        assertEquals(expectedPrice, result);
        verify(twelveDataClient).getPrice(normalizedTicker, API_KEY);
    }
}

