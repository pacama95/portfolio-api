package com.portfolio.domain.model;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private final LocalDate firstPurchaseDate;
    private Boolean isActive;
    private Instant lastEventAppliedAt; // For event ordering and idempotency
    private String exchange;
    private String country;
    private final List<UUID> transactions;

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
        this.transactions = new ArrayList<>();
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
                    Instant lastEventAppliedAt,
                    String exchange,
                    String country,
                    List<UUID> transactions) {
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
        this.exchange = exchange;
        this.country = country;
        this.transactions = transactions;
    }

    public boolean hasShares() {
        return sharesOwned != null && sharesOwned.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getTotalMarketValue() {
        if (!hasShares()) {
            return BigDecimal.ZERO;
        }

        return sharesOwned.multiply(latestMarketPrice);
    }

    public BigDecimal getUnrealizedGainLoss() {
        if (!hasShares()) {
            return BigDecimal.ZERO;
        }

        return getTotalMarketValue().subtract(totalInvestedAmount);
    }

    public BigDecimal getUnrealizedGainLossPercentage() {
        if (!hasShares()) {
            return BigDecimal.ZERO;
        }

        if (totalInvestedAmount == null || totalInvestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return getUnrealizedGainLoss().divide(totalInvestedAmount, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    public void applyBuy(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Price cannot be negative");
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

    public void applySell(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Quantity must be positive");
        }
        if (this.sharesOwned.compareTo(quantity) < 0) {
            throw new ServiceException(Errors.Position.OVERSELL);
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Price cannot be negative");
        }
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }

        BigDecimal currentInvested = this.totalInvestedAmount != null ? this.totalInvestedAmount : BigDecimal.ZERO;
        BigDecimal averageCost = this.averageCostPerShare != null ? this.averageCostPerShare : BigDecimal.ZERO;
        BigDecimal currentFees = this.totalTransactionFees != null ? this.totalTransactionFees : BigDecimal.ZERO;

        // Calculate new shares after sale
        BigDecimal newTotalShares = this.sharesOwned.subtract(quantity);

        // Reduce invested amount proportionally based on average cost
        BigDecimal proportionalCost = quantity.multiply(averageCost);
        BigDecimal newTotalInvested = currentInvested.subtract(proportionalCost);
        BigDecimal totalTransactionFees = currentFees.add(fees);

        // Calculate new average cost per share
        BigDecimal newAverageCost = newTotalShares.compareTo(BigDecimal.ZERO) > 0
                ? newTotalInvested.divide(newTotalShares, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Update position state
        this.sharesOwned = newTotalShares;
        this.averageCostPerShare = newAverageCost;
        this.totalInvestedAmount = (newTotalShares.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : newTotalInvested;
        this.latestMarketPrice = price;
        this.totalTransactionFees = totalTransactionFees;

        // Mark as inactive if no shares left
        if (newTotalShares.compareTo(BigDecimal.ZERO) <= 0) {
            this.isActive = false;
        }
    }

    public void applyTransaction(UUID transactionId, String transactionType, BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (transactionType == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }

        switch (transactionType.toUpperCase()) {
            case "BUY" -> applyBuy(quantity, price, fees);
            case "SELL" -> applySell(quantity, price, fees);
            default -> throw new IllegalArgumentException("Unknown transaction type: " + transactionType);
        }

        this.transactions.add(transactionId);
        this.lastUpdated = LocalDate.now();
    }

    private void reverseBuy(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Quantity must be positive");
        }
        if (this.sharesOwned.compareTo(quantity) < 0) {
            throw new ServiceException(Errors.Position.OVERSELL);
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Price cannot be negative");
        }
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }

        BigDecimal currentInvested = this.totalInvestedAmount != null ? this.totalInvestedAmount : BigDecimal.ZERO;
        BigDecimal currentFees = this.totalTransactionFees != null ? this.totalTransactionFees : BigDecimal.ZERO;

        // Reverse the BUY: remove shares, remove cost (including fees), and remove fees
        BigDecimal transactionCost = quantity.multiply(price).add(fees);
        BigDecimal newTotalInvested = currentInvested.subtract(transactionCost);
        BigDecimal newTotalShares = this.sharesOwned.subtract(quantity);
        BigDecimal totalTransactionFees = currentFees.subtract(fees);

        // Recalculate average cost per share
        BigDecimal newAverageCost = newTotalShares.compareTo(BigDecimal.ZERO) > 0
                ? newTotalInvested.divide(newTotalShares, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Update position state
        this.sharesOwned = newTotalShares;
        this.averageCostPerShare = newAverageCost;
        this.totalInvestedAmount = (newTotalShares.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : newTotalInvested;
        this.latestMarketPrice = price;
        this.totalTransactionFees = totalTransactionFees;

        // Mark as inactive if no shares left
        if (newTotalShares.compareTo(BigDecimal.ZERO) <= 0) {
            this.isActive = false;
        }
    }

    private void reverseSell(BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(Errors.Position.INVALID_INPUT, "Price cannot be negative");
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
        BigDecimal newTotalInvested = currentInvested.add(proportionalCost);
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
        this.totalTransactionFees = totalTransactionFees;
        this.isActive = true;
    }

    public void reverseTransaction(UUID transactionId, String transactionType, BigDecimal quantity, BigDecimal price, BigDecimal fees) {
        if (transactionType == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }

        switch (transactionType.toUpperCase()) {
            case "BUY" -> reverseBuy(quantity, price, fees);
            case "SELL" -> reverseSell(quantity, price, fees);
            default -> throw new IllegalArgumentException("Unknown transaction type: " + transactionType);
        }

        this.transactions.remove(transactionId);
        this.lastUpdated = LocalDate.now();
    }

    public void markAsInactive() {
        this.isActive = false;
        this.lastUpdated = LocalDate.now();
    }

    public void updateLastEventAppliedAt(Instant timestamp) {
        this.lastEventAppliedAt = timestamp;
    }

    public boolean shouldIgnoreEvent(Instant eventOccurredAt) {
        return this.lastEventAppliedAt != null &&
                !eventOccurredAt.isAfter(this.lastEventAppliedAt);
    }

    public void updateExchange(String exchange) {
        this.exchange = exchange;
    }

    public void updateCountry(String country) {
        this.country = country;
    }
} 