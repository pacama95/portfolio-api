package com.portfolio.infrastructure.persistence.mapper;

import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionEntityMapperTest {
    private PositionEntityMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(PositionEntityMapper.class);
    }

    @Test
    void testToDomain() {
        PositionEntity entity = new PositionEntity();
        entity.setTicker("AAPL");
        entity.setSharesOwned(BigDecimal.valueOf(100));
        entity.setAverageCostPerShare(BigDecimal.valueOf(150));
        entity.setTotalInvestedAmount(BigDecimal.valueOf(20));
        entity.setCurrency(Currency.USD);
        entity.setUnrealizedGainLoss(BigDecimal.valueOf(1000));
        entity.setLatestMarketPrice(BigDecimal.valueOf(10));
        entity.setUpdatedAt(OffsetDateTime.now());

        Position position = mapper.toDomain(entity);

        assertEquals("AAPL", position.getTicker());
        assertEquals(BigDecimal.valueOf(100), position.getSharesOwned());
        assertEquals(BigDecimal.valueOf(150), position.getAverageCostPerShare());
        assertEquals(BigDecimal.valueOf(20), position.getTotalInvestedAmount());
        assertEquals(BigDecimal.valueOf(1000), position.getTotalMarketValue());
        assertEquals(Currency.USD, position.getCurrency());
        assertEquals(LocalDate.of(entity.getUpdatedAt().getYear(), entity.getUpdatedAt().getMonth(), entity.getUpdatedAt().getDayOfMonth()),
                position.getLastUpdated());
        assertEquals(BigDecimal.valueOf(980), position.getUnrealizedGainLoss());
    }
} 