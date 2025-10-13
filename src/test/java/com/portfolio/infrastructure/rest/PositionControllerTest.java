package com.portfolio.infrastructure.rest;

import com.portfolio.application.usecase.position.GetPositionUseCase;
import com.portfolio.application.usecase.position.UpdateMarketDataUseCase;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.CurrentPosition;
import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.rest.dto.PositionResponse;
import com.portfolio.infrastructure.rest.dto.UpdateMarketDataRequest;
import com.portfolio.infrastructure.rest.mapper.PositionMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionControllerTest {

    @Mock
    private GetPositionUseCase getPositionUseCase;

    @Mock
    private UpdateMarketDataUseCase updateMarketDataUseCase;

    @Mock
    private PositionMapper positionMapper;

    @InjectMocks
    private PositionController positionController;

    private CurrentPosition currentPosition;
    private Position position;
    private PositionResponse positionResponse;

    @BeforeEach
    void setUp() {
        currentPosition = CurrentPositionMother.create();
        position = PositionMother.create();
        positionResponse = PositionResponseMother.create();
    }

    @Test
    void testGetAllPositions_ReturnsAllPositions() {
        // Given
        List<CurrentPosition> positions = List.of(currentPosition);
        List<PositionResponse> responses = List.of(positionResponse);

        when(getPositionUseCase.getAll()).thenReturn(Uni.createFrom().item(positions));
        when(positionMapper.toCurrentPositionResponses(positions)).thenReturn(responses);

        // When
        Uni<List<PositionResponse>> result = positionController.getAllPositions();

        // Then
        List<PositionResponse> actualResponses = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertNotNull(actualResponses);
        assertEquals(1, actualResponses.size());
        assertEquals(positionResponse.ticker(), actualResponses.getFirst().ticker());

        verify(getPositionUseCase, times(1)).getAll();
        verify(positionMapper, times(1)).toCurrentPositionResponses(positions);
    }

    @Test
    void testGetAllPositions_ReturnsEmptyList() {
        // Given
        List<CurrentPosition> emptyList = List.of();
        when(getPositionUseCase.getAll()).thenReturn(Uni.createFrom().item(emptyList));
        when(positionMapper.toCurrentPositionResponses(emptyList)).thenReturn(List.of());

        // When
        Uni<List<PositionResponse>> result = positionController.getAllPositions();

        // Then
        List<PositionResponse> actualResponses = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertNotNull(actualResponses);
        assertTrue(actualResponses.isEmpty());

        verify(getPositionUseCase, times(1)).getAll();
    }

    @Test
    void testGetActivePositions_ReturnsOnlyActivePositions() {
        // Given
        List<CurrentPosition> activePositions = List.of(currentPosition);
        List<PositionResponse> responses = List.of(positionResponse);

        when(getPositionUseCase.getActivePositions()).thenReturn(Uni.createFrom().item(activePositions));
        when(positionMapper.toCurrentPositionResponses(activePositions)).thenReturn(responses);

        // When
        Uni<List<PositionResponse>> result = positionController.getActivePositions();

        // Then
        List<PositionResponse> actualResponses = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertNotNull(actualResponses);
        assertEquals(1, actualResponses.size());

        verify(getPositionUseCase, times(1)).getActivePositions();
        verify(positionMapper, times(1)).toCurrentPositionResponses(activePositions);
    }

    @Test
    void testGetPosition_ById_Found() {
        // Given
        UUID id = UUID.randomUUID();
        when(getPositionUseCase.getById(id)).thenReturn(Uni.createFrom().item(currentPosition));
        when(positionMapper.toResponse(currentPosition)).thenReturn(positionResponse);

        // When
        Uni<Response> result = positionController.getPosition(id);

        // Then
        Response response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(positionResponse, response.getEntity());

        verify(getPositionUseCase, times(1)).getById(id);
        verify(positionMapper, times(1)).toResponse(currentPosition);
    }

    @Test
    void testGetPosition_ById_NotFound() {
        // Given
        UUID id = UUID.randomUUID();
        when(getPositionUseCase.getById(id)).thenReturn(Uni.createFrom().item((CurrentPosition) null));

        // When
        Uni<Response> result = positionController.getPosition(id);

        // Then
        Response response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        verify(getPositionUseCase, times(1)).getById(id);
        verify(positionMapper, never()).toResponse(any(CurrentPosition.class));
    }

    @Test
    void testGetPositionByTicker_Found() {
        // Given
        String ticker = "AAPL";
        when(getPositionUseCase.getByTicker(ticker)).thenReturn(Uni.createFrom().item(currentPosition));
        when(positionMapper.toResponse(currentPosition)).thenReturn(positionResponse);

        // When
        Uni<Response> result = positionController.getPositionByTicker(ticker);

        // Then
        Response response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(positionResponse, response.getEntity());

        verify(getPositionUseCase, times(1)).getByTicker(ticker);
        verify(positionMapper, times(1)).toResponse(currentPosition);
    }

    @Test
    void testGetPositionByTicker_NotFound() {
        // Given
        String ticker = "INVALID";
        when(getPositionUseCase.getByTicker(ticker)).thenReturn(Uni.createFrom().item((CurrentPosition) null));

        // When
        Uni<Response> result = positionController.getPositionByTicker(ticker);

        // Then
        Response response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        verify(getPositionUseCase, times(1)).getByTicker(ticker);
        verify(positionMapper, never()).toResponse(any(CurrentPosition.class));
    }

    @Test
    void testUpdateMarketPrice_Success() {
        // Given
        String ticker = "AAPL";
        BigDecimal newPrice = new BigDecimal("180.50");
        UpdateMarketDataRequest request = new UpdateMarketDataRequest(newPrice);

        when(updateMarketDataUseCase.execute(ticker, newPrice)).thenReturn(Uni.createFrom().item(position));
        when(positionMapper.toResponse(position)).thenReturn(positionResponse);

        // When
        Uni<Response> result = positionController.updateMarketPrice(ticker, request);

        // Then
        Response response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(positionResponse, response.getEntity());

        verify(updateMarketDataUseCase, times(1)).execute(ticker, newPrice);
        verify(positionMapper, times(1)).toResponse(position);
    }

    @Test
    void testUpdateMarketPrice_Failure() {
        // Given
        String ticker = "AAPL";
        BigDecimal newPrice = new BigDecimal("180.50");
        UpdateMarketDataRequest request = new UpdateMarketDataRequest(newPrice);
        RuntimeException error = new RuntimeException("Position not found");

        when(updateMarketDataUseCase.execute(ticker, newPrice))
                .thenReturn(Uni.createFrom().failure(error));

        // When
        Uni<Response> result = positionController.updateMarketPrice(ticker, request);

        // Then
        Response response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Error updating market data"));

        verify(updateMarketDataUseCase, times(1)).execute(ticker, newPrice);
        verify(positionMapper, never()).toResponse(any(Position.class));
    }

    @Test
    void testCheckPositionExists_True() {
        // Given
        String ticker = "AAPL";
        when(getPositionUseCase.existsByTicker(ticker)).thenReturn(Uni.createFrom().item(true));

        // When
        Uni<Boolean> result = positionController.checkPositionExists(ticker);

        // Then
        Boolean exists = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertTrue(exists);
        verify(getPositionUseCase, times(1)).existsByTicker(ticker);
    }

    @Test
    void testCheckPositionExists_False() {
        // Given
        String ticker = "INVALID";
        when(getPositionUseCase.existsByTicker(ticker)).thenReturn(Uni.createFrom().item(false));

        // When
        Uni<Boolean> result = positionController.checkPositionExists(ticker);

        // Then
        Boolean exists = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertFalse(exists);
        verify(getPositionUseCase, times(1)).existsByTicker(ticker);
    }

    @Test
    void testGetPositionCount_ReturnsCount() {
        // Given
        Long count = 10L;
        when(getPositionUseCase.countAll()).thenReturn(Uni.createFrom().item(count));

        // When
        Uni<Long> result = positionController.getPositionCount();

        // Then
        Long actualCount = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(count, actualCount);
        verify(getPositionUseCase, times(1)).countAll();
    }

    @Test
    void testGetActivePositionCount_ReturnsCount() {
        // Given
        Long count = 7L;
        when(getPositionUseCase.countActivePositions()).thenReturn(Uni.createFrom().item(count));

        // When
        Uni<Long> result = positionController.getActivePositionCount();

        // Then
        Long actualCount = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertEquals(count, actualCount);
        verify(getPositionUseCase, times(1)).countActivePositions();
    }

    // Mother Objects
    private static class CurrentPositionMother {
        static CurrentPosition create() {
            CurrentPosition currentPosition = new CurrentPosition();
            currentPosition.setId(UUID.randomUUID());
            currentPosition.setTicker("AAPL");
            currentPosition.setSharesOwned(new BigDecimal("100.000000"));
            currentPosition.setAverageCostPerShare(new BigDecimal("150.00"));
            currentPosition.setLatestMarketPrice(new BigDecimal("175.00"));
            currentPosition.setTotalInvestedAmount(new BigDecimal("15000.00"));
            currentPosition.setCurrency(Currency.USD);
            currentPosition.setLastUpdated(LocalDate.now());
            currentPosition.setIsActive(true);
            currentPosition.setExchange("NASDAQ");
            currentPosition.setCountry("US");
            return currentPosition;
        }
    }

    private static class PositionMother {
        static Position create() {
            return new Position(
                    UUID.randomUUID(),
                    "AAPL",
                    new BigDecimal("100.000000"),
                    new BigDecimal("150.00"),
                    new BigDecimal("175.00"),
                    new BigDecimal("15000.00"),
                    new BigDecimal("10.00"),
                    Currency.USD,
                    LocalDate.now(),
                    LocalDate.of(2024, 1, 15),
                    true,
                    null,
                    "NASDAQ",
                    "US",
                    List.of()
            );
        }
    }

    private static class PositionResponseMother {
        static PositionResponse create() {
            return new PositionResponse(
                    UUID.randomUUID(),
                    "AAPL",
                    new BigDecimal("100.000000"),
                    new BigDecimal("150.00"),
                    new BigDecimal("175.00"),
                    new BigDecimal("15000.00"),
                    Currency.USD,
                    LocalDate.now(),
                    true,
                    new BigDecimal("17500.00"),
                    new BigDecimal("2500.00"),
                    new BigDecimal("16.67"),
                    "NASDAQ",
                    "US"
            );
        }
    }
}
