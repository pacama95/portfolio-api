package com.portfolio.infrastructure.mcp.mapper;

import com.portfolio.infrastructure.mcp.dto.MarketPriceMcpDto;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * MapStruct mapper for market price data to MCP DTO
 */
@Mapper(componentModel = "cdi")
public interface MarketPriceMcpMapper {
    int MONETARY_SCALE = 4;
    RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Creates a MarketPriceMcpDto from individual components
     */
    default MarketPriceMcpDto toDto(String ticker, BigDecimal price, LocalDateTime timestamp) {
        return new MarketPriceMcpDto(
            ticker,
            normalizeMonetary(price),
            timestamp
        );
    }

    // Normalization helper
    default BigDecimal normalizeMonetary(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(MONETARY_SCALE, ROUNDING);
    }
}

