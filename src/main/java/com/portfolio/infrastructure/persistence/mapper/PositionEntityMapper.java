package com.portfolio.infrastructure.persistence.mapper;

import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Mapper(componentModel = "cdi")
public interface PositionEntityMapper {
    
    @Mapping(target = "sharesOwned", source = "sharesOwned")
    @Mapping(target = "averageCostPerShare", source = "averageCostPerShare")
    @Mapping(target = "latestMarketPrice", source = "latestMarketPrice")
    @Mapping(target = "totalInvestedAmount", source = "totalInvestedAmount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "lastUpdated", source = "updatedAt")
    @Mapping(target = "isActive", constant = "true")
    Position toDomain(PositionEntity entity);

    @Mapping(target = "sharesOwned", source = "sharesOwned")
    @Mapping(target = "averageCostPerShare", source = "averageCostPerShare")
    @Mapping(target = "latestMarketPrice", source = "latestMarketPrice")
    @Mapping(target = "totalInvestedAmount", source = "totalInvestedAmount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "updatedAt", expression = "java(java.time.OffsetDateTime.now())")
    PositionEntity toEntity(Position domain);

    default BigDecimal mapUnrealizedGainLoss(Position position) {
        if (position == null || position.getSharesOwned() == null || position.getLatestMarketPrice() == null || position.getTotalInvestedAmount() == null) {
            return null;
        }
        BigDecimal marketValue = position.getSharesOwned().multiply(position.getLatestMarketPrice());
        BigDecimal costBasis = position.getTotalInvestedAmount();
        return marketValue.subtract(costBasis);
    }

    default LocalDate map(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toLocalDate() : null;
    }
} 