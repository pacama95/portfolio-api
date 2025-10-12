package com.portfolio.infrastructure.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.infrastructure.stream.config.ProcessingConfig;
import com.portfolio.infrastructure.stream.config.RedisStreamConfig;
import com.portfolio.infrastructure.stream.dto.EventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.ReactiveStreamCommands;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base class for Redis Stream consumers with common functionality
 */
public abstract class BaseRedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(BaseRedisStreamConsumer.class);

    // Metrics
    protected Counter processedCounter;
    protected Counter errorCounter;
    protected Timer processingTimer;

    @Inject
    protected ReactiveRedisDataSource redisDataSource;

    @Inject
    protected RedisStreamConfig config;

    @Inject
    protected ProcessingConfig processingConfig;

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    MeterRegistry meterRegistry;

    protected ReactiveStreamCommands<String, String, String> streamCommands;

    /**
     * Initialize the stream commands
     */
    protected void initializeStreamCommands() {
        this.streamCommands = redisDataSource.stream(String.class, String.class, String.class);
    }

    protected void initializeMetrics(String streamName) {
        this.processedCounter = Counter.builder("redis.stream.processed")
                .description("Number of messages processed")
                .tag("stream", streamName)
                .register(meterRegistry);
        this.errorCounter = Counter.builder("redis.stream.errors")
                .description("Number of errors processing messages")
                .tag("stream", streamName)
                .register(meterRegistry);
        this.processingTimer = Timer.builder("redis.stream.processing")
                .description("Time taken to process messages")
                .tag("stream", streamName)
                .register(meterRegistry);
    }

    /**
     * Get the stream name this consumer handles
     */
    protected abstract String getStreamName();

    /**
     * Parse the Redis Stream message into an EventEnvelope
     */
    protected Uni<EventEnvelope> parseMessage(Map<String, String> fields) {
        return Uni.createFrom().item(() -> {
            try {
                // Get the event JSON from the payload field
                String eventJson = fields.get("payload");
                if (eventJson == null) {
                    throw new IllegalArgumentException("Missing 'payload' field in stream message");
                }

                return objectMapper.readValue(eventJson, EventEnvelope.class);
            } catch (Exception e) {
                log.error("Failed to parse event envelope from stream message: {}", fields, e);
                throw new RuntimeException("Failed to parse event envelope", e);
            }
        });
    }

    /**
     * Acknowledge a message
     */
    protected Uni<Void> acknowledgeMessage(String messageId) {
        return streamCommands.xack(getStreamName(), config.group(), messageId)
                .onItem().transform(count -> {
                    log.debug("Acknowledged message {} from stream {}, count: {}",
                            messageId, getStreamName(), count);
                    return (Void) null;
                })
                .onFailure().invoke(throwable ->
                        log.error("Failed to acknowledge message {} from stream {}",
                                messageId, getStreamName(), throwable))
                .onFailure().recoverWithItem((Void) null);
    }

    /**
     * Send message to dead letter queue
     */
    protected Uni<Void> sendToDlq(String messageId, EventEnvelope envelope, Throwable error) {
        String dlqStream = getStreamName() + ":" + config.dlqSuffix();

        return Uni.createFrom().item(() -> {
                    try {
                        return Map.of(
                                "originalMessageId", messageId,
                                "originalStream", getStreamName(),
                                "error", error.getMessage(),
                                "data", objectMapper.writeValueAsString(envelope)
                        );
                    } catch (Exception e) {
                        log.error("Failed to serialize DLQ message", e);
                        throw new RuntimeException("Failed to serialize DLQ message", e);
                    }
                })
                .flatMap(dlqFields -> streamCommands.xadd(dlqStream, dlqFields))
                .onItem().transform(dlqMessageId -> {
                    log.warn("Sent message {} to DLQ stream {} with new ID {}",
                            messageId, dlqStream, dlqMessageId);
                    return (Void) null;
                })
                .onFailure().invoke(throwable ->
                        log.error("Failed to send message {} to DLQ stream {}",
                                messageId, dlqStream, throwable))
                .onFailure().recoverWithItem((Void) null);
    }

    /**
     * Validate the event envelope
     */
    protected boolean isInvalidEnvelope(EventEnvelope envelope) {
        if (envelope == null) {
            log.warn("Event envelope is null");
            return true;
        }

        if (envelope.getEventId() == null) {
            log.warn("Event envelope missing eventId");
            return true;
        }

        if (envelope.getOccurredAt() == null) {
            log.warn("Event envelope missing occurredAt");
            return true;
        }

        if (envelope.getPayload() == null) {
            log.warn("Event envelope missing payload");
            return true;
        }

        return false;
    }
}
