package com.portfolio.infrastructure.mcp.mapper;

import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.CurrentPosition;
import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.mcp.dto.CurrentPositionMcpDto;
import com.portfolio.infrastructure.mcp.dto.PositionMcpDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PositionMcpMapperTest {
    private final PositionMcpMapper mapper = Mappers.getMapper(PositionMcpMapper.class);

    @Test
    void testToDto_normalizesFields() {
        // Given
        Position position = new Position(
                UUID.randomUUID(),
                "GOOG",
                new BigDecimal("10.1234567"),
                new BigDecimal("100.987654"),
                new BigDecimal("101.234567"),
                new BigDecimal("1010.987654"),
                BigDecimal.ZERO,
                Currency.USD,
                LocalDate.of(2024, 3, 3),
                LocalDate.of(2024, 2, 1),
                true,
                null,
                "NASDAQ",
                "United States",
                List.of()
        );

        // When
        PositionMcpDto dto = mapper.toDto(position);

        // Then
        assertEquals(new BigDecimal("10.123457"), dto.totalQuantity()); // scale 6
        assertEquals(new BigDecimal("100.9877"), dto.averagePrice()); // scale 4
        assertEquals(new BigDecimal("101.2346"), dto.currentPrice()); // scale 4
        assertEquals(new BigDecimal("1010.9877"), dto.totalCost()); // scale 4
        assertEquals("NASDAQ", dto.exchange());
        assertEquals("United States", dto.country());
    }

    @Test
    void testCurrentPositionToDto_withRealTimePrice() {
        // Given
        CurrentPosition currentPosition = createTestCurrentPosition("AAPL", new BigDecimal("175.50"));

        // When
        CurrentPositionMcpDto dto = mapper.toDto(currentPosition);

        // Then
        assertNotNull(dto);
        assertEquals(currentPosition.getId(), dto.id());
        assertEquals("AAPL", dto.ticker());
        assertEquals(new BigDecimal("100.000000"), dto.totalQuantity()); // Normalized to scale 6
        assertEquals(new BigDecimal("150.0000"), dto.averagePrice()); // Normalized to scale 4
        assertEquals(new BigDecimal("175.5000"), dto.currentPrice()); // Real-time price normalized to scale 4
        assertEquals(new BigDecimal("15000.0000"), dto.totalCost()); // Normalized to scale 4
        assertEquals(Currency.USD, dto.currency());
        assertTrue(dto.isActive());

        // Market value should be calculated with real-time price: 100 * 175.50 = 17550
        assertEquals(new BigDecimal("17550.0000"), dto.marketValue());
        // Unrealized gain/loss: 17550 - 15000 = 2550
        assertEquals(new BigDecimal("2550.0000"), dto.unrealizedGainLoss());
        // Percentage: (2550/15000) * 100 = 17%
        assertEquals(new BigDecimal("17.0000"), dto.unrealizedGainLossPercentage());
        assertNotNull(dto.currentPriceTimestamp());
    }

    @Test
    void testCurrentPositionToDto_withFallbackPrice() {
        // Given
        Position originalPosition = new Position(
                UUID.randomUUID(),
                "MSFT",
                new BigDecimal("100"),
                new BigDecimal("150.00"),
                new BigDecimal("300.25"),
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                Currency.USD,
                LocalDate.now().minusDays(1),
                LocalDate.now().minusDays(10),
                true,
                null,
                "NYSE",
                "United States",
                List.of()
        );

        // Create CurrentPosition with fallback timestamp (stale data)
        CurrentPosition currentPosition = new CurrentPosition(
                originalPosition,
                new BigDecimal("300.25"),
                LocalDateTime.now().minusHours(2)
        );

        // When
        CurrentPositionMcpDto dto = mapper.toDto(currentPosition);

        // Then
        assertNotNull(dto);
        assertEquals("MSFT", dto.ticker());
        assertEquals(new BigDecimal("300.2500"), dto.currentPrice()); // Fallback price
        // Market value with fallback price: 100 * 300.25 = 30025
        assertEquals(new BigDecimal("30025.0000"), dto.marketValue());
        assertEquals("NYSE", dto.exchange());
        assertEquals("United States", dto.country());
    }

    @Test
    void testCurrentPositionToDtoList_withMultiplePositions() {
        // Given
        CurrentPosition position1 = createTestCurrentPosition("AAPL", new BigDecimal("175.50"));
        CurrentPosition position2 = createTestCurrentPosition("GOOGL", new BigDecimal("2650.75"));
        position2.setSharesOwned(new BigDecimal("10"));
        position2.setTotalInvestedAmount(new BigDecimal("25000.00"));

        List<CurrentPosition> positions = List.of(position1, position2);

        // When
        List<CurrentPositionMcpDto> dtos = mapper.toCurrentPositionDtoList(positions);

        // Then
        assertNotNull(dtos);
        assertEquals(2, dtos.size());

        // First position
        CurrentPositionMcpDto dto1 = dtos.getFirst();
        assertEquals("AAPL", dto1.ticker());
        assertEquals(new BigDecimal("175.5000"), dto1.currentPrice());
        assertEquals(new BigDecimal("17550.0000"), dto1.marketValue());

        // Second position
        CurrentPositionMcpDto dto2 = dtos.get(1);
        assertEquals("GOOGL", dto2.ticker());
        assertEquals(new BigDecimal("2650.7500"), dto2.currentPrice());
        // 10 * 2650.75 = 26507.5
        assertEquals(new BigDecimal("26507.5000"), dto2.marketValue());
    }

    @ParameterizedTest
    @MethodSource("currentPositionFieldNormalizationData")
    void testCurrentPositionFieldNormalization(BigDecimal inputQuantity, BigDecimal inputPrice,
                                               BigDecimal expectedQuantity, BigDecimal expectedPrice) {
        // Given
        CurrentPosition position = createTestCurrentPosition("TEST", inputPrice);
        position.setSharesOwned(inputQuantity);

        // When
        CurrentPositionMcpDto dto = mapper.toDto(position);

        // Then
        assertEquals(expectedQuantity, dto.totalQuantity());
        assertEquals(expectedPrice, dto.currentPrice());
    }

    static Stream<Arguments> currentPositionFieldNormalizationData() {
        return Stream.of(
                // quantity with excessive precision, price with excessive precision
                Arguments.of(
                        new BigDecimal("123.1234567890"),
                        new BigDecimal("456.7890123456"),
                        new BigDecimal("123.123457"), // normalized to scale 6
                        new BigDecimal("456.7890")    // normalized to scale 4
                ),
                // zero values
                Arguments.of(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.0000")
                ),
                // large numbers
                Arguments.of(
                        new BigDecimal("999999.999999"),
                        new BigDecimal("999999.9999"),
                        new BigDecimal("999999.999999"),
                        new BigDecimal("999999.9999")
                )
        );
    }

    @Test
    void testCurrentPositionToDto_withNullValues() {
        // Given
        CurrentPosition position = new CurrentPosition();
        position.setId(UUID.randomUUID());
        position.setTicker("NULL_TEST");
        position.setCurrency(Currency.USD);
        position.setIsActive(true);
        // Leave all numeric fields as null

        // When
        CurrentPositionMcpDto dto = mapper.toDto(position);

        // Then
        assertNotNull(dto);
        assertEquals("NULL_TEST", dto.ticker());
        assertNull(dto.totalQuantity());
        assertNull(dto.averagePrice());
        assertNull(dto.currentPrice());
        assertNull(dto.totalCost());
        // Market value should be zero when quantity or price is null
        assertEquals(new BigDecimal("0.0000"), dto.marketValue());
        assertEquals(new BigDecimal("0.0000"), dto.unrealizedGainLoss());
        assertEquals(new BigDecimal("0.0000"), dto.unrealizedGainLossPercentage());
    }

    @Test
    void testCurrentPositionToDto_calculatedFields() {
        // Given
        CurrentPosition position = createTestCurrentPosition("CALC_TEST", new BigDecimal("200.00"));
        position.setSharesOwned(new BigDecimal("50"));
        position.setTotalInvestedAmount(new BigDecimal("7500.00")); // 50 * 150 average price

        // When
        CurrentPositionMcpDto dto = mapper.toDto(position);

        // Then
        // Market value: 50 * 200 = 10000
        assertEquals(new BigDecimal("10000.0000"), dto.marketValue());
        // Unrealized gain: 10000 - 7500 = 2500
        assertEquals(new BigDecimal("2500.0000"), dto.unrealizedGainLoss());
        // Percentage: (2500 / 7500) * 100 = 33.33%
        assertEquals(new BigDecimal("33.3333"), dto.unrealizedGainLossPercentage());
    }

    @Test
    void testCurrentPositionToDto_negativeUnrealizedGainLoss() {
        // Given
        CurrentPosition position = createTestCurrentPosition("LOSS_TEST", new BigDecimal("120.00"));
        position.setSharesOwned(new BigDecimal("100"));
        position.setTotalInvestedAmount(new BigDecimal("15000.00")); // Higher than current market value

        // When
        CurrentPositionMcpDto dto = mapper.toDto(position);

        // Then
        // Market value: 100 * 120 = 12000
        assertEquals(new BigDecimal("12000.0000"), dto.marketValue());
        // Unrealized loss: 12000 - 15000 = -3000
        assertEquals(new BigDecimal("-3000.0000"), dto.unrealizedGainLoss());
        // Percentage: (-3000 / 15000) * 100 = -20%
        assertEquals(new BigDecimal("-20.0000"), dto.unrealizedGainLossPercentage());
    }

    @Test
    void testCurrentPositionToDto_preservesOriginalPositionFields() {
        // Given
        UUID originalId = UUID.randomUUID();
        LocalDate lastUpdated = LocalDate.of(2024, 1, 15);

        Position originalPosition = new Position(
                originalId,
                "PRESERVE_TEST",
                new BigDecimal("100"),
                new BigDecimal("150.00"),
                new BigDecimal("160.00"),
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                Currency.EUR,
                lastUpdated,
                LocalDate.of(2024, 1, 1),
                false,
                null,
                "XETRA",
                "Germany",
                List.of()
        );

        CurrentPosition currentPosition = new CurrentPosition(originalPosition, new BigDecimal("250.75"));

        // When
        CurrentPositionMcpDto dto = mapper.toDto(currentPosition);

        // Then
        assertEquals(originalId, dto.id());
        assertEquals(lastUpdated, dto.lastUpdated());
        assertFalse(dto.isActive());
        assertEquals(Currency.EUR, dto.currency());
        assertEquals(new BigDecimal("250.7500"), dto.currentPrice()); // Real-time price
        assertEquals("XETRA", dto.exchange());
        assertEquals("Germany", dto.country());
    }

    @Test
    void testCurrentPositionToDto_emptyList() {
        // Given
        List<CurrentPosition> emptyList = List.of();

        // When
        List<CurrentPositionMcpDto> dtos = mapper.toCurrentPositionDtoList(emptyList);

        // Then
        assertNotNull(dtos);
        assertTrue(dtos.isEmpty());
    }

    @Test
    void testPositionToDto_basicMapping() {
        // Given
        Position position = createTestPosition("AAPL");

        // When
        PositionMcpDto dto = mapper.toDto(position);

        // Then
        assertNotNull(dto);
        assertEquals(position.getId(), dto.id());
        assertEquals("AAPL", dto.ticker());
        assertEquals(new BigDecimal("100.000000"), dto.totalQuantity());
        assertEquals(new BigDecimal("150.0000"), dto.averagePrice());
        assertEquals(new BigDecimal("160.0000"), dto.currentPrice());
        assertEquals(new BigDecimal("15000.0000"), dto.totalCost());
    }

    @Test
    void testPositionToDtoList_withMultiplePositions() {
        // Given
        Position position1 = createTestPosition("AAPL");
        Position position2 = createTestPosition("MSFT");

        List<Position> positions = List.of(position1, position2);

        // When
        List<PositionMcpDto> dtos = mapper.toDtoList(positions);

        // Then
        assertNotNull(dtos);
        assertEquals(2, dtos.size());
        assertEquals("AAPL", dtos.get(0).ticker());
        assertEquals("MSFT", dtos.get(1).ticker());
    }

    @Test
    void testPositionToDtoList_emptyList() {
        // Given
        List<Position> emptyList = List.of();

        // When
        List<PositionMcpDto> dtos = mapper.toDtoList(emptyList);

        // Then
        assertNotNull(dtos);
        assertTrue(dtos.isEmpty());
    }

    private CurrentPosition createTestCurrentPosition(String ticker, BigDecimal realTimePrice) {
        Position originalPosition = createTestPosition(ticker);
        return new CurrentPosition(originalPosition, realTimePrice);
    }

    private Position createTestPosition(String ticker) {
        return new Position(
                UUID.randomUUID(),
                ticker,
                new BigDecimal("100"),
                new BigDecimal("150.00"),
                new BigDecimal("160.00"),
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                Currency.USD,
                LocalDate.now().minusDays(1),
                LocalDate.now().minusDays(10),
                true,
                null,
                "NASDAQ",
                "United States",
                List.of()
        );
    }
}

