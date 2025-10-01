package com.portfolio.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.application.usecase.portfolio.GetPortfolioSummaryUseCase;
import com.portfolio.application.usecase.position.GetPositionUseCase;
import com.portfolio.application.usecase.position.UpdateMarketDataUseCase;
import com.portfolio.domain.port.MarketDataService;
import com.portfolio.infrastructure.mcp.converter.ParameterConversionService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;

@Singleton
public class PortfolioMcpServer {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GetPositionUseCase getPositionUseCase;

    @Inject
    UpdateMarketDataUseCase updateMarketDataUseCase;

    @Inject
    GetPortfolioSummaryUseCase getPortfolioSummaryUseCase;

    @Inject
    MarketDataService marketDataService;

    @Inject
    ParameterConversionService parameterConversionService;

    // ============ MCP TOOL METHODS ============

    @Tool(description = "Get all current positions in the portfolio.")
    public Uni<String> getAllPositions() {
        return getPositionUseCase.getAll()
            .map(positions -> {
                try {
                    return objectMapper.writeValueAsString(positions);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing result", e);
                }
            })
            .onFailure().invoke(e -> Log.error("Error getting all positions", e))
            .onFailure().transform(throwable -> new ToolCallException("Error getting all positions"));
    }

    @Tool(description = "Get active positions in the portfolio (positions with shares > 0).")
    public Uni<String> getActivePositions() {
        return getPositionUseCase.getActivePositions()
            .map(positions -> {
                try {
                    return objectMapper.writeValueAsString(positions);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing result", e);
                }
            })
            .onFailure().invoke(e -> Log.error("Error getting active positions", e))
            .onFailure().transform(throwable -> new ToolCallException("Error getting active positions"));
    }

    @Tool(description = "Get position details for a specific ticker.")
    public Uni<String> getPositionByTicker(@ToolArg(description = "Stock ticker symbol") String ticker) {
        return getPositionUseCase.getByTicker(ticker)
            .map(position -> {
                try {
                    return objectMapper.writeValueAsString(position);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing result", e);
                }
            })
            .onFailure().invoke(e -> Log.error("Error getting position for ticker %s".formatted(ticker), e))
            .onFailure().transform(throwable -> new ToolCallException("Error getting position for ticker %s".formatted(ticker)));
    }

    @Tool(description = "Update market data for a position.")
    public Uni<String> updateMarketData(
            @ToolArg(description = "Stock ticker symbol") String ticker,
            @ToolArg(description = "Current market price") Object currentPrice) {
        BigDecimal convertedCurrentPrice = (BigDecimal) parameterConversionService.convert(currentPrice, "currentPrice");
        
        return updateMarketDataUseCase.execute(ticker, convertedCurrentPrice)
            .map(result -> {
                try {
                    return objectMapper.writeValueAsString(result);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing result", e);
                }
            })
            .onFailure().invoke(e -> Log.error("Error updating market data for ticker %s".formatted(ticker), e))
            .onFailure().transform(throwable -> new ToolCallException("Error updating market data for ticker %s".formatted(ticker)));
    }

    @Tool(description = "Get portfolio summary with key metrics.")
    public Uni<String> getPortfolioSummary() {
        return getPortfolioSummaryUseCase.getPortfolioSummary()
            .map(summary -> {
                try {
                    return objectMapper.writeValueAsString(summary);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing result", e);
                }
            })
            .onFailure().invoke(e -> Log.error("Error getting portfolio summary", e))
            .onFailure().transform(throwable -> new ToolCallException("Error getting portfolio summary"));
    }

    @Tool(description = "Get active portfolio summary with key metrics (only positions with shares > 0).")
    public Uni<String> getActivePortfolioSummary() {
        return getPortfolioSummaryUseCase.getActiveSummary()
            .map(summary -> {
                try {
                    return objectMapper.writeValueAsString(summary);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing result", e);
                }
            })
            .onFailure().invoke(e -> Log.error("Error getting active portfolio summary", e))
            .onFailure().transform(throwable -> new ToolCallException("Error getting active portfolio summary"));
    }

    @Tool(description = "Get current market price for a stock ticker from TwelveData API.")
    public Uni<String> getCurrentPrice(@ToolArg(description = "Stock ticker symbol (e.g., AAPL, MSFT)") String ticker) {
        return marketDataService.getCurrentPrice(ticker)
            .map(price -> {
                try {
                    return objectMapper.writeValueAsString(
                        new PriceResult(ticker, price, java.time.LocalDateTime.now())
                    );
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing result", e);
                }
            })
            .onFailure().invoke(e -> Log.error("Error getting current price for ticker {}", ticker, e))
            .onFailure().transform(throwable -> new ToolCallException("Error getting current price for ticker " + ticker));
    }

    // Helper record for price response
    private record PriceResult(String ticker, java.math.BigDecimal price, java.time.LocalDateTime timestamp) {}
}