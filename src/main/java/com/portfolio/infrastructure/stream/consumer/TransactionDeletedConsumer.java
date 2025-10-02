package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.usecase.ProcessTransactionDeletedUseCase;
import com.portfolio.infrastructure.stream.dto.EventEnvelope;
import com.portfolio.infrastructure.stream.dto.TransactionDeletedEvent;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Consumer for transaction:deleted stream events
 */
@ApplicationScoped
public class TransactionDeletedConsumer extends BaseRedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionDeletedConsumer.class);
    private static final String STREAM_NAME = "transaction:deleted";

    private final ProcessTransactionDeletedUseCase processTransactionDeletedUseCase;

    public TransactionDeletedConsumer(ProcessTransactionDeletedUseCase processTransactionDeletedUseCase) {
        this.processTransactionDeletedUseCase = processTransactionDeletedUseCase;
    }

    @PostConstruct
    void init() {
        initializeStreamCommands();
        log.info("TransactionDeletedConsumer initialized for stream: {}", STREAM_NAME);
    }

    @Override
    protected String getStreamName() {
        return STREAM_NAME;
    }

    /**
     * Start consuming messages from the stream
     */
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
        // Use XREADGROUP for proper consumer group functionality
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

        log.info("Processing message {} from stream: {} with fields: {}", messageId, STREAM_NAME, fields);

        return parseMessage(fields)
                .onItem().transformToUni(envelope -> {
                    if (isInvalidEnvelope(envelope)) {
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
        log.info("Processing transaction deleted event: {} from message: {}",
                envelope.getEventId(), messageId);

        return switch (envelope.getPayload()) {
            case TransactionDeletedEvent deletedEvent -> {
                log.info("🔄 TRANSACTION DELETED EVENT RECEIVED (ROLLBACK):");
                log.info("   Event ID: {}", envelope.getEventId());
                log.info("   Occurred At: {}", envelope.getOccurredAt());
                log.info("   Transaction ID: {}", deletedEvent.id());
                log.info("   Ticker: {}", deletedEvent.ticker());
                log.info("   Type: {}", deletedEvent.transactionType());
                log.info("   Quantity: {}", deletedEvent.quantity());
                log.info("   Price: {}", deletedEvent.price());

                // Create command from event for rollback
                var command = new ProcessTransactionDeletedUseCase.Command(
                        deletedEvent.id(),
                        deletedEvent.ticker(),
                        deletedEvent.transactionType(),
                        deletedEvent.quantity(),
                        deletedEvent.price(),
                        deletedEvent.fees(),
                        deletedEvent.currency(),
                        deletedEvent.transactionDate(),
                        envelope.getOccurredAt()
                );

                final Context context = VertxContext.getOrCreateDuplicatedContext();
                final Executor executor = action -> context.runOnContext(ignored -> action.run());

                // Process through use case in worker thread pool with Vertx context safety
                yield Uni.createFrom().voidItem()
                        .emitOn(executor)
                        .onItem().transformToUni(ignore -> processTransactionDeletedUseCase.execute(command)
                                .onItem().invoke(result -> {
                                    switch (result) {
                                        case ProcessTransactionDeletedUseCase.Result.Success success ->
                                                log.info("✅ Successfully rolled back transaction, position: {} now has {} shares",
                                                        success.position().getTicker(), success.position().getSharesOwned());
                                        case ProcessTransactionDeletedUseCase.Result.Ignored ignored ->
                                                log.info("⏭️ Ignored out-of-order transaction deleted event: {}",
                                                        ignored.reason());
                                        case ProcessTransactionDeletedUseCase.Result.Error error ->
                                                log.error("❌ Failed to process transaction deleted event: {} - {}",
                                                        error.error(), error.message());
                                    }
                                })
                                .replaceWithVoid()
                        );
            }
            default -> Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Expected TransactionDeletedEvent but got: " + envelope.getPayload().getClass().getSimpleName()
                    )
            );
        };
    }
}
