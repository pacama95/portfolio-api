package com.portfolio.infrastructure.stream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base event envelope for all Redis Stream events with typed payload
 */
@Getter
@RegisterForReflection
public class EventEnvelope {

    @JsonProperty("eventId")
    private UUID eventId;

    @JsonProperty("occurredAt")
    private Instant occurredAt;

    @JsonProperty("messageCreatedAt")
    private Instant messageCreatedAt;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("payload")
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
    private TransactionEvent payload;

    // Default constructor for Jackson
    public EventEnvelope() {
    }

    public EventEnvelope(UUID eventId, Instant occurredAt, Instant messageCreatedAt, String eventType, TransactionEvent payload) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.messageCreatedAt = messageCreatedAt;
        this.eventType = eventType;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "EventEnvelope{" +
                "eventId=" + eventId +
                ", occurredAt=" + occurredAt +
                ", messageCreatedAt=" + messageCreatedAt +
                ", eventType='" + eventType + '\'' +
                ", payload=" + payload +
                '}';
    }
}
