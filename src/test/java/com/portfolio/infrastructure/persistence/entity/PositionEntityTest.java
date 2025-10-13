package com.portfolio.infrastructure.persistence.entity;

import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PositionEntityTest {

    @Test
    void testUpdate_UpdatesAllFields() {
        // Given
        PositionEntity entity = new PositionEntity();
        entity.setTicker("OLD");
        entity.setSharesOwned(BigDecimal.TEN);
        entity.setAverageCostPerShare(new BigDecimal("100.00"));
        entity.setCurrency(Currency.USD);
        entity.setTransactions(new ArrayList<>());

        Position position = PositionMother.createComplete();

        // When
        entity.update(position);

        // Then
        assertEquals(position.getTicker(), entity.getTicker());
        assertEquals(position.getSharesOwned(), entity.getSharesOwned());
        assertEquals(position.getAverageCostPerShare(), entity.getAverageCostPerShare());
        assertEquals(position.getCurrency(), entity.getCurrency());
        assertEquals(position.getTotalInvestedAmount(), entity.getTotalInvestedAmount());
        assertEquals(position.getTotalTransactionFees(), entity.getTotalTransactionFees());
        assertEquals(position.getFirstPurchaseDate(), entity.getFirstPurchaseDate());
        assertEquals(position.getUnrealizedGainLoss(), entity.getUnrealizedGainLoss());
        assertEquals(position.getTotalMarketValue(), entity.getTotalMarketValue());
        assertEquals(position.getLatestMarketPrice(), entity.getLatestMarketPrice());
        assertEquals(position.getLastEventAppliedAt(), entity.getLastEventAppliedAt());
        assertEquals(position.getExchange(), entity.getExchange());
        assertEquals(position.getCountry(), entity.getCountry());
    }

    @Test
    void testUpdate_AddsNewTransactions() {
        // Given
        PositionEntity entity = new PositionEntity();
        entity.setTicker("AAPL");
        entity.setTransactions(new ArrayList<>());

        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        UUID tx3 = UUID.randomUUID();
        List<UUID> transactionIds = List.of(tx1, tx2, tx3);

        Position position = PositionMother.createWithTransactions(transactionIds);

        // When
        entity.update(position);

        // Then
        assertEquals(3, entity.getTransactions().size());
        List<UUID> entityTransactionIds = entity.getTransactions().stream()
                .map(PositionTransactionEntity::getTransactionId)
                .toList();
        assertTrue(entityTransactionIds.containsAll(transactionIds));
    }

    @Test
    void testUpdate_RemovesDeletedTransactions() {
        // Given
        PositionEntity entity = new PositionEntity();
        entity.setTicker("AAPL");

        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        UUID tx3 = UUID.randomUUID();

        List<PositionTransactionEntity> existingTransactions = new ArrayList<>();
        existingTransactions.add(new PositionTransactionEntity(entity, tx1));
        existingTransactions.add(new PositionTransactionEntity(entity, tx2));
        existingTransactions.add(new PositionTransactionEntity(entity, tx3));
        entity.setTransactions(existingTransactions);

        // Position only has tx1 and tx2 (tx3 should be removed)
        Position position = PositionMother.createWithTransactions(List.of(tx1, tx2));

        // When
        entity.update(position);

        // Then
        assertEquals(2, entity.getTransactions().size());
        List<UUID> entityTransactionIds = entity.getTransactions().stream()
                .map(PositionTransactionEntity::getTransactionId)
                .toList();
        assertTrue(entityTransactionIds.contains(tx1));
        assertTrue(entityTransactionIds.contains(tx2));
        assertFalse(entityTransactionIds.contains(tx3));
    }

    @Test
    void testUpdate_SynchronizesTransactions_AddsAndRemoves() {
        // Given
        PositionEntity entity = new PositionEntity();
        entity.setTicker("AAPL");

        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        UUID tx3 = UUID.randomUUID();
        UUID tx4 = UUID.randomUUID();

        // Entity has tx1, tx2, tx3
        List<PositionTransactionEntity> existingTransactions = new ArrayList<>();
        existingTransactions.add(new PositionTransactionEntity(entity, tx1));
        existingTransactions.add(new PositionTransactionEntity(entity, tx2));
        existingTransactions.add(new PositionTransactionEntity(entity, tx3));
        entity.setTransactions(existingTransactions);

        // Position has tx2, tx3, tx4 (remove tx1, keep tx2 and tx3, add tx4)
        Position position = PositionMother.createWithTransactions(List.of(tx2, tx3, tx4));

        // When
        entity.update(position);

        // Then
        assertEquals(3, entity.getTransactions().size());
        List<UUID> entityTransactionIds = entity.getTransactions().stream()
                .map(PositionTransactionEntity::getTransactionId)
                .toList();
        assertFalse(entityTransactionIds.contains(tx1)); // Removed
        assertTrue(entityTransactionIds.contains(tx2));  // Kept
        assertTrue(entityTransactionIds.contains(tx3));  // Kept
        assertTrue(entityTransactionIds.contains(tx4));  // Added
    }

    @Test
    void testUpdate_WithEmptyTransactionsList() {
        // Given
        PositionEntity entity = new PositionEntity();
        entity.setTicker("AAPL");

        UUID tx1 = UUID.randomUUID();
        List<PositionTransactionEntity> existingTransactions = new ArrayList<>();
        existingTransactions.add(new PositionTransactionEntity(entity, tx1));
        entity.setTransactions(existingTransactions);

        // Position has no transactions
        Position position = PositionMother.createWithTransactions(List.of());

        // When
        entity.update(position);

        // Then
        assertTrue(entity.getTransactions().isEmpty());
    }

    @Test
    void testUpdate_PreservesExistingTransactionReferences() {
        // Given
        PositionEntity entity = new PositionEntity();
        entity.setTicker("AAPL");

        UUID tx1 = UUID.randomUUID();
        PositionTransactionEntity existingTx = new PositionTransactionEntity(entity, tx1);
        List<PositionTransactionEntity> existingTransactions = new ArrayList<>();
        existingTransactions.add(existingTx);
        entity.setTransactions(existingTransactions);

        // Position still has tx1
        Position position = PositionMother.createWithTransactions(List.of(tx1));

        // When
        entity.update(position);

        // Then
        assertEquals(1, entity.getTransactions().size());
        assertSame(existingTx, entity.getTransactions().getFirst()); // Same reference preserved
    }

    @Test
    void testUpdate_WithNullOptionalFields() {
        // Given
        PositionEntity entity = new PositionEntity();
        entity.setTicker("AAPL");
        entity.setExchange("NASDAQ");
        entity.setCountry("US");
        entity.setTransactions(new ArrayList<>());

        Position position = PositionMother.createMinimal();

        // When
        entity.update(position);

        // Then
        assertNull(entity.getExchange());
        assertNull(entity.getCountry());
        assertNull(entity.getLastEventAppliedAt());
    }

    @Test
    void testAddTransaction_SetsPositionReference() {
        // Given
        PositionEntity entity = new PositionEntity();
        PositionTransactionEntity tx = new PositionTransactionEntity();
        tx.setTransactionId(UUID.randomUUID());

        // When
        entity.addTransaction(tx);

        // Then
        assertEquals(entity, tx.getPosition());
        assertTrue(entity.getTransactions().contains(tx));
    }

    @Test
    void testRemoveTransaction_ClearsPositionReference() {
        // Given
        PositionEntity entity = new PositionEntity();
        PositionTransactionEntity tx = new PositionTransactionEntity();
        tx.setTransactionId(UUID.randomUUID());
        entity.addTransaction(tx);

        // When
        entity.removeTransaction(tx);

        // Then
        assertNull(tx.getPosition());
        assertFalse(entity.getTransactions().contains(tx));
    }

    // Mother Object
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

        static Position createWithTransactions(List<UUID> transactionIds) {
            Position position = createComplete();
            return new Position(
                    position.getId(), position.getTicker(), position.getSharesOwned(),
                    position.getAverageCostPerShare(), position.getLatestMarketPrice(),
                    position.getTotalInvestedAmount(), position.getTotalTransactionFees(),
                    position.getCurrency(), position.getLastUpdated(), position.getFirstPurchaseDate(),
                    position.getIsActive(), position.getLastEventAppliedAt(), position.getExchange(),
                    position.getCountry(), new ArrayList<>(transactionIds)
            );
        }
    }
}
