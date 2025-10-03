package com.portfolio.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.application.service.MarketDataPriceFetchService;
import com.portfolio.application.usecase.portfolio.GetPortfolioSummaryUseCase;
import com.portfolio.application.usecase.position.GetPositionUseCase;
import com.portfolio.application.usecase.position.UpdateMarketDataUseCase;
import com.portfolio.infrastructure.mcp.converter.ParameterConversionService;
import com.portfolio.infrastructure.mcp.mapper.MarketPriceMcpMapper;
import com.portfolio.infrastructure.mcp.mapper.PortfolioSummaryMcpMapper;
import com.portfolio.infrastructure.mcp.mapper.PositionMcpMapper;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    MarketDataPriceFetchService marketDataPriceFetchService;

    @Inject
    ParameterConversionService parameterConversionService;

    @Inject
    PositionMcpMapper positionMcpMapper;

    @Inject
    PortfolioSummaryMcpMapper portfolioSummaryMcpMapper;

    @Inject
    MarketPriceMcpMapper marketPriceMcpMapper;

    // ============ MCP TOOL METHODS ============

    @Tool(description = "Get all current positions in the portfolio.")
    public Uni<String> getAllPositions() {
        return getPositionUseCase.getAll()
                .map(positionMcpMapper::toCurrentPositionDtoList)
                .map(dtos -> {
                    try {
                        return objectMapper.writeValueAsString(dtos);
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
                .map(positionMcpMapper::toCurrentPositionDtoList)
                .map(dtos -> {
                    try {
                        return objectMapper.writeValueAsString(dtos);
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
                .map(positionMcpMapper::toDto)
                .map(dto -> {
                    try {
                        return objectMapper.writeValueAsString(dto);
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
                .map(positionMcpMapper::toDto)
                .map(dto -> {
                    try {
                        return objectMapper.writeValueAsString(dto);
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
                .map(portfolioSummaryMcpMapper::toDto)
                .map(dto -> {
                    try {
                        return objectMapper.writeValueAsString(dto);
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
                .map(portfolioSummaryMcpMapper::toDto)
                .map(dto -> {
                    try {
                        return objectMapper.writeValueAsString(dto);
                    } catch (Exception e) {
                        throw new RuntimeException("Error serializing result", e);
                    }
                })
                .onFailure().invoke(e -> Log.error("Error getting active portfolio summary", e))
                .onFailure().transform(throwable -> new ToolCallException("Error getting active portfolio summary"));
    }

    @Tool(description = "Get current market price for a stock ticker from TwelveData API.")
    public Uni<String> getCurrentPrice(@ToolArg(description = "Stock ticker symbol (e.g., AAPL, MSFT)") String ticker,
                                       @ToolArg(description = "Exchange code for the symbol (e.g., BME, NYSE)", required = false) String exchangeCode) {
        return marketDataPriceFetchService.getCurrentPrice(ticker, exchangeCode)
                .map(price -> marketPriceMcpMapper.toDto(ticker, price, LocalDateTime.now()))
                .map(dto -> {
                    try {
                        return objectMapper.writeValueAsString(dto);
                    } catch (Exception e) {
                        throw new RuntimeException("Error serializing result", e);
                    }
                })
                .onFailure().invoke(e -> Log.error("Error getting current price for ticker {}", ticker, e))
                .onFailure().transform(throwable -> new ToolCallException("Error getting current price for ticker " + ticker));
    }
}