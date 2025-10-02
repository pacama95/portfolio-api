package com.portfolio.application.service;

import com.portfolio.domain.exception.ServiceException;
import com.portfolio.domain.port.MarketDataService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketDataPriceFetchServiceTest {
    private MarketDataService twelveDataService;
    private MarketDataService alternativePriceService;
    private MarketDataPriceFetchService service;

    @BeforeEach
    void setUp() {
        twelveDataService = mock(MarketDataService.class);
        alternativePriceService = mock(MarketDataService.class);
        service = new MarketDataPriceFetchService(twelveDataService, alternativePriceService);
    }

    @Test
    void testGetCurrentPrice_TwelveDataSuccess() {
        // Given
        String ticker = "AAPL";
        String exchange = null;
        BigDecimal expectedPrice = new BigDecimal("175.50");
        
        when(twelveDataService.getCurrentPrice(ticker, exchange))
            .thenReturn(Uni.createFrom().item(expectedPrice));

        // When
        Uni<BigDecimal> uni = service.getCurrentPrice(ticker, exchange);
        BigDecimal result = uni.subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();

        // Then
        assertNotNull(result);
        assertEquals(expectedPrice, result);
        verify(twelveDataService).getCurrentPrice(ticker, exchange);
        verifyNoInteractions(alternativePriceService);
    }

    @Test
    void testGetCurrentPrice_TwelveDataFails_AlternativeSucceeds() {
        // Given
        String ticker = "MSFT";
        String exchange = "BME";
        BigDecimal expectedPrice = new BigDecimal("305.75");
        ServiceException twelveDataError = new ServiceException(
            new com.portfolio.domain.exception.Error("0602"), 
            "TwelveData failed"
        );
        
        when(twelveDataService.getCurrentPrice(ticker, exchange))
            .thenReturn(Uni.createFrom().failure(twelveDataError));
        when(alternativePriceService.getCurrentPrice(ticker, exchange))
            .thenReturn(Uni.createFrom().item(expectedPrice));

        // When
        Uni<BigDecimal> uni = service.getCurrentPrice(ticker, exchange);
        BigDecimal result = uni.subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();

        // Then
        assertNotNull(result);
        assertEquals(expectedPrice, result);
        verify(twelveDataService).getCurrentPrice(ticker, exchange);
        verify(alternativePriceService).getCurrentPrice(ticker, exchange);
    }

    @Test
    void testGetCurrentPrice_BothProvidersFail() {
        // Given
        String ticker = "TSLA";
        String exchange = "NASDAQ";
        ServiceException twelveDataError = new ServiceException(
            new com.portfolio.domain.exception.Error("0602"), 
            "TwelveData failed"
        );
        ServiceException alternativeError = new ServiceException(
            new com.portfolio.domain.exception.Error("0602"), 
            "Alternative provider failed"
        );
        
        when(twelveDataService.getCurrentPrice(ticker, exchange))
            .thenReturn(Uni.createFrom().failure(twelveDataError));
        when(alternativePriceService.getCurrentPrice(ticker, exchange))
            .thenReturn(Uni.createFrom().failure(alternativeError));

        // When
        Uni<BigDecimal> uni = service.getCurrentPrice(ticker, exchange);
        Throwable failure = uni.subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertFailed()
            .getFailure();

        // Then
        assertNotNull(failure);
        assertTrue(failure instanceof ServiceException);
        ServiceException serviceException = (ServiceException) failure;
        assertEquals("Alternative provider failed", serviceException.getMessage());
        verify(twelveDataService).getCurrentPrice(ticker, exchange);
        verify(alternativePriceService).getCurrentPrice(ticker, exchange);
    }

    @Test
    void testGetCurrentPrice_WithExchange() {
        // Given
        String ticker = "ITX.MC";
        String exchange = "BME";
        BigDecimal expectedPrice = new BigDecimal("47.80");
        
        when(twelveDataService.getCurrentPrice(ticker, exchange))
            .thenReturn(Uni.createFrom().item(expectedPrice));

        // When
        Uni<BigDecimal> uni = service.getCurrentPrice(ticker, exchange);
        BigDecimal result = uni.subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();

        // Then
        assertNotNull(result);
        assertEquals(expectedPrice, result);
        verify(twelveDataService).getCurrentPrice(ticker, exchange);
        verifyNoInteractions(alternativePriceService);
    }
}

