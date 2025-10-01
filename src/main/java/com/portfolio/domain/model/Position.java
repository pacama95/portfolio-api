package com.portfolio.domain.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Position domain entity representing an aggregated position for a ticker
 */
@Getter
public class Position {
    private UUID id;
    private final String ticker;
    private BigDecimal sharesOwned;
    private BigDecimal averageCostPerShare;
    private BigDecimal latestMarketPrice;
    private BigDecimal totalInvestedAmount;
    private BigDecimal totalTransactionFees;
    private final Currency currency;
    private LocalDate lastUpdated;
    private LocalDate firstPurchaseDate;
    private Boolean isActive;
    private Instant lastEventAppliedAt; // For event ordering and idempotency

    // Constructor with required fields for creating new positions
    public Position(String ticker, Currency currency) {
        this.ticker = ticker;
        this.currency = currency;
        this.lastUpdated = LocalDate.now();
        this.sharesOwned = BigDecimal.ZERO;
        this.averageCostPerShare = BigDecimal.ZERO;
        this.latestMarketPrice = BigDecimal.ZERO;
        this.totalInvestedAmount = BigDecimal.ZERO;
        this.totalTransactionFees = BigDecimal.ZERO;
        this.firstPurchaseDate = LocalDate.now();
        this.isActive = true;
    }

    @Default
    public Position(UUID id,
                    String ticker,
                    BigDecimal sharesOwned,
                    BigDecimal averageCostPerShare,
                    BigDecimal latestMarketPrice,
                    BigDecimal totalInvestedAmount,
                    BigDecimal totalTransactionFees,
                    Currency currency,
                    LocalDate lastUpdated,
                    LocalDate firstPurchaseDate,
                    Boolean isActive,
                    Instant lastEventAppliedAt) {
        this.id = id;
        this.ticker = ticker;
        this.sharesOwned = sharesOwned;
        this.averageCostPerShare = averageCostPerShare;
        this.latestMarketPrice = latestMarketPrice;
        this.totalInvestedAmount = totalInvestedAmount;
        this.totalTransactionFees = totalTransactionFees;
        this.currency = currency;
        this.lastUpdated = lastUpdated;
        this.firstPurchaseDate = firstPurchaseDate;
        this.isActive = isActive;
        this.lastEventAppliedAt = lastEventAppliedAt;
    }

    /**
     * Checks if the position has any shares
     */
    public boolean hasShares() {
        return sharesOwned != null && sharesOwned.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Calculates the current market value
     */
    public BigDecimal getTotalMarketValue() {
        return sharesOwned.multiply(latestMarketPrice);
    }

    /**
     * Calculates the unrealized gain/loss
     */
    public BigDecimal getUnrealizedGainLoss() {
        return getTotalMarketValue().subtract(totalInvestedAmount);
    }

    /**
     * Calculates the unrealized gain/loss percentage
     */
    public BigDecimal getUnrealizedGainLossPercentage() {
        if (totalInvestedAmount == null || totalInvestedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getUnrealizedGainLoss().divide(totalInvestedAmount, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    /**
     * Apply a BUY transaction to this position
     * Updates shares owned, average cost, and total invested amount
     */
    public void applyBuy(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }

        BigDecimal currentShares = this.sharesOwned != null ? this.sharesOwned : BigDecimal.ZERO;
        BigDecimal currentInvested = this.totalInvestedAmount != null ? this.totalInvestedAmount : BigDecimal.ZERO;
        BigDecimal currentFees = this.totalTransactionFees != null ? this.totalTransactionFees : BigDecimal.ZERO;

        // Calculate transaction cost including fees
        BigDecimal transactionCost = quantity.multiply(price).add(fees);
        BigDecimal newTotalInvested = currentInvested.add(transactionCost);
        BigDecimal newTotalShares = currentShares.add(quantity);
        BigDecimal totalTransactionFees = currentFees.add(fees);

        // Calculate new average cost per share
        BigDecimal newAverageCost = newTotalShares.compareTo(BigDecimal.ZERO) > 0
                ? newTotalInvested.divide(newTotalShares, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Update position state
        this.sharesOwned = newTotalShares;
        this.averageCostPerShare = newAverageCost;
        this.totalInvestedAmount = newTotalInvested;
        this.latestMarketPrice = price; // TODO: replace it with the actual market price from the provider!
        this.totalTransactionFees = totalTransactionFees;
        this.isActive = true;
    }

    /**
     * Apply a SELL transaction to this position
     * Reduces shares owned, adjusts invested amount proportionally, and subtracts fees
     */
    public void applySell(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }

        BigDecimal currentShares = this.sharesOwned != null ? this.sharesOwned : BigDecimal.ZERO;
        BigDecimal currentInvested = this.totalInvestedAmount != null ? this.totalInvestedAmount : BigDecimal.ZERO;
        BigDecimal averageCost = this.averageCostPerShare != null ? this.averageCostPerShare : BigDecimal.ZERO;
        BigDecimal currentFees = this.totalTransactionFees != null ? this.totalTransactionFees : BigDecimal.ZERO;

        // Calculate new shares after sale
        BigDecimal newTotalShares = currentShares.subtract(quantity);

        // Reduce invested amount proportionally based on average cost
        BigDecimal proportionalCost = quantity.multiply(averageCost);
        BigDecimal newTotalInvested = currentInvested.subtract(proportionalCost).subtract(fees);
        BigDecimal totalTransactionFees = currentFees.add(fees);

        // Update position state
        this.sharesOwned = newTotalShares.max(BigDecimal.ZERO);  // Don't go negative
        this.totalInvestedAmount = newTotalInvested.max(BigDecimal.ZERO);
        this.latestMarketPrice = price;
        this.totalTransactionFees = totalTransactionFees;

        // Mark as inactive if no shares left
        if (newTotalShares.compareTo(BigDecimal.ZERO) <= 0) {
            this.isActive = false;
        }
    }

    /**
     * Apply a transaction based on its type
     */
    public void applyTransaction(String transactionType, BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (transactionType == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }

        switch (transactionType.toUpperCase()) {
            case "BUY" -> applyBuy(quantity, price, fees);
            case "SELL" -> applySell(quantity, price, fees);
            default -> throw new IllegalArgumentException("Unknown transaction type: " + transactionType);
        }

        this.lastUpdated = LocalDate.now();
    }

    /**
     * Reverse a BUY transaction
     * Undoes everything that applyBuy did, including subtracting fees
     */
    private void reverseBuy(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }

        BigDecimal currentShares = this.sharesOwned != null ? this.sharesOwned : BigDecimal.ZERO;
        BigDecimal currentInvested = this.totalInvestedAmount != null ? this.totalInvestedAmount : BigDecimal.ZERO;
        BigDecimal currentFees = this.totalTransactionFees != null ? this.totalTransactionFees : BigDecimal.ZERO;

        // Reverse the BUY: remove shares, remove cost (including fees), and remove fees
        BigDecimal transactionCost = quantity.multiply(price).add(fees);
        BigDecimal newTotalInvested = currentInvested.subtract(transactionCost);
        BigDecimal newTotalShares = currentShares.subtract(quantity);
        BigDecimal totalTransactionFees = currentFees.subtract(fees);

        // Recalculate average cost per share
        BigDecimal newAverageCost = newTotalShares.compareTo(BigDecimal.ZERO) > 0
                ? newTotalInvested.divide(newTotalShares, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Update position state
        this.sharesOwned = newTotalShares.max(BigDecimal.ZERO);
        this.averageCostPerShare = newAverageCost;
        this.totalInvestedAmount = newTotalInvested.max(BigDecimal.ZERO);
        this.latestMarketPrice = price;
        this.totalTransactionFees = totalTransactionFees.max(BigDecimal.ZERO);

        // Mark as inactive if no shares left
        if (newTotalShares.compareTo(BigDecimal.ZERO) <= 0) {
            this.isActive = false;
        }
    }

    /**
     * Reverse a SELL transaction
     * Undoes everything that applySell did, including subtracting fees
     */
    private void reverseSell(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }

        BigDecimal currentShares = this.sharesOwned != null ? this.sharesOwned : BigDecimal.ZERO;
        BigDecimal currentInvested = this.totalInvestedAmount != null ? this.totalInvestedAmount : BigDecimal.ZERO;
        BigDecimal averageCost = this.averageCostPerShare != null ? this.averageCostPerShare : BigDecimal.ZERO;
        BigDecimal currentFees = this.totalTransactionFees != null ? this.totalTransactionFees : BigDecimal.ZERO;

        // Reverse the SELL: add shares back, add cost back (plus fees that were subtracted), and remove fees
        BigDecimal newTotalShares = currentShares.add(quantity);
        BigDecimal proportionalCost = quantity.multiply(averageCost);
        BigDecimal newTotalInvested = currentInvested.add(proportionalCost).add(fees);
        BigDecimal totalTransactionFees = currentFees.subtract(fees);

        // Recalculate average cost per share
        BigDecimal newAverageCost = newTotalShares.compareTo(BigDecimal.ZERO) > 0
                ? newTotalInvested.divide(newTotalShares, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Update position state
        this.sharesOwned = newTotalShares;
        this.averageCostPerShare = newAverageCost;
        this.totalInvestedAmount = newTotalInvested;
        this.latestMarketPrice = price;
        this.totalTransactionFees = totalTransactionFees.max(BigDecimal.ZERO);
        this.isActive = true;
    }

    /**
     * Reverse a transaction based on its type
     * Properly undoes all effects including fees
     */
    public void reverseTransaction(String transactionType, BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (transactionType == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }

        switch (transactionType.toUpperCase()) {
            case "BUY" -> reverseBuy(quantity, price, fees);
            case "SELL" -> reverseSell(quantity, price, fees);
            default -> throw new IllegalArgumentException("Unknown transaction type: " + transactionType);
        }

        this.lastUpdated = LocalDate.now();
    }

    /**
     * Mark this position as inactive (soft delete)
     */
    public void markAsInactive() {
        this.isActive = false;
        this.lastUpdated = LocalDate.now();
    }

    /**
     * Update the event applied timestamp for idempotency
     */
    public void updateLastEventAppliedAt(Instant timestamp) {
        this.lastEventAppliedAt = timestamp;
    }

    /**
     * Check if an event should be ignored based on timestamp ordering
     */
    public boolean shouldIgnoreEvent(Instant eventOccurredAt) {
        return this.lastEventAppliedAt != null &&
                !eventOccurredAt.isAfter(this.lastEventAppliedAt);
    }
} 