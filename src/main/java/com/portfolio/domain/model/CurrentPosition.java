package com.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.math.RoundingMode;

/**
 * CurrentPosition domain entity representing a position with real-time market data
 */
public class CurrentPosition {
    private UUID id;
    private String ticker;
    private BigDecimal sharesOwned;
    private BigDecimal averageCostPerShare;
    private BigDecimal latestMarketPrice;
    private BigDecimal totalInvestedAmount;
    private Currency currency;
    private LocalDate lastUpdated;
    private Boolean isActive;
    private LocalDateTime currentPriceTimestamp;
    private String exchange;
    private String country;

    // Default constructor
    public CurrentPosition() {
        this.isActive = true;
        this.currentPriceTimestamp = LocalDateTime.now();
    }

    // Constructor from Position with updated current price
    public CurrentPosition(Position position, BigDecimal realTimeCurrentPrice) {
        this.id = position.getId();
        this.ticker = position.getTicker();
        this.sharesOwned = position.getSharesOwned();
        this.averageCostPerShare = position.getAverageCostPerShare();
        this.latestMarketPrice = realTimeCurrentPrice;
        this.totalInvestedAmount = position.getTotalInvestedAmount();
        this.currency = position.getCurrency();
        this.lastUpdated = position.getLastUpdated();
        this.isActive = position.getIsActive();
        this.currentPriceTimestamp = LocalDateTime.now();
        this.exchange = position.getExchange();
        this.country = position.getCountry();
    }

    // Constructor from Position with updated current price and custom timestamp
    public CurrentPosition(Position position, BigDecimal realTimeCurrentPrice, LocalDateTime priceTimestamp) {
        this.id = position.getId();
        this.ticker = position.getTicker();
        this.sharesOwned = position.getSharesOwned();
        this.averageCostPerShare = position.getAverageCostPerShare();
        this.latestMarketPrice = realTimeCurrentPrice;
        this.totalInvestedAmount = position.getTotalInvestedAmount();
        this.currency = position.getCurrency();
        this.lastUpdated = position.getLastUpdated();
        this.isActive = position.getIsActive();
        this.currentPriceTimestamp = priceTimestamp;
        this.exchange = position.getExchange();
        this.country = position.getCountry();
    }

    // Constructor with required fields
    public CurrentPosition(String ticker, Currency currency, BigDecimal currentPrice) {
        this();
        this.ticker = ticker;
        this.currency = currency;
        this.latestMarketPrice = currentPrice;
        this.lastUpdated = LocalDate.now();
        this.sharesOwned = BigDecimal.ZERO;
        this.averageCostPerShare = BigDecimal.ZERO;
        this.totalInvestedAmount = BigDecimal.ZERO;
    }

    /**
     * Checks if the position has any shares
     */
    public boolean hasShares() {
        return sharesOwned != null && sharesOwned.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Calculates the current market value using real-time price
     */
    public BigDecimal getTotalMarketValue() {
        if (sharesOwned == null || latestMarketPrice == null) {
            return BigDecimal.ZERO;
        }
        return sharesOwned.multiply(latestMarketPrice);
    }

    /**
     * Calculates the unrealized gain/loss using real-time price
     */
    public BigDecimal getUnrealizedGainLoss() {
        return getTotalMarketValue().subtract(totalInvestedAmount != null ? totalInvestedAmount : BigDecimal.ZERO);
    }

    /**
     * Calculates the unrealized gain/loss percentage using real-time price
     */
    public BigDecimal getUnrealizedGainLossPercentage() {
        if (totalInvestedAmount == null || totalInvestedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getUnrealizedGainLoss().divide(totalInvestedAmount, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    /**
     * Checks if the current price data is fresh (updated within last 30 minutes)
     */
    public boolean isCurrentPriceFresh() {
        if (currentPriceTimestamp == null) {
            return false;
        }
        return currentPriceTimestamp.isAfter(LocalDateTime.now().minusMinutes(30));
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public BigDecimal getSharesOwned() { return sharesOwned; }
    public void setSharesOwned(BigDecimal sharesOwned) { this.sharesOwned = sharesOwned; }

    public BigDecimal getAverageCostPerShare() { return averageCostPerShare; }
    public void setAverageCostPerShare(BigDecimal averageCostPerShare) { this.averageCostPerShare = averageCostPerShare; }

    public BigDecimal getLatestMarketPrice() { return latestMarketPrice; }
    public void setLatestMarketPrice(BigDecimal latestMarketPrice) { 
        this.latestMarketPrice = latestMarketPrice;
        this.currentPriceTimestamp = LocalDateTime.now();
    }

    public BigDecimal getTotalInvestedAmount() { return totalInvestedAmount; }
    public void setTotalInvestedAmount(BigDecimal totalInvestedAmount) { this.totalInvestedAmount = totalInvestedAmount; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public LocalDate getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDate lastUpdated) { this.lastUpdated = lastUpdated; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCurrentPriceTimestamp() { return currentPriceTimestamp; }
    public void setCurrentPriceTimestamp(LocalDateTime currentPriceTimestamp) { 
        this.currentPriceTimestamp = currentPriceTimestamp; 
    }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
