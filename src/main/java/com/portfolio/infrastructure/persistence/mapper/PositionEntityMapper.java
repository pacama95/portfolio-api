package com.portfolio.infrastructure.persistence.mapper;

import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import com.portfolio.infrastructure.persistence.entity.PositionTransactionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "cdi")
public interface PositionEntityMapper {

    @Mapping(target = "sharesOwned", source = "sharesOwned")
    @Mapping(target = "averageCostPerShare", source = "averageCostPerShare")
    @Mapping(target = "latestMarketPrice", source = "latestMarketPrice")
    @Mapping(target = "totalInvestedAmount", source = "totalInvestedAmount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "lastUpdated", source = "updatedAt")
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "transactions", ignore = true)
    Position toDomain(PositionEntity entity);

    @Mapping(target = "sharesOwned", source = "sharesOwned")
    @Mapping(target = "averageCostPerShare", source = "averageCostPerShare")
    @Mapping(target = "latestMarketPrice", source = "latestMarketPrice")
    @Mapping(target = "totalInvestedAmount", source = "totalInvestedAmount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "lastUpdated", source = "updatedAt")
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "transactions", expression = "java(mapTransactions(entity.getTransactions()))")
    Position toDomainWithTransactions(PositionEntity entity);

    @Mapping(target = "sharesOwned", source = "sharesOwned")
    @Mapping(target = "averageCostPerShare", source = "averageCostPerShare")
    @Mapping(target = "latestMarketPrice", source = "latestMarketPrice")
    @Mapping(target = "totalInvestedAmount", source = "totalInvestedAmount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "updatedAt", expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "transactions", expression = "java(setTransactions(positionEntity, domain))")
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

    default List<UUID> mapTransactions(List<PositionTransactionEntity> positionTransactions) {
        return positionTransactions.stream()
                .map(PositionTransactionEntity::getTransactionId)
                .collect(Collectors.toList());
    }

    default List<PositionTransactionEntity> setTransactions(PositionEntity entity, Position position) {
        if (position.getTransactions() == null) {
            return java.util.Collections.emptyList();
        }
        return position.getTransactions().stream()
                .map(transactionId -> new PositionTransactionEntity(entity, transactionId))
                .collect(Collectors.toList());
    }
}