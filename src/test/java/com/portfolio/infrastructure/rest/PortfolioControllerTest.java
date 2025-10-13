package com.portfolio.infrastructure.rest;

import com.portfolio.application.usecase.portfolio.GetPortfolioSummaryUseCase;
import com.portfolio.domain.model.PortfolioSummary;
import com.portfolio.infrastructure.rest.dto.PortfolioSummaryResponse;
import com.portfolio.infrastructure.rest.mapper.PortfolioSummaryMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    private GetPortfolioSummaryUseCase getPortfolioSummaryUseCase;

    @Mock
    private PortfolioSummaryMapper portfolioSummaryMapper;

    @InjectMocks
    private PortfolioController portfolioController;

    private PortfolioSummary portfolioSummary;
    private PortfolioSummaryResponse portfolioSummaryResponse;

    @BeforeEach
    void setUp() {
        portfolioSummary = PortfolioSummaryMother.createComplete();
        portfolioSummaryResponse = PortfolioSummaryResponseMother.createComplete();
    }

    @Test
    void testGetPortfolioSummary_ReturnsCompletePortfolio() {
        // Given
        when(getPortfolioSummaryUseCase.getPortfolioSummary())
                .thenReturn(Uni.createFrom().item(portfolioSummary));
        when(portfolioSummaryMapper.toResponse(portfolioSummary))
                .thenReturn(portfolioSummaryResponse);

        // When
        Uni<PortfolioSummaryResponse> result = portfolioController.getPortfolioSummary();

        // Then
        PortfolioSummaryResponse response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertNotNull(response);
        assertEquals(portfolioSummaryResponse.totalMarketValue(), response.totalMarketValue());
        assertEquals(portfolioSummaryResponse.totalCost(), response.totalCost());
        assertEquals(portfolioSummaryResponse.totalUnrealizedGainLoss(), response.totalUnrealizedGainLoss());
        assertEquals(portfolioSummaryResponse.totalUnrealizedGainLossPercentage(), response.totalUnrealizedGainLossPercentage());
        assertEquals(portfolioSummaryResponse.totalPositions(), response.totalPositions());

        verify(getPortfolioSummaryUseCase, times(1)).getPortfolioSummary();
        verify(portfolioSummaryMapper, times(1)).toResponse(portfolioSummary);
    }

    @Test
    void testGetPortfolioSummary_WithEmptyPortfolio() {
        // Given
        PortfolioSummary emptyPortfolio = PortfolioSummaryMother.createEmpty();
        PortfolioSummaryResponse emptyResponse = PortfolioSummaryResponseMother.createEmpty();

        when(getPortfolioSummaryUseCase.getPortfolioSummary())
                .thenReturn(Uni.createFrom().item(emptyPortfolio));
        when(portfolioSummaryMapper.toResponse(emptyPortfolio))
                .thenReturn(emptyResponse);

        // When
        Uni<PortfolioSummaryResponse> result = portfolioController.getPortfolioSummary();

        // Then
        PortfolioSummaryResponse response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.totalMarketValue());
        assertEquals(BigDecimal.ZERO, response.totalCost());
        assertEquals(BigDecimal.ZERO, response.totalUnrealizedGainLoss());
        assertEquals(BigDecimal.ZERO, response.totalUnrealizedGainLossPercentage());
        assertEquals(0, response.totalPositions());

        verify(getPortfolioSummaryUseCase, times(1)).getPortfolioSummary();
        verify(portfolioSummaryMapper, times(1)).toResponse(emptyPortfolio);
    }

    @Test
    void testGetActivePortfolioSummary_ReturnsActivePositionsOnly() {
        // Given
        when(getPortfolioSummaryUseCase.getActiveSummary())
                .thenReturn(Uni.createFrom().item(portfolioSummary));
        when(portfolioSummaryMapper.toResponse(portfolioSummary))
                .thenReturn(portfolioSummaryResponse);

        // When
        Uni<PortfolioSummaryResponse> result = portfolioController.getActivePortfolioSummary();

        // Then
        PortfolioSummaryResponse response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertNotNull(response);
        assertEquals(portfolioSummaryResponse.totalMarketValue(), response.totalMarketValue());
        assertEquals(portfolioSummaryResponse.activePositions(), response.activePositions());

        verify(getPortfolioSummaryUseCase, times(1)).getActiveSummary();
        verify(portfolioSummaryMapper, times(1)).toResponse(portfolioSummary);
    }

    @Test
    void testGetPortfolioSummary_HandlesError() {
        // Given
        RuntimeException error = new RuntimeException("Database error");
        when(getPortfolioSummaryUseCase.getPortfolioSummary())
                .thenReturn(Uni.createFrom().failure(error));

        // When
        Uni<PortfolioSummaryResponse> result = portfolioController.getPortfolioSummary();

        // Then
        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(RuntimeException.class, "Database error");

        verify(getPortfolioSummaryUseCase, times(1)).getPortfolioSummary();
        verify(portfolioSummaryMapper, never()).toResponse(any());
    }

    @Test
    void testGetActivePortfolioSummary_HandlesError() {
        // Given
        RuntimeException error = new RuntimeException("Service unavailable");
        when(getPortfolioSummaryUseCase.getActiveSummary())
                .thenReturn(Uni.createFrom().failure(error));

        // When
        Uni<PortfolioSummaryResponse> result = portfolioController.getActivePortfolioSummary();

        // Then
        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(RuntimeException.class, "Service unavailable");

        verify(getPortfolioSummaryUseCase, times(1)).getActiveSummary();
        verify(portfolioSummaryMapper, never()).toResponse(any());
    }

    // Mother Objects
    private static class PortfolioSummaryMother {
        static PortfolioSummary createComplete() {
            return new PortfolioSummary(
                    new BigDecimal("50000.00"),
                    new BigDecimal("45000.00"),
                    new BigDecimal("5000.00"),
                    new BigDecimal("11.11"),
                    5L,
                    3L
            );
        }

        static PortfolioSummary createEmpty() {
            return new PortfolioSummary(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0L,
                    0L
            );
        }
    }

    private static class PortfolioSummaryResponseMother {
        static PortfolioSummaryResponse createComplete() {
            return new PortfolioSummaryResponse(
                    new BigDecimal("50000.00"),
                    new BigDecimal("45000.00"),
                    new BigDecimal("5000.00"),
                    new BigDecimal("11.11"),
                    5L,
                    3L
            );
        }

        static PortfolioSummaryResponse createEmpty() {
            return new PortfolioSummaryResponse(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0L,
                    0L
            );
        }
    }
}
