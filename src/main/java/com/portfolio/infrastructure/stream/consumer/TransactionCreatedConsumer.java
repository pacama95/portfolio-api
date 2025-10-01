package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.usecase.ProcessTransactionCreatedUseCase;
import com.portfolio.infrastructure.stream.dto.EventEnvelope;
import com.portfolio.infrastructure.stream.dto.TransactionCreatedEvent;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Consumer for transaction:created stream events
 */
@ApplicationScoped
public class TransactionCreatedConsumer extends BaseRedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionCreatedConsumer.class);
    private static final String STREAM_NAME = "transaction:created";

    private final ProcessTransactionCreatedUseCase processTransactionCreatedUseCase;

    public TransactionCreatedConsumer(ProcessTransactionCreatedUseCase processTransactionCreatedUseCase) {
        this.processTransactionCreatedUseCase = processTransactionCreatedUseCase;
    }

    @PostConstruct
    void init() {
        initializeStreamCommands();
        log.info("TransactionCreatedConsumer initialized for stream: {}", STREAM_NAME);
    }

    @Override
    protected String getStreamName() {
        return STREAM_NAME;
    }

    /**
     * Start consuming messages from the stream
     */
    @ActivateRequestContext
    public Uni<Void> startConsuming() {
        log.info("Starting to consume from stream: {}", STREAM_NAME);

        return consumeMessages()
                .onFailure().invoke(throwable ->
                        log.error("Error in consumer loop for stream: {}", STREAM_NAME, throwable))
                .onFailure().recoverWithUni(throwable -> {
                    log.info("Restarting consumer for stream: {} after error", STREAM_NAME);
                    return Uni.createFrom().voidItem().onItem().delayIt().by(java.time.Duration.ofSeconds(5))
                            .flatMap(ignored -> startConsuming());
                });
    }

    private Uni<Void> consumeMessages() {
        java.util.Map<String, String> streamOffsets = new java.util.HashMap<>();
        streamOffsets.put(STREAM_NAME, ">");

        io.quarkus.redis.datasource.stream.XReadGroupArgs args =
                new io.quarkus.redis.datasource.stream.XReadGroupArgs()
                        .count(config.readCount())
                        .block(java.time.Duration.ofMillis(config.blockMs()));

        return streamCommands.xreadgroup(
                        config.group(),
                        config.consumerName(),
                        streamOffsets,
                        args
                )
                .onItem().transformToUni(messages -> {
                    if (messages != null && !messages.isEmpty()) {
                        log.info("Received {} messages from stream: {} via consumer group: {}",
                                messages.size(), STREAM_NAME, config.group());
                        return processMessages(messages);
                    } else {
                        log.debug("No messages received from stream: {} for consumer: {}",
                                STREAM_NAME, config.consumerName());
                        return Uni.createFrom().voidItem();
                    }
                })
                .onItem().transformToUni(ignored -> consumeMessages()); // Continue consuming
    }

    private Uni<Void> processMessages(List<StreamMessage<String, String, String>> messages) {
        if (messages.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        log.debug("Processing {} messages from stream: {}", messages.size(), STREAM_NAME);

        return Uni.join().all(
                        messages.stream()
                                .map(this::processMessage)
                                .toList()
                ).andFailFast()
                .onItem().transform(results -> null);
    }

    private Uni<Void> processMessage(StreamMessage<String, String, String> message) {
        String messageId = message.id();
        Map<String, String> fields = message.payload();
        Context context = Vertx.currentContext();

        log.info("Processing message {} from stream: {} with fields: {}", messageId, STREAM_NAME, fields);

        return parseMessage(fields)
                .onItem().transformToUni(envelope -> {
                    if (!isValidEnvelope(envelope)) {
                        log.warn("Invalid event envelope for message {}: {}", messageId, envelope);
                        // ACK invalid messages to avoid infinite retries
                        return acknowledgeMessage(messageId);
                    }

                    return processEvent(envelope, messageId);
                })
                .onItem().transformToUni(ignored -> acknowledgeMessage(messageId))
                .onFailure().recoverWithUni(throwable -> {
                    log.error("Failed to process message {} from stream: {}",
                            messageId, STREAM_NAME, throwable);

                    // For now, ACK failed messages to avoid infinite retries
                    // TODO: Implement retry logic with PEL or send to DLQ
                    return acknowledgeMessage(messageId);
                });
    }

    @Override
    protected Uni<Void> processEvent(EventEnvelope envelope, String messageId) {
        log.info("Processing transaction created event: {} from message: {}",
                envelope.getEventId(), messageId);

        return switch (envelope.getPayload()) {
            case TransactionCreatedEvent createdEvent -> {
                log.info("✅ TRANSACTION CREATED EVENT RECEIVED:");
                log.info("   Event ID: {}", envelope.getEventId());
                log.info("   Occurred At: {}", envelope.getOccurredAt());
                log.info("   Transaction ID: {}", createdEvent.id());
                log.info("   Ticker: {}", createdEvent.ticker());
                log.info("   Type: {}", createdEvent.transactionType());
                log.info("   Quantity: {}", createdEvent.quantity());
                log.info("   Price: {}", createdEvent.price());

                // Create command from event
                var command = new ProcessTransactionCreatedUseCase.Command(
                        createdEvent.id(),
                        createdEvent.ticker(),
                        createdEvent.transactionType(),
                        createdEvent.quantity(),
                        createdEvent.price(),
                        createdEvent.fees(),
                        createdEvent.currency(),
                        createdEvent.transactionDate(),
                        envelope.getOccurredAt()
                );

                final Context context = VertxContext.getOrCreateDuplicatedContext();
                final Executor executor = action -> context.runOnContext(ignored -> action.run());

                // Process through use case in a duplicated context
                yield Uni.createFrom().voidItem()
                        .emitOn(executor)
                        .onItem().transformToUni(ignore -> processTransactionCreatedUseCase.execute(command)
                                .onItem().invoke(result -> {
                                    switch (result) {
                                        case ProcessTransactionCreatedUseCase.Result.Success success ->
                                                log.info("✅ Successfully processed transaction created event, position: {}",
                                                        success.position().getTicker());
                                        case ProcessTransactionCreatedUseCase.Result.Ignored ignored ->
                                                log.info("⏭️ Ignored out-of-order transaction created event: {}",
                                                        ignored.reason());
                                        case ProcessTransactionCreatedUseCase.Result.Error error ->
                                                log.error("❌ Failed to process transaction created event: {} - {}",
                                                        error.error(), error.message());
                                    }
                                })
                                .replaceWithVoid());
            }
            default -> Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Expected TransactionCreatedEvent but got: " + envelope.getPayload().getClass().getSimpleName()
                    )
            );
        };
    }
}
