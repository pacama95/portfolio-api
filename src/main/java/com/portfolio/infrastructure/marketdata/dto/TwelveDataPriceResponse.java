package com.portfolio.infrastructure.marketdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * TwelveData API response for real-time price data
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwelveDataPriceResponse(
    @JsonProperty("price") BigDecimal price,
    @JsonProperty("symbol") String symbol,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("datetime") String datetime,
    @JsonProperty("status") String status
) {
    
    /**
     * Checks if the response indicates success
     */
    public boolean isSuccessful() {
        return "ok".equalsIgnoreCase(status) && price != null;
    }
}
