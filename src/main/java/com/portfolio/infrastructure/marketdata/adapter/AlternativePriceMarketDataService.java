package com.portfolio.infrastructure.marketdata.adapter;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import com.portfolio.domain.port.MarketDataService;
import com.portfolio.infrastructure.marketdata.client.AlternativePriceClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Alternative Price API implementation of MarketDataService
 */
@ApplicationScoped
@Named("alternativePrice")
public class AlternativePriceMarketDataService implements MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(AlternativePriceMarketDataService.class);

    @Inject
    @RestClient
    AlternativePriceClient alternativePriceClient;

    @Override
    public Uni<BigDecimal> getCurrentPrice(String ticker, String exchange) {
        if (ticker == null || ticker.trim().isEmpty()) {
            return Uni.createFrom().failure(
                new ServiceException(Errors.MarketData.INVALID_INPUT, "Ticker cannot be null or empty")
            );
        }
        
        log.debug("Fetching current price from alternative provider for ticker: {} with exchange: {}", ticker, exchange);

        return alternativePriceClient.getPrice(ticker.trim().toUpperCase(), exchange)
                .map(response -> {
                    if (response == null || response.price() == null) {
                        log.error("Invalid response from alternative provider for ticker: {}", ticker);
                        throw new ServiceException(
                            Errors.MarketData.PRICE_NOT_FOUND,
                            "Failed to get price from alternative provider for ticker: " + ticker
                        );
                    }
                    log.info("Successfully retrieved price {} for ticker: {} from alternative provider", response.price(), ticker);
                    return response.price();
                })
                .onFailure().invoke(throwable ->
                        log.error("Error fetching price from alternative provider for ticker: {}", ticker, throwable)
                );
    }
}

