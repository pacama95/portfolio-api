package com.portfolio.domain.port;

import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;

/**
 * Port interface for market data operations
 */
public interface MarketDataService {

    /**
     * Gets the current market price for a ticker
     * @param ticker Stock ticker symbol (e.g., "AAPL", "MSFT")
     * @return Current market price
     */
    Uni<BigDecimal> getCurrentPrice(String ticker);
}
