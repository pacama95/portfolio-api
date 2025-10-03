package com.portfolio.infrastructure.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MCP DTO for market price responses
 */
@RegisterForReflection
public record MarketPriceMcpDto(
    String ticker,
    BigDecimal price,
    LocalDateTime timestamp
) {}

