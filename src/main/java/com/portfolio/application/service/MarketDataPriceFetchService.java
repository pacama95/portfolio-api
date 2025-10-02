package com.portfolio.application.service;

import com.portfolio.domain.port.MarketDataService;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Service responsible for fetching market data prices with fallback logic
 * Tries TwelveData first, then falls back to Alternative provider
 */
@ApplicationScoped
@Slf4j
public class MarketDataPriceFetchService {

    private final MarketDataService twelveDataService;
    private final MarketDataService alternativePriceService;

    public MarketDataPriceFetchService(
            @Named("twelveData") MarketDataService twelveDataService,
            @Named("alternativePrice") MarketDataService alternativePriceService) {
        this.twelveDataService = twelveDataService;
        this.alternativePriceService = alternativePriceService;
    }

    /**
     * Gets current price for a ticker with caching and fallback logic
     * @param ticker Stock ticker symbol
     * @param exchange Stock exchange (can be null for TwelveData)
     * @return Current market price
     */
    @CacheResult(cacheName = "stock-prices")
    public Uni<BigDecimal> getCurrentPrice(String ticker, String exchange) {
        log.debug("Fetching current price for ticker: {} with exchange: {}", ticker, exchange);

        // First try TwelveData
        return twelveDataService.getCurrentPrice(ticker, exchange)
                .onItem().invoke(price -> 
                    log.info("Retrieved current price {} for ticker {} from TwelveData", price, ticker)
                )
                .onFailure().recoverWithUni(twelveDataError -> {
                    log.warn("TwelveData failed for ticker {}, attempting alternative provider", ticker, twelveDataError);
                    
                    // Try alternative provider as fallback
                    return alternativePriceService.getCurrentPrice(ticker, exchange)
                            .onItem().invoke(price ->
                                log.info("Retrieved current price {} for ticker {} from alternative provider", price, ticker)
                            )
                            .onFailure().invoke(alternativeError ->
                                log.error("Both providers failed for ticker {}", ticker, alternativeError)
                            );
                });
    }
}

