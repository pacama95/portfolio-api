package com.portfolio.infrastructure.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

/**
 * MCP DTO for PortfolioSummary responses
 */
@RegisterForReflection
public record PortfolioSummaryMcpDto(
    BigDecimal totalMarketValue,
    BigDecimal totalCost,
    BigDecimal totalUnrealizedGainLoss,
    BigDecimal totalUnrealizedGainLossPercentage,
    long totalPositions,
    long activePositions
) {}

