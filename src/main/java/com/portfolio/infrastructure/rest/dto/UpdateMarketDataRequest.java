package com.portfolio.infrastructure.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Request to update market price for a position")
public record UpdateMarketDataRequest(
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Price must be positive")
    @Schema(description = "New market price per share", example = "155.75", required = true)
    BigDecimal price
) {} 