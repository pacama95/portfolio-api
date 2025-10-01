# TwelveData Integration for Current Prices

This document outlines the TwelveData integration for fetching real-time stock prices in the portfolio system.

## Overview

The TwelveData integration provides real-time market price data for stocks. This integration **only handles current price data** - dividend functionality has been removed as requested.

## Architecture

### Domain Layer
- **`MarketDataService`** (port): Interface for market data operations
  - `getCurrentPrice(String ticker)`: Gets current price for a ticker

### Infrastructure Layer
- **`TwelveDataClient`**: REST client for TwelveData API
- **`TwelveDataMarketDataService`**: Implementation of MarketDataService using TwelveData
- **`TwelveDataPriceResponse`**: DTO for price response from TwelveData API

## Configuration

### Environment Variables
Set your TwelveData API key:
```bash
export TWELVE_DATA_API_KEY=your_api_key_here
```

### Application Properties
```properties
# TwelveData API Configuration
application.market-data.twelve-data.api-key=${TWELVE_DATA_API_KEY:not-configured}
quarkus.rest-client.twelve-data-api.url=https://api.twelvedata.com

# Cache Configuration for Stock Prices (30 minute TTL)
quarkus.cache.caffeine.stock-prices.initial-capacity=100
quarkus.cache.caffeine.stock-prices.maximum-size=1000
quarkus.cache.caffeine.stock-prices.expire-after-write=PT30M
quarkus.cache.caffeine.stock-prices.metrics-enabled=true
```

## API Usage

### TwelveData API Endpoint
The integration uses TwelveData's `/price` endpoint:
```
GET https://api.twelvedata.com/price?symbol={TICKER}&apikey={API_KEY}
```

### Response Format
```json
{
  "price": "150.25",
  "symbol": "AAPL",
  "datetime": "2023-12-01 15:30:00",
  "timestamp": "1701443400",
  "status": "ok"
}
```

## Features

### 1. Real-time Price Fetching
- Positions automatically fetch real-time prices when retrieved
- Cached for 30 minutes to reduce API calls
- Fallback to stored prices if TwelveData fails

### 2. Caching
- **Cache Name**: `stock-prices`
- **TTL**: 30 minutes
- **Max Size**: 1000 entries
- **Metrics**: Enabled for monitoring

### 3. Error Handling
- Graceful fallback to stored prices if API fails
- Development mode fallback (returns $100.00) if API key not configured
- Comprehensive logging for debugging

### 4. MCP Integration
Direct price fetching via MCP tool:
- **Tool**: `getCurrentPrice`
- **Input**: Stock ticker (e.g., "AAPL")
- **Output**: JSON with ticker, price, and timestamp

## Usage Examples

### 1. Get Position with Real-time Price
```java
@Inject
GetPositionUseCase getPositionUseCase;

Uni<CurrentPosition> position = getPositionUseCase.getByTicker("AAPL");
// Returns position with current market price from TwelveData
```

### 2. Direct Price Fetch
```java
@Inject
MarketDataService marketDataService;

Uni<BigDecimal> price = marketDataService.getCurrentPrice("AAPL");
```

### 3. MCP Tool Usage
```bash
# Via MCP client
getCurrentPrice("AAPL")
```

## Integration Points

### Position Retrieval
All position retrieval methods now fetch real-time prices:
- `getById(UUID id)` - Single position by ID
- `getByTicker(String ticker)` - Single position by ticker
- `getAll()` - All positions
- `getActivePositions()` - Active positions only

### Portfolio Summary
Portfolio summaries use real-time prices for accurate valuations:
- Market values calculated with current prices
- P&L calculations reflect real-time data

## Error Scenarios

### 1. API Key Not Configured
- **Behavior**: Returns fallback price ($100.00)
- **Log Level**: WARN
- **Use Case**: Development/testing environments

### 2. TwelveData API Failure
- **Behavior**: Falls back to stored position prices
- **Log Level**: ERROR
- **Recovery**: Uses position's stored `currentPrice`

### 3. Invalid Ticker
- **Behavior**: API returns error, caught and logged
- **Recovery**: Falls back to stored price or zero

## Monitoring

### Cache Metrics
Monitor cache performance:
- Hit/miss ratios
- Eviction rates
- Cache size utilization

### API Usage
Track TwelveData API usage:
- Request counts
- Error rates
- Response times

## Development Notes

### Testing Without API Key
Set `TWELVE_DATA_API_KEY=not-configured` to use fallback prices during development.

### API Rate Limits
TwelveData has rate limits based on subscription tier:
- **Basic**: 8 requests/minute
- **Growth**: 55 requests/minute
- **Pro**: 550 requests/minute

The 30-minute cache helps stay within rate limits.

## Migration Notes

### From Previous Implementation
This implementation differs from the original in that:
- **Removed**: All dividend-related functionality
- **Kept**: Current price fetching with caching
- **Enhanced**: Better error handling and fallback mechanisms
- **Added**: Direct MCP tool for price testing

### Backward Compatibility
All existing position and portfolio endpoints continue to work, now with enhanced real-time pricing.
