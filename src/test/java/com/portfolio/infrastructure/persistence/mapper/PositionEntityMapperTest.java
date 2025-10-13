package com.portfolio.infrastructure.persistence.mapper;

import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import com.portfolio.infrastructure.persistence.entity.PositionTransactionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PositionEntityMapperTest {
    private PositionEntityMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(PositionEntityMapper.class);
    }

    @Test
    void testToDomain_MapsAllFieldsCorrectly() {
        // Given
        PositionEntity entity = PositionEntityMother.createComplete();

        // When
        Position position = mapper.toDomain(entity);

        // Then
        assertEquals(entity.getId(), position.getId());
        assertEquals(entity.getTicker(), position.getTicker());
        assertEquals(entity.getSharesOwned(), position.getSharesOwned());
        assertEquals(entity.getAverageCostPerShare(), position.getAverageCostPerShare());
        assertEquals(entity.getLatestMarketPrice(), position.getLatestMarketPrice());
        assertEquals(entity.getTotalInvestedAmount(), position.getTotalInvestedAmount());
        assertEquals(entity.getTotalTransactionFees(), position.getTotalTransactionFees());
        assertEquals(entity.getCurrency(), position.getCurrency());
        assertEquals(entity.getUpdatedAt().toLocalDate(), position.getLastUpdated());
        assertEquals(entity.getFirstPurchaseDate(), position.getFirstPurchaseDate());
        assertTrue(position.getIsActive());
        assertEquals(entity.getLastEventAppliedAt(), position.getLastEventAppliedAt());
        assertEquals(entity.getExchange(), position.getExchange());
        assertEquals(entity.getCountry(), position.getCountry());
        assertNull(position.getTransactions()); // Transactions are ignored in toDomain
    }

    @Test
    void testToDomain_WithNullOptionalFields() {
        // Given
        PositionEntity entity = PositionEntityMother.createMinimal();

        // When
        Position position = mapper.toDomain(entity);

        // Then
        assertEquals(entity.getTicker(), position.getTicker());
        assertEquals(entity.getSharesOwned(), position.getSharesOwned());
        assertEquals(entity.getCurrency(), position.getCurrency());
        assertNull(position.getExchange());
        assertNull(position.getCountry());
        assertNull(position.getLastEventAppliedAt());
    }

    @Test
    void testToDomainWithTransactions_MapsAllFieldsIncludingTransactions() {
        // Given
        PositionEntity entity = PositionEntityMother.createWithTransactions();

        // When
        Position position = mapper.toDomainWithTransactions(entity);

        // Then
        assertEquals(entity.getId(), position.getId());
        assertEquals(entity.getTicker(), position.getTicker());
        assertEquals(entity.getSharesOwned(), position.getSharesOwned());
        assertEquals(entity.getAverageCostPerShare(), position.getAverageCostPerShare());
        assertEquals(entity.getCurrency(), position.getCurrency());
        assertTrue(position.getIsActive());

        // Verify transactions are mapped
        assertNotNull(position.getTransactions());
        assertEquals(3, position.getTransactions().size());

        List<UUID> expectedTransactionIds = entity.getTransactions().stream()
                .map(PositionTransactionEntity::getTransactionId)
                .toList();
        assertTrue(position.getTransactions().containsAll(expectedTransactionIds));
    }

    @Test
    void testToDomainWithTransactions_WithEmptyTransactions() {
        // Given
        PositionEntity entity = PositionEntityMother.createComplete();
        entity.setTransactions(new ArrayList<>());

        // When
        Position position = mapper.toDomainWithTransactions(entity);

        // Then
        assertNotNull(position.getTransactions());
        assertTrue(position.getTransactions().isEmpty());
    }

    @Test
    void testToEntity_MapsAllFieldsCorrectly() {
        // Given
        Position domain = PositionMother.createComplete();

        // When
        PositionEntity entity = mapper.toEntity(domain);

        // Then
        assertEquals(domain.getId(), entity.getId());
        assertEquals(domain.getTicker(), entity.getTicker());
        assertEquals(domain.getSharesOwned(), entity.getSharesOwned());
        assertEquals(domain.getAverageCostPerShare(), entity.getAverageCostPerShare());
        assertEquals(domain.getLatestMarketPrice(), entity.getLatestMarketPrice());
        assertEquals(domain.getTotalInvestedAmount(), entity.getTotalInvestedAmount());
        assertEquals(domain.getTotalTransactionFees(), entity.getTotalTransactionFees());
        assertEquals(domain.getCurrency(), entity.getCurrency());
        assertEquals(domain.getFirstPurchaseDate(), entity.getFirstPurchaseDate());
        assertEquals(domain.getLastEventAppliedAt(), entity.getLastEventAppliedAt());
        assertEquals(domain.getExchange(), entity.getExchange());
        assertEquals(domain.getCountry(), entity.getCountry());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void testToEntity_WithTransactions() {
        // Given
        Position domain = PositionMother.createWithTransactions();

        // When
        PositionEntity entity = mapper.toEntity(domain);

        // Then
        assertNotNull(entity.getTransactions());
        assertEquals(3, entity.getTransactions().size());

        List<UUID> expectedTransactionIds = domain.getTransactions();
        List<UUID> actualTransactionIds = entity.getTransactions().stream()
                .map(PositionTransactionEntity::getTransactionId)
                .toList();

        assertTrue(actualTransactionIds.containsAll(expectedTransactionIds));
    }

    @Test
    void testToEntity_WithNullTransactions() {
        // Given
        Position domain = PositionMother.createMinimal();

        // When
        PositionEntity entity = mapper.toEntity(domain);

        // Then
        assertNotNull(entity.getTransactions());
        assertTrue(entity.getTransactions().isEmpty());
    }

    @Test
    void testMapUnrealizedGainLoss_CalculatesCorrectly() {
        // Given
        Position position = PositionMother.createComplete();

        // When
        BigDecimal result = mapper.mapUnrealizedGainLoss(position);

        // Then
        BigDecimal expected = position.getSharesOwned()
                .multiply(position.getLatestMarketPrice())
                .subtract(position.getTotalInvestedAmount());
        assertEquals(expected, result);
    }

    @Test
    void testMapUnrealizedGainLoss_WithNullPosition() {
        // When
        BigDecimal result = mapper.mapUnrealizedGainLoss(null);

        // Then
        assertNull(result);
    }

    @Test
    void testMapUnrealizedGainLoss_WithNullFields() {
        // Given
        Position position = new Position("AAPL", Currency.USD);

        // When
        BigDecimal result = mapper.mapUnrealizedGainLoss(position);

        // Then
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testMapOffsetDateTimeToLocalDate_WithValidDate() {
        // Given
        OffsetDateTime offsetDateTime = OffsetDateTime.now();

        // When
        LocalDate result = mapper.map(offsetDateTime);

        // Then
        assertEquals(offsetDateTime.toLocalDate(), result);
    }

    @Test
    void testMapOffsetDateTimeToLocalDate_WithNull() {
        // When
        LocalDate result = mapper.map(null);

        // Then
        assertNull(result);
    }

    @Test
    void testMapTransactions_WithMultipleTransactions() {
        // Given
        PositionEntity entity = PositionEntityMother.createComplete();
        List<PositionTransactionEntity> transactions = new ArrayList<>();
        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        UUID tx3 = UUID.randomUUID();

        transactions.add(new PositionTransactionEntity(entity, tx1));
        transactions.add(new PositionTransactionEntity(entity, tx2));
        transactions.add(new PositionTransactionEntity(entity, tx3));

        // When
        List<UUID> result = mapper.mapTransactions(transactions);

        // Then
        assertEquals(3, result.size());
        assertTrue(result.contains(tx1));
        assertTrue(result.contains(tx2));
        assertTrue(result.contains(tx3));
    }

    @Test
    void testMapTransactions_WithEmptyList() {
        // Given
        List<PositionTransactionEntity> transactions = new ArrayList<>();

        // When
        List<UUID> result = mapper.mapTransactions(transactions);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSetTransactions_WithMultipleTransactionIds() {
        // Given
        PositionEntity entity = PositionEntityMother.createComplete();
        Position position = PositionMother.createWithTransactions();

        // When
        List<PositionTransactionEntity> result = mapper.setTransactions(entity, position);

        // Then
        assertEquals(3, result.size());
        result.forEach(tx -> {
            assertEquals(entity, tx.getPosition());
            assertTrue(position.getTransactions().contains(tx.getTransactionId()));
        });
    }

    @Test
    void testSetTransactions_WithNullTransactions() {
        // Given
        PositionEntity entity = PositionEntityMother.createComplete();
        Position position = PositionMother.createMinimal();

        // When
        List<PositionTransactionEntity> result = mapper.setTransactions(entity, position);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // Mother Objects
    private static class PositionEntityMother {
        static PositionEntity createComplete() {
            PositionEntity entity = new PositionEntity();
            entity.setId(UUID.randomUUID());
            entity.setTicker("AAPL");
            entity.setSharesOwned(new BigDecimal("100.000000"));
            entity.setAverageCostPerShare(new BigDecimal("150.5000"));
            entity.setLatestMarketPrice(new BigDecimal("175.2500"));
            entity.setTotalInvestedAmount(new BigDecimal("15050.00"));
            entity.setTotalTransactionFees(new BigDecimal("10.50"));
            entity.setCurrency(Currency.USD);
            entity.setFirstPurchaseDate(LocalDate.of(2024, 1, 15));
            entity.setUpdatedAt(OffsetDateTime.now());
            entity.setLastEventAppliedAt(Instant.now());
            entity.setExchange("NASDAQ");
            entity.setCountry("US");
            entity.setTransactions(new ArrayList<>());
            return entity;
        }

        static PositionEntity createMinimal() {
            PositionEntity entity = new PositionEntity();
            entity.setId(UUID.randomUUID());
            entity.setTicker("MSFT");
            entity.setSharesOwned(new BigDecimal("50.000000"));
            entity.setAverageCostPerShare(new BigDecimal("200.0000"));
            entity.setLatestMarketPrice(new BigDecimal("220.0000"));
            entity.setTotalInvestedAmount(new BigDecimal("10000.00"));
            entity.setTotalTransactionFees(BigDecimal.ZERO);
            entity.setCurrency(Currency.USD);
            entity.setFirstPurchaseDate(LocalDate.now());
            entity.setUpdatedAt(OffsetDateTime.now());
            entity.setTransactions(new ArrayList<>());
            return entity;
        }

        static PositionEntity createWithTransactions() {
            PositionEntity entity = createComplete();
            List<PositionTransactionEntity> transactions = new ArrayList<>();
            transactions.add(new PositionTransactionEntity(entity, UUID.randomUUID()));
            transactions.add(new PositionTransactionEntity(entity, UUID.randomUUID()));
            transactions.add(new PositionTransactionEntity(entity, UUID.randomUUID()));
            entity.setTransactions(transactions);
            return entity;
        }
    }

    private static class PositionMother {
        static Position createComplete() {
            UUID id = UUID.randomUUID();
            String ticker = "AAPL";
            BigDecimal sharesOwned = new BigDecimal("100.000000");
            BigDecimal averageCostPerShare = new BigDecimal("150.5000");
            BigDecimal latestMarketPrice = new BigDecimal("175.2500");
            BigDecimal totalInvestedAmount = new BigDecimal("15050.00");
            BigDecimal totalTransactionFees = new BigDecimal("10.50");
            Currency currency = Currency.USD;
            LocalDate lastUpdated = LocalDate.now();
            LocalDate firstPurchaseDate = LocalDate.of(2024, 1, 15);
            Boolean isActive = true;
            Instant lastEventAppliedAt = Instant.now();
            String exchange = "NASDAQ";
            String country = "US";
            List<UUID> transactions = new ArrayList<>();

            return new Position(
                    id, ticker, sharesOwned, averageCostPerShare, latestMarketPrice,
                    totalInvestedAmount, totalTransactionFees, currency, lastUpdated,
                    firstPurchaseDate, isActive, lastEventAppliedAt, exchange, country, transactions
            );
        }

        static Position createMinimal() {
            UUID id = UUID.randomUUID();
            String ticker = "MSFT";
            BigDecimal sharesOwned = new BigDecimal("50.000000");
            BigDecimal averageCostPerShare = new BigDecimal("200.0000");
            BigDecimal latestMarketPrice = new BigDecimal("220.0000");
            BigDecimal totalInvestedAmount = new BigDecimal("10000.00");
            BigDecimal totalTransactionFees = BigDecimal.ZERO;
            Currency currency = Currency.USD;
            LocalDate lastUpdated = LocalDate.now();
            LocalDate firstPurchaseDate = LocalDate.now();
            Boolean isActive = true;
            List<UUID> transactions = new ArrayList<>();

            return new Position(
                    id, ticker, sharesOwned, averageCostPerShare, latestMarketPrice,
                    totalInvestedAmount, totalTransactionFees, currency, lastUpdated,
                    firstPurchaseDate, isActive, null, null, null, transactions
            );
        }

        static Position createWithTransactions() {
            Position position = createComplete();
            List<UUID> transactions = new ArrayList<>();
            transactions.add(UUID.randomUUID());
            transactions.add(UUID.randomUUID());
            transactions.add(UUID.randomUUID());

            return new Position(
                    position.getId(), position.getTicker(), position.getSharesOwned(),
                    position.getAverageCostPerShare(), position.getLatestMarketPrice(),
                    position.getTotalInvestedAmount(), position.getTotalTransactionFees(),
                    position.getCurrency(), position.getLastUpdated(), position.getFirstPurchaseDate(),
                    position.getIsActive(), position.getLastEventAppliedAt(), position.getExchange(),
                    position.getCountry(), transactions
            );
        }
    }
} 