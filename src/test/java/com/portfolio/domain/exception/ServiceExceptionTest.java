package com.portfolio.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceExceptionTest {

    @Test
    void testGetErrorCode_ReturnsCorrectCode() {
        // Given
        Error error = Errors.Position.INVALID_INPUT;
        ServiceException exception = new ServiceException(error);

        // When
        String errorCode = exception.getErrorCode();

        // Then
        assertEquals("0101", errorCode);
    }

    @Test
    void testDifferentErrorTypes() {
        // Test different error types from Errors interface
        ServiceException positionError = new ServiceException(Errors.Position.INVALID_INPUT);
        assertEquals("0101", positionError.getErrorCode());

        ServiceException oversellError = new ServiceException(Errors.Position.OVERSELL);
        assertEquals("0102", oversellError.getErrorCode());

        ServiceException portfolioError = new ServiceException(Errors.GetPortfolioSummary.NOT_FOUND);
        assertEquals("1002", portfolioError.getErrorCode());

        ServiceException marketDataError = new ServiceException(Errors.MarketData.PRICE_NOT_FOUND);
        assertEquals("0602", marketDataError.getErrorCode());
    }

    @Test
    void testExceptionIsRuntimeException() {
        // Given
        Error error = Errors.Position.INVALID_INPUT;
        ServiceException exception = new ServiceException(error);

        // Then
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testExceptionCanBeThrown() {
        // Given
        Error error = Errors.Position.OVERSELL;
        String message = "Cannot sell 10 shares, only 5 available";

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            throw new ServiceException(error, message);
        });

        assertEquals(error, exception.getError());
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testExceptionWithCausePreservesStackTrace() {
        // Given
        Error error = Errors.GetPosition.PERSISTENCE_ERROR;
        RuntimeException originalException = new RuntimeException("Original error");
        ServiceException exception = new ServiceException(error, originalException);

        // When
        Throwable cause = exception.getCause();

        // Then
        assertNotNull(cause);
        assertEquals("Original error", cause.getMessage());
        assertInstanceOf(RuntimeException.class, cause);
    }
}
