package com.portfolio.infrastructure.mcp.dto;

import com.portfolio.domain.model.Currency;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MCP DTO for CurrentPosition responses
 */
@RegisterForReflection
public record CurrentPositionMcpDto(
    UUID id,
    String ticker,
    BigDecimal totalQuantity,
    BigDecimal averagePrice,
    BigDecimal currentPrice,
    BigDecimal totalCost,
    Currency currency,
    LocalDate lastUpdated,
    Boolean isActive,
    BigDecimal marketValue,
    BigDecimal unrealizedGainLoss,
    BigDecimal unrealizedGainLossPercentage,
    LocalDateTime currentPriceTimestamp,
    String exchange,
    String country
) {}

