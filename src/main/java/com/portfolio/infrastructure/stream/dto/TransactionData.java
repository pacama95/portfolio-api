package com.portfolio.infrastructure.stream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents transaction data that can be used in events
 */
@RegisterForReflection
public record TransactionData(
    @JsonProperty("id") UUID id,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("transactionType") String transactionType,
    @JsonProperty("quantity") BigDecimal quantity,
    @JsonProperty("price") BigDecimal price,
    @JsonProperty("fees") BigDecimal fees,
    @JsonProperty("currency") String currency,
    @JsonProperty("transactionDate") LocalDate transactionDate,
    @JsonProperty("notes") String notes,
    @JsonProperty("isFractional") Boolean isFractional,
    @JsonProperty("fractionalMultiplier") BigDecimal fractionalMultiplier,
    @JsonProperty("commissionCurrency") String commissionCurrency
) {}

