package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.usecase.ProcessTransactionUpdatedUseCase;
import com.portfolio.infrastructure.stream.dto.EventEnvelope;
import com.portfolio.infrastructure.stream.dto.TransactionUpdatedEvent;
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
 * Consumer for transaction:updated stream events
 */
@ApplicationScoped
public class TransactionUpdatedConsumer extends BaseRedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionUpdatedConsumer.class);
    private static final String STREAM_NAME = "transaction:updated";

    private final ProcessTransactionUpdatedUseCase processTransactionUpdatedUseCase;

    public TransactionUpdatedConsumer(ProcessTransactionUpdatedUseCase processTransactionUpdatedUseCase) {
        this.processTransactionUpdatedUseCase = processTransactionUpdatedUseCase;
    }

    @PostConstruct
    void init() {
        initializeStreamCommands();
        log.info("TransactionUpdatedConsumer initialized for stream: {}", STREAM_NAME);
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
        log.info("Processing transaction updated event: {} from message: {}",
                envelope.getEventId(), messageId);

        return switch (envelope.getPayload()) {
            case TransactionUpdatedEvent updatedEvent -> {
                log.info("🔄 TRANSACTION UPDATED EVENT RECEIVED:");
                log.info("   Event ID: {}", envelope.getEventId());
                log.info("   Occurred At: {}", envelope.getOccurredAt());

                var previousTx = updatedEvent.previousTransaction();
                var newTx = updatedEvent.newTransaction();

                log.info("   PREVIOUS TRANSACTION:");
                log.info("      ID: {}", previousTx.id());
                log.info("      Ticker: {}", previousTx.ticker());
                log.info("      Type: {}", previousTx.transactionType());
                log.info("      Quantity: {}", previousTx.quantity());
                log.info("      Price: {}", previousTx.price());

                log.info("   NEW TRANSACTION:");
                log.info("      ID: {}", newTx.id());
                log.info("      Ticker: {}", newTx.ticker());
                log.info("      Type: {}", newTx.transactionType());
                log.info("      Quantity: {}", newTx.quantity());
                log.info("      Price: {}", newTx.price());

                // Create command from event with both previous and new transactions
                var command = new ProcessTransactionUpdatedUseCase.Command(
                        new ProcessTransactionUpdatedUseCase.TransactionData(
                                previousTx.id(),
                                previousTx.ticker(),
                                previousTx.transactionType(),
                                previousTx.quantity(),
                                previousTx.price(),
                                previousTx.fees(),
                                previousTx.currency(),
                                previousTx.transactionDate()
                        ),
                        new ProcessTransactionUpdatedUseCase.TransactionData(
                                newTx.id(),
                                newTx.ticker(),
                                newTx.transactionType(),
                                newTx.quantity(),
                                newTx.price(),
                                newTx.fees(),
                                newTx.currency(),
                                newTx.transactionDate()
                        ),
                        envelope.getOccurredAt()
                );

                final Context context = VertxContext.getOrCreateDuplicatedContext();
                final Executor executor = action -> context.runOnContext(ignored -> action.run());

                // Process through use case in a duplicated context
                yield Uni.createFrom().voidItem()
                        .emitOn(executor)
                        .onItem().transformToUni(ignore -> processTransactionUpdatedUseCase.execute(command)
                                .onItem().invoke(result -> {
                                    switch (result) {
                                        case ProcessTransactionUpdatedUseCase.Result.Success success ->
                                                log.info("✅ Successfully processed transaction updated event, position: {}",
                                                        success.position().getTicker());
                                        case ProcessTransactionUpdatedUseCase.Result.Ignored ignored ->
                                                log.info("⏭️ Ignored out-of-order transaction updated event: {}",
                                                        ignored.reason());
                                        case ProcessTransactionUpdatedUseCase.Result.Error error ->
                                                log.error("❌ Failed to process transaction updated event: {} - {}",
                                                        error.error(), error.message());
                                    }
                                })
                                .replaceWithVoid()
                        );
            }
            default -> Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Expected TransactionUpdatedEvent but got: " + envelope.getPayload().getClass().getSimpleName()
                    )
            );
        };
    }
}
