package com.portfolio.infrastructure.marketdata.client;

import com.portfolio.infrastructure.marketdata.dto.TwelveDataPriceResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for TwelveData API
 */
@RegisterRestClient(configKey = "twelve-data-api")
public interface TwelveDataClient {

    /**
     * Get real-time price for a stock symbol
     * @param symbol Stock symbol (e.g., "AAPL")
     * @param apikey TwelveData API key
     * @return Price response from TwelveData
     */
    @GET
    @Path("/price")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TwelveDataPriceResponse> getPrice(
        @QueryParam("symbol") String symbol,
        @QueryParam("apikey") String apikey
    );
}
