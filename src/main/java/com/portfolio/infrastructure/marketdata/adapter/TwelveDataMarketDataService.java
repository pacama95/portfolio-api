package com.portfolio.infrastructure.marketdata.adapter;

import com.portfolio.domain.port.MarketDataService;
import com.portfolio.infrastructure.marketdata.client.TwelveDataClient;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * TwelveData implementation of MarketDataService
 */
@ApplicationScoped
public class TwelveDataMarketDataService implements MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataMarketDataService.class);

    @Inject
    @RestClient
    TwelveDataClient twelveDataClient;

    @ConfigProperty(name = "application.market-data.twelve-data.api-key")
    String apiKey;

    /**
     * Gets current price for a ticker with caching
     */
    @Override
    @CacheResult(cacheName = "stock-prices")
    public Uni<BigDecimal> getCurrentPrice(String ticker) {
        if (ticker == null || ticker.trim().isEmpty()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Ticker cannot be null or empty"));
        }

        if ("not-configured".equals(apiKey)) {
            log.warn("TwelveData API key not configured, returning fallback price for ticker: {}", ticker);
            return Uni.createFrom().item(BigDecimal.valueOf(100.00)); // Fallback price for development
        }

        log.debug("Fetching current price for ticker: {}", ticker);

        return twelveDataClient.getPrice(ticker.trim().toUpperCase(), apiKey)
            .map(response -> {
                if (response == null || !response.isSuccessful()) {
                    log.error("Invalid response from TwelveData for ticker: {}, response: {}", ticker, response);
                    throw new RuntimeException("Failed to get price from TwelveData for ticker: " + ticker);
                }
                
                log.info("Successfully retrieved price {} for ticker: {}", response.price(), ticker);
                return response.price();
            })
            .onFailure().invoke(throwable -> 
                log.error("Error fetching price from TwelveData for ticker: {}", ticker, throwable)
            );
    }
}
