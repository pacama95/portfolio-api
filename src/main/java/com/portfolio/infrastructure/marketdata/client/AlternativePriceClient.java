package com.portfolio.infrastructure.marketdata.client;

import com.portfolio.infrastructure.marketdata.dto.AlternativePriceResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Alternative Price API (Railway)
 */
@RegisterRestClient(configKey = "alternative-price-api")
public interface AlternativePriceClient {

    /**
     * Get real-time price for a stock symbol
     * @param symbol Stock symbol (e.g., "ITX.MC")
     * @param exchange Stock exchange (e.g., "BME")
     * @return Price response from Alternative API
     */
    @GET
    @Path("/api/stock/{symbol}/price")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<AlternativePriceResponse> getPrice(
        @PathParam("symbol") String symbol,
        @QueryParam("exchange") String exchange
    );
}

