package com.portfolio.infrastructure.mcp.mapper;

import com.portfolio.infrastructure.mcp.dto.MarketPriceMcpDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MarketPriceMcpMapperTest {
    private final MarketPriceMcpMapper mapper = Mappers.getMapper(MarketPriceMcpMapper.class);

    @Test
    void testToDto_basicMapping() {
        // Given
        String ticker = "AAPL";
        BigDecimal price = new BigDecimal("175.50");
        LocalDateTime timestamp = LocalDateTime.of(2024, 10, 3, 10, 30, 0);

        // When
        MarketPriceMcpDto dto = mapper.toDto(ticker, price, timestamp);

        // Then
        assertNotNull(dto);
        assertEquals(ticker, dto.ticker());
        assertEquals(new BigDecimal("175.5000"), dto.price()); // normalized to scale 4
        assertEquals(timestamp, dto.timestamp());
    }

    @Test
    void testToDto_normalizesPrice() {
        // Given
        String ticker = "MSFT";
        BigDecimal price = new BigDecimal("350.123456789");
        LocalDateTime timestamp = LocalDateTime.now();

        // When
        MarketPriceMcpDto dto = mapper.toDto(ticker, price, timestamp);

        // Then
        assertEquals(new BigDecimal("350.1235"), dto.price()); // normalized to scale 4
    }

    @ParameterizedTest
    @MethodSource("priceNormalizationData")
    void testToDto_variousPrecisions(BigDecimal inputPrice, BigDecimal expectedPrice) {
        // Given
        String ticker = "TEST";
        LocalDateTime timestamp = LocalDateTime.now();

        // When
        MarketPriceMcpDto dto = mapper.toDto(ticker, inputPrice, timestamp);

        // Then
        assertEquals(expectedPrice, dto.price());
    }

    static Stream<Arguments> priceNormalizationData() {
        return Stream.of(
            // Excessive precision
            Arguments.of(
                new BigDecimal("123.456789012345"),
                new BigDecimal("123.4568")
            ),
            // Zero value
            Arguments.of(
                BigDecimal.ZERO,
                new BigDecimal("0.0000")
            ),
            // Large value
            Arguments.of(
                new BigDecimal("9999.9999"),
                new BigDecimal("9999.9999")
            ),
            // Small value with many decimals
            Arguments.of(
                new BigDecimal("0.123456789"),
                new BigDecimal("0.1235")
            ),
            // Negative value (edge case)
            Arguments.of(
                new BigDecimal("-100.567890"),
                new BigDecimal("-100.5679")
            )
        );
    }

    @Test
    void testToDto_withNullPrice() {
        // Given
        String ticker = "NULL_TEST";
        BigDecimal price = null;
        LocalDateTime timestamp = LocalDateTime.now();

        // When
        MarketPriceMcpDto dto = mapper.toDto(ticker, price, timestamp);

        // Then
        assertNotNull(dto);
        assertEquals(ticker, dto.ticker());
        assertNull(dto.price());
        assertEquals(timestamp, dto.timestamp());
    }

    @Test
    void testToDto_withCurrentTimestamp() {
        // Given
        String ticker = "GOOGL";
        BigDecimal price = new BigDecimal("2650.75");
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        LocalDateTime timestamp = LocalDateTime.now();
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        // When
        MarketPriceMcpDto dto = mapper.toDto(ticker, price, timestamp);

        // Then
        assertNotNull(dto);
        assertNotNull(dto.timestamp());
        assertTrue(dto.timestamp().isAfter(before) || dto.timestamp().isEqual(before));
        assertTrue(dto.timestamp().isBefore(after) || dto.timestamp().isEqual(after));
    }

    @Test
    void testToDto_withPastTimestamp() {
        // Given
        String ticker = "TSLA";
        BigDecimal price = new BigDecimal("250.00");
        LocalDateTime pastTimestamp = LocalDateTime.of(2023, 1, 1, 12, 0, 0);

        // When
        MarketPriceMcpDto dto = mapper.toDto(ticker, price, pastTimestamp);

        // Then
        assertNotNull(dto);
        assertEquals(pastTimestamp, dto.timestamp());
        assertTrue(dto.timestamp().isBefore(LocalDateTime.now()));
    }

    @Test
    void testToDto_withFutureTimestamp() {
        // Given
        String ticker = "AMZN";
        BigDecimal price = new BigDecimal("175.50");
        LocalDateTime futureTimestamp = LocalDateTime.now().plusDays(1);

        // When
        MarketPriceMcpDto dto = mapper.toDto(ticker, price, futureTimestamp);

        // Then
        assertNotNull(dto);
        assertEquals(futureTimestamp, dto.timestamp());
        assertTrue(dto.timestamp().isAfter(LocalDateTime.now()));
    }

    @Test
    void testToDto_differentTickers() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        BigDecimal price = new BigDecimal("100.00");

        // When
        MarketPriceMcpDto dto1 = mapper.toDto("AAPL", price, timestamp);
        MarketPriceMcpDto dto2 = mapper.toDto("MSFT", price, timestamp);
        MarketPriceMcpDto dto3 = mapper.toDto("GOOGL", price, timestamp);

        // Then
        assertEquals("AAPL", dto1.ticker());
        assertEquals("MSFT", dto2.ticker());
        assertEquals("GOOGL", dto3.ticker());
        assertEquals(dto1.price(), dto2.price());
        assertEquals(dto2.price(), dto3.price());
    }

    @Test
    void testToDto_precisionEdgeCases() {
        // Given
        String ticker = "EDGE";
        LocalDateTime timestamp = LocalDateTime.now();

        // Test with price that needs rounding up (HALF_UP always rounds 5 up)
        BigDecimal price1 = new BigDecimal("100.99995");
        MarketPriceMcpDto dto1 = mapper.toDto(ticker, price1, timestamp);
        assertEquals(new BigDecimal("101.0000"), dto1.price()); // HALF_UP rounds 5 up

        // Test with price that needs rounding down
        BigDecimal price2 = new BigDecimal("100.99994");
        MarketPriceMcpDto dto2 = mapper.toDto(ticker, price2, timestamp);
        assertEquals(new BigDecimal("100.9999"), dto2.price());

        // Test with exact precision
        BigDecimal price3 = new BigDecimal("100.9999");
        MarketPriceMcpDto dto3 = mapper.toDto(ticker, price3, timestamp);
        assertEquals(new BigDecimal("100.9999"), dto3.price());
    }
}

