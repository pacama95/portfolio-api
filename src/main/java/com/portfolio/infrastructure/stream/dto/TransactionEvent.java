package com.portfolio.infrastructure.stream.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Sealed interface for all transaction events
 * Type discrimination is handled at the EventEnvelope level using eventType field
 */
@RegisterForReflection
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "eventType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionCreatedEvent.class, name = "TransactionCreated"),
        @JsonSubTypes.Type(value = TransactionUpdatedEvent.class, name = "TransactionUpdated"),
        @JsonSubTypes.Type(value = TransactionDeletedEvent.class, name = "TransactionDeleted")
})
public sealed interface TransactionEvent
        permits TransactionCreatedEvent, TransactionUpdatedEvent, TransactionDeletedEvent {
}
