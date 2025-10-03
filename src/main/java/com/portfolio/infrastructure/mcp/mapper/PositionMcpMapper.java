package com.portfolio.infrastructure.mcp.mapper;

import com.portfolio.domain.model.CurrentPosition;
import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.mcp.dto.CurrentPositionMcpDto;
import com.portfolio.infrastructure.mcp.dto.PositionMcpDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * MapStruct mapper for Position and CurrentPosition domain entities to MCP DTOs
 */
@Mapper(componentModel = "cdi")
public interface PositionMcpMapper {
    int MONETARY_SCALE = 4;
    int QUANTITY_SCALE = 6;
    RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Mapping(target = "totalQuantity", expression = "java(normalizeQuantity(position.getSharesOwned()))")
    @Mapping(target = "averagePrice", expression = "java(normalizeMonetary(position.getAverageCostPerShare()))")
    @Mapping(target = "currentPrice", expression = "java(normalizeMonetary(position.getLatestMarketPrice()))")
    @Mapping(target = "totalCost", expression = "java(normalizeMonetary(position.getTotalInvestedAmount()))")
    @Mapping(target = "marketValue", expression = "java(normalizeMonetary(position.getTotalMarketValue()))")
    @Mapping(target = "unrealizedGainLoss", expression = "java(normalizeMonetary(position.getUnrealizedGainLoss()))")
    @Mapping(target = "unrealizedGainLossPercentage", expression = "java(normalizeMonetary(position.getUnrealizedGainLossPercentage()))")
    PositionMcpDto toDto(Position position);

    List<PositionMcpDto> toDtoList(List<Position> positions);

    /**
     * Maps CurrentPosition to CurrentPositionMcpDto with real-time current price
     */
    @Mapping(target = "totalQuantity", expression = "java(normalizeQuantity(currentPosition.getSharesOwned()))")
    @Mapping(target = "averagePrice", expression = "java(normalizeMonetary(currentPosition.getAverageCostPerShare()))")
    @Mapping(target = "currentPrice", expression = "java(normalizeMonetary(currentPosition.getLatestMarketPrice()))")
    @Mapping(target = "totalCost", expression = "java(normalizeMonetary(currentPosition.getTotalInvestedAmount()))")
    @Mapping(target = "marketValue", expression = "java(normalizeMonetary(currentPosition.getTotalMarketValue()))")
    @Mapping(target = "unrealizedGainLoss", expression = "java(normalizeMonetary(currentPosition.getUnrealizedGainLoss()))")
    @Mapping(target = "unrealizedGainLossPercentage", expression = "java(normalizeMonetary(currentPosition.getUnrealizedGainLossPercentage()))")
    CurrentPositionMcpDto toDto(CurrentPosition currentPosition);

    List<CurrentPositionMcpDto> toCurrentPositionDtoList(List<CurrentPosition> currentPositions);

    // Normalization helpers
    default BigDecimal normalizeMonetary(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(MONETARY_SCALE, ROUNDING);
    }

    default BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(QUANTITY_SCALE, ROUNDING);
    }
}

