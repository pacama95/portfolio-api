package com.portfolio.infrastructure.marketdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Alternative API response for stock price data
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlternativePriceResponse(
    @JsonProperty("symbol") String symbol,
    @JsonProperty("price") BigDecimal price,
    @JsonProperty("currency") String currency,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("change") BigDecimal change,
    @JsonProperty("changePercent") BigDecimal changePercent
) {
}

