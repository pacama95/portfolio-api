package com.portfolio.infrastructure.mcp.mapper;

import com.portfolio.domain.model.PortfolioSummary;
import com.portfolio.infrastructure.mcp.dto.PortfolioSummaryMcpDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MapStruct mapper for PortfolioSummary domain entity to MCP DTO
 */
@Mapper(componentModel = "cdi")
public interface PortfolioSummaryMcpMapper {
    int MONETARY_SCALE = 4;
    RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Mapping(target = "totalMarketValue", expression = "java(normalizeMonetary(summary.totalMarketValue()))")
    @Mapping(target = "totalCost", expression = "java(normalizeMonetary(summary.totalCost()))")
    @Mapping(target = "totalUnrealizedGainLoss", expression = "java(normalizeMonetary(summary.totalUnrealizedGainLoss()))")
    @Mapping(target = "totalUnrealizedGainLossPercentage", expression = "java(normalizeMonetary(summary.totalUnrealizedGainLossPercentage()))")
    PortfolioSummaryMcpDto toDto(PortfolioSummary summary);

    // Normalization helper
    default BigDecimal normalizeMonetary(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(MONETARY_SCALE, ROUNDING);
    }
}

