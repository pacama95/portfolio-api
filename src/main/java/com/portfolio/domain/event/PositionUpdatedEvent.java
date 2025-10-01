package com.portfolio.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain event for position updates from external sources (e.g., Kafka)
 */
record PositionUpdateData(
    String ticker,
    BigDecimal quantity,
    BigDecimal averagePrice,
    BigDecimal currentPrice,
    String currency,
    LocalDateTime timestamp,
    String source
) {
    
    public static PositionUpdateData create(
        String ticker,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        String currency,
        String source
    ) {
        return new PositionUpdateData(
            ticker,
            quantity,
            averagePrice,
            currentPrice,
            currency,
            LocalDateTime.now(),
            source
        );
    }
}

/**
 * Domain event for position updates from external sources (e.g., Kafka)
 */
public class PositionUpdatedEvent extends DomainEvent<PositionUpdateData> {
    
    public PositionUpdatedEvent(PositionUpdateData data) {
        super(data);
    }

    public static PositionUpdatedEvent create(
        String ticker,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        String currency,
        String source
    ) {
        PositionUpdateData data = PositionUpdateData.create(
            ticker, quantity, averagePrice, currentPrice, currency, source
        );
        return new PositionUpdatedEvent(data);
    }

    public String getTicker() {
        return getData().ticker();
    }

    public BigDecimal getQuantity() {
        return getData().quantity();
    }

    public BigDecimal getAveragePrice() {
        return getData().averagePrice();
    }

    public BigDecimal getCurrentPrice() {
        return getData().currentPrice();
    }

    public String getCurrency() {
        return getData().currency();
    }

    public String getSource() {
        return getData().source();
    }
}
