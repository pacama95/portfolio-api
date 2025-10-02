package com.portfolio.infrastructure.marketdata.adapter;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import com.portfolio.domain.port.MarketDataService;
import com.portfolio.infrastructure.marketdata.client.TwelveDataClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * TwelveData implementation of MarketDataService
 */
@ApplicationScoped
@Named("twelveData")
public class TwelveDataMarketDataService implements MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataMarketDataService.class);

    @Inject
    @RestClient
    TwelveDataClient twelveDataClient;

    @ConfigProperty(name = "application.market-data.twelve-data.api-key")
    String apiKey;

    @Override
    public Uni<BigDecimal> getCurrentPrice(String ticker, String exchange) {
        if (ticker == null || ticker.trim().isEmpty()) {
            return Uni.createFrom().failure(
                    new ServiceException(Errors.MarketData.INVALID_INPUT, "Ticker cannot be null or empty")
            );
        }

        if ("not-configured".equals(apiKey)) {
            log.warn("TwelveData API key not configured, returning fallback price for ticker: {}", ticker);
            return Uni.createFrom().item(BigDecimal.valueOf(0.0)); // Fallback price for development
        }

        log.debug("Fetching current price for ticker: {} from TwelveData", ticker);

        return twelveDataClient.getPrice(ticker.trim().toUpperCase(), apiKey)
                .map(response -> {
                    if (response == null) {
                        log.error("Invalid response from TwelveData for ticker: {}", ticker);
                        throw new ServiceException(
                                Errors.MarketData.API_ERROR,
                                "Invalid response from TwelveData for ticker: " + ticker
                        );
                    }

                    // Check if TwelveData returned an error response
                    if (response.isError()) {
                        log.warn("TwelveData returned error for ticker: {}, code: {}, status: {}",
                                ticker, response.code(), response.status());
                        throw new ServiceException(
                                Errors.MarketData.PRICE_NOT_FOUND,
                                "TwelveData returned error for ticker: " + ticker + ", code: " + response.code()
                        );
                    }

                    // Check if price is available
                    if (response.price() == null) {
                        log.warn("No price in TwelveData response for ticker: {}", ticker);
                        throw new ServiceException(
                                Errors.MarketData.PRICE_NOT_FOUND,
                                "No price available from TwelveData for ticker: " + ticker
                        );
                    }

                    log.info("Successfully retrieved price {} for ticker: {} from TwelveData", response.price(), ticker);
                    return response.price();
                })
                .onFailure().invoke(throwable ->
                        log.error("Error fetching price from TwelveData for ticker: {}", ticker, throwable)
                );
    }
}
