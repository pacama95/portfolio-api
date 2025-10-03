package com.portfolio.infrastructure.mcp.mapper;

import com.portfolio.domain.model.PortfolioSummary;
import com.portfolio.infrastructure.mcp.dto.PortfolioSummaryMcpDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioSummaryMcpMapperTest {
    private final PortfolioSummaryMcpMapper mapper = Mappers.getMapper(PortfolioSummaryMcpMapper.class);

    @Test
    void testToDto_normalizesFields() {
        // Given
        PortfolioSummary summary = new PortfolioSummary(
            new BigDecimal("12345.67891"),
            new BigDecimal("12000.12345"),
            new BigDecimal("345.555555"),
            new BigDecimal("2.879999"),
            10L,
            8L
        );

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertEquals(new BigDecimal("12345.6789"), dto.totalMarketValue()); // scale 4
        assertEquals(new BigDecimal("12000.1235"), dto.totalCost()); // scale 4
        assertEquals(new BigDecimal("345.5556"), dto.totalUnrealizedGainLoss()); // scale 4
        assertEquals(new BigDecimal("2.8800"), dto.totalUnrealizedGainLossPercentage()); // scale 4
        assertEquals(10L, dto.totalPositions());
        assertEquals(8L, dto.activePositions());
    }

    @Test
    void testToDto_withZeroValues() {
        // Given
        PortfolioSummary summary = PortfolioSummary.empty();

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertEquals(new BigDecimal("0.0000"), dto.totalMarketValue());
        assertEquals(new BigDecimal("0.0000"), dto.totalCost());
        assertEquals(new BigDecimal("0.0000"), dto.totalUnrealizedGainLoss());
        assertEquals(new BigDecimal("0.0000"), dto.totalUnrealizedGainLossPercentage());
        assertEquals(0L, dto.totalPositions());
        assertEquals(0L, dto.activePositions());
    }

    @Test
    void testToDto_withPositiveGains() {
        // Given
        PortfolioSummary summary = new PortfolioSummary(
            new BigDecimal("125000.00"),
            new BigDecimal("110000.00"),
            new BigDecimal("15000.00"),
            new BigDecimal("13.636363"),
            10L,
            8L
        );

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertEquals(new BigDecimal("125000.0000"), dto.totalMarketValue());
        assertEquals(new BigDecimal("110000.0000"), dto.totalCost());
        assertEquals(new BigDecimal("15000.0000"), dto.totalUnrealizedGainLoss());
        assertEquals(new BigDecimal("13.6364"), dto.totalUnrealizedGainLossPercentage());
        assertEquals(10L, dto.totalPositions());
        assertEquals(8L, dto.activePositions());
    }

    @Test
    void testToDto_withNegativeLosses() {
        // Given
        PortfolioSummary summary = new PortfolioSummary(
            new BigDecimal("95000.00"),
            new BigDecimal("110000.00"),
            new BigDecimal("-15000.00"),
            new BigDecimal("-13.636363"),
            10L,
            8L
        );

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertEquals(new BigDecimal("95000.0000"), dto.totalMarketValue());
        assertEquals(new BigDecimal("110000.0000"), dto.totalCost());
        assertEquals(new BigDecimal("-15000.0000"), dto.totalUnrealizedGainLoss());
        assertEquals(new BigDecimal("-13.6364"), dto.totalUnrealizedGainLossPercentage());
    }

    @ParameterizedTest
    @MethodSource("normalizationTestData")
    void testToDto_variousPrecisions(BigDecimal inputMarketValue, BigDecimal inputCost,
                                     BigDecimal expectedMarketValue, BigDecimal expectedCost) {
        // Given
        PortfolioSummary summary = new PortfolioSummary(
            inputMarketValue,
            inputCost,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            5L,
            3L
        );

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertEquals(expectedMarketValue, dto.totalMarketValue());
        assertEquals(expectedCost, dto.totalCost());
    }

    static Stream<Arguments> normalizationTestData() {
        return Stream.of(
            // Excessive precision
            Arguments.of(
                new BigDecimal("12345.678901234"),
                new BigDecimal("12000.123456789"),
                new BigDecimal("12345.6789"),
                new BigDecimal("12000.1235")
            ),
            // Minimal precision
            Arguments.of(
                new BigDecimal("100"),
                new BigDecimal("50"),
                new BigDecimal("100.0000"),
                new BigDecimal("50.0000")
            ),
            // Large numbers (HALF_UP rounding: 999999 rounds up to 1000000.0000)
            Arguments.of(
                new BigDecimal("1000000.999999"),
                new BigDecimal("999999.111111"),
                new BigDecimal("1000001.0000"), // HALF_UP rounds .999999 up to 1.0000
                new BigDecimal("999999.1111")
            )
        );
    }

    @Test
    void testToDto_withNullValues() {
        // Given - create a summary with null BigDecimal values (edge case)
        // Note: The PortfolioSummary record doesn't typically allow nulls, but we test the mapper's resilience
        PortfolioSummary summary = new PortfolioSummary(
            null,
            null,
            null,
            null,
            0L,
            0L
        );

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertNull(dto.totalMarketValue());
        assertNull(dto.totalCost());
        assertNull(dto.totalUnrealizedGainLoss());
        assertNull(dto.totalUnrealizedGainLossPercentage());
        assertEquals(0L, dto.totalPositions());
        assertEquals(0L, dto.activePositions());
    }

    @Test
    void testToDto_allPositionsActive() {
        // Given
        PortfolioSummary summary = new PortfolioSummary(
            new BigDecimal("100000.00"),
            new BigDecimal("95000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("5.263157"),
            5L,
            5L // All positions are active
        );

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertEquals(5L, dto.totalPositions());
        assertEquals(5L, dto.activePositions());
    }

    @Test
    void testToDto_noActivePositions() {
        // Given
        PortfolioSummary summary = new PortfolioSummary(
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            5L,
            0L // No active positions
        );

        // When
        PortfolioSummaryMcpDto dto = mapper.toDto(summary);

        // Then
        assertEquals(5L, dto.totalPositions());
        assertEquals(0L, dto.activePositions());
    }
}

