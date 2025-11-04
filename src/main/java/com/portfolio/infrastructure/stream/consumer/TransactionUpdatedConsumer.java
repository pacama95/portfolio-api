package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.usecase.ProcessTransactionUpdatedUseCase;
import com.portfolio.infrastructure.stream.config.RedisStreamConfig;
import com.portfolio.infrastructure.stream.dto.TransactionData;
import com.portfolio.infrastructure.stream.dto.TransactionUpdatedEvent;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.quarkus.redis.datasource.stream.XReadGroupArgs;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.Context;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumer for transaction:updated stream events
 */
@ApplicationScoped
public class TransactionUpdatedConsumer extends BaseRedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionUpdatedConsumer.class);
    private static final String STREAM_NAME = "transaction:updated";

    private final Map<String, String> streamOffsets;
    private final XReadGroupArgs xReadGroupArgs;
    private final ProcessTransactionUpdatedUseCase processTransactionUpdatedUseCase;
    private final ConcurrentHashMap<String, AtomicInteger> replayAttempts = new ConcurrentHashMap<>();

    private volatile Cancellable consumerSubscription;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeProcessing = new AtomicInteger(0);

    public TransactionUpdatedConsumer(ProcessTransactionUpdatedUseCase processTransactionUpdatedUseCase,
                                      RedisStreamConfig config) {
        this.processTransactionUpdatedUseCase = processTransactionUpdatedUseCase;
        streamOffsets = new java.util.HashMap<>();
        streamOffsets.put(STREAM_NAME, ">"); // Read only new messages not yet delivered
        xReadGroupArgs = new XReadGroupArgs()
                .count(config.readCount())
                .block(java.time.Duration.ofMillis(config.blockMs()));
    }

    @PostConstruct
    void init() {
        initializeStreamCommands();
        initializeMetrics(STREAM_NAME);
        log.info("TransactionUpdatedConsumer initialized for stream: {}", STREAM_NAME);
    }

    @Override
    protected String getStreamName() {
        return STREAM_NAME;
    }

    public Cancellable startConsuming() {
        if (running.compareAndSet(false, true)) {
            log.info("🚀 Starting Redis Stream consumer for stream: {} with consumer: {} in group: {}",
                    STREAM_NAME, config.consumerName(), config.group());

            consumerSubscription = createConsumerPipeline()
                    .subscribe().with(
                            result -> log.debug("Processed message successfully"),
                            failure -> {
                                log.error("❌ Consumer pipeline failed for stream {}", STREAM_NAME, failure);
                                if (running.get()) {
                                    scheduleRestart();
                                }
                            },
                            () -> log.error("⚠️ Consumer pipeline completed unexpectedly for stream {}", STREAM_NAME)
                    );

            log.info("✅ Consumer subscription active for stream: {}", STREAM_NAME);
        } else {
            log.warn("Consumer for stream {} is already running", STREAM_NAME);
        }

        return consumerSubscription;
    }

    public Multi<ProcessTransactionUpdatedUseCase.Result> createConsumerPipeline() {
        return Multi.createBy().repeating()
                .uni(this::fetchMessages)
                .whilst(__ -> running.get())
                .onItem().transformToMulti(streamMessages -> Multi.createFrom().iterable(streamMessages))
                .concatenate()
                .onItem().call(message -> {
                    activeProcessing.incrementAndGet();
                    log.info("Processing message: {}", message.id());
                    return Uni.createFrom().voidItem();
                })
                .onItem().transformToUniAndConcatenate(
                        message ->
                                processMessage(message)
                                        .onTermination().invoke(activeProcessing::decrementAndGet))
                .onOverflow().buffer(processingConfig.bufferSize())
                .onRequest().invoke(n -> log.info("Requested {} items", n));

    }

    private Uni<List<StreamMessage<String, String, String>>> fetchMessages() {
        log.debug("Polling stream {} with consumer {} in group {}", STREAM_NAME, config.consumerName(), config.group());
        return streamCommands.xreadgroup(
                        config.group(),
                        config.consumerName(),
                        streamOffsets,
                        xReadGroupArgs)
                .onItem().invoke(messages -> {
                    if (!messages.isEmpty()) {
                        log.info("Fetched {} messages from stream {}", messages.size(), STREAM_NAME);
                    }
                })
                .onFailure().invoke(throwable ->
                        log.error("Failed to fetch messages from stream {}: {}", STREAM_NAME, throwable.getMessage()))
                .onFailure().retry().withBackOff(java.time.Duration.ofSeconds(1)).atMost(3)
                .onFailure().recoverWithItem(List.of());
    }

    private Uni<ProcessTransactionUpdatedUseCase.Command> parseStreamMessage(StreamMessage<String, String, String> message) {
        String messageId = message.id();
        Map<String, String> fields = message.payload();

        log.info("Processing message {} from stream: {} with fields: {}", messageId, STREAM_NAME, fields);

        return parseMessage(fields)
                .onItem().transform(envelope -> {
                    if (isInvalidEnvelope(envelope)) {
                        log.warn("Invalid event envelope for message {}: {}", messageId, envelope);
                        // ACK invalid messages to avoid infinite retries
                        acknowledgeMessage(messageId);
                        throw new IllegalArgumentException("Invalid envelope");
                    } else if (envelope.getPayload() instanceof TransactionUpdatedEvent transactionUpdatedEvent) {
                        TransactionData prevTxn = transactionUpdatedEvent.previousTransaction();
                        TransactionData newTxn = transactionUpdatedEvent.newTransaction();

                        log.info("Transaction update: id={}, previousTicker={}, newTicker={}, prevType={}, newType={}",
                                prevTxn.id(), prevTxn.ticker(), newTxn.ticker(),
                                prevTxn.transactionType(), newTxn.transactionType());

                        return new ProcessTransactionUpdatedUseCase.Command(
                                new ProcessTransactionUpdatedUseCase.TransactionData(
                                        prevTxn.id(),
                                        prevTxn.ticker(),
                                        prevTxn.transactionType(),
                                        prevTxn.quantity(),
                                        prevTxn.price(),
                                        prevTxn.fees(),
                                        prevTxn.currency(),
                                        prevTxn.transactionDate(),
                                        prevTxn.exchange(),
                                        prevTxn.country()
                                ),
                                new ProcessTransactionUpdatedUseCase.TransactionData(
                                        newTxn.id(),
                                        newTxn.ticker(),
                                        newTxn.transactionType(),
                                        newTxn.quantity(),
                                        newTxn.price(),
                                        newTxn.fees(),
                                        newTxn.currency(),
                                        newTxn.transactionDate(),
                                        newTxn.exchange(),
                                        newTxn.country()
                                ),
                                envelope.getOccurredAt()
                        );
                    }

                    throw new IllegalArgumentException("Invalid envelope");
                })
                .invoke(() -> log.info("Command for transaction updated parsed successfully"))
                .onFailure().transform(error -> {
                    log.error("Failed to parse message {} from stream: {} with fields: {}", messageId, STREAM_NAME, fields);
                    throw new IllegalArgumentException("Invalid envelope");
                });
    }

    private Uni<ProcessTransactionUpdatedUseCase.Result> processMessage(StreamMessage<String, String, String> message) {
        return processMessageWithCommand(message, null);
    }

    private Uni<ProcessTransactionUpdatedUseCase.Result> processMessageWithCommand(
            StreamMessage<String, String, String> message,
            ProcessTransactionUpdatedUseCase.Command cachedCommand) {

        final Context context = VertxContext.getOrCreateDuplicatedContext();
        final Executor executor = action -> context.runOnContext(ignored -> action.run());
        final String messageId = message.id();

        // Get or create command
        Uni<ProcessTransactionUpdatedUseCase.Command> commandUni = cachedCommand != null
                ? Uni.createFrom().item(cachedCommand)
                : parseStreamMessage(message);

        return processingTimer.record(() -> Uni.createFrom().voidItem()
                .emitOn(executor)
                .onItem().transformToUni(ignored -> commandUni)
                .flatMap(command -> processTransactionUpdatedUseCase.execute(command)
                        .onItem().transformToUni(result -> handleProcessingResult(message, command, result)))
                .onFailure().recoverWithUni(throwable -> {
                    log.error("Exception while processing message {}: {}", messageId, throwable.getMessage(), throwable);
                    acknowledgeMessage(messageId); // ACK to avoid infinite retries on unexpected errors
                    replayAttempts.remove(messageId);
                    return Uni.createFrom().item(
                            new ProcessTransactionUpdatedUseCase.Result.Error(
                                    Errors.ProcessTransactionEvent.UNEXPECTED_ERROR,
                                    throwable.getMessage()));
                }));
    }

    private Uni<ProcessTransactionUpdatedUseCase.Result> handleProcessingResult(
            StreamMessage<String, String, String> message,
            ProcessTransactionUpdatedUseCase.Command command,
            ProcessTransactionUpdatedUseCase.Result result) {

        final String messageId = message.id();

        return switch (result) {
            case ProcessTransactionUpdatedUseCase.Result.Success success -> {
                log.info("✅ Successfully updated transaction, position: {} now has {} shares",
                        success.position().getTicker(), success.position().getSharesOwned());
                acknowledgeMessage(messageId);
                replayAttempts.remove(messageId);
                processedCounter.increment();
                yield Uni.createFrom().item(result);
            }
            case ProcessTransactionUpdatedUseCase.Result.Ignored ignored -> {
                log.info("⏭️ Ignored transaction updated event: {}", ignored.reason());
                acknowledgeMessage(messageId);
                replayAttempts.remove(messageId);
                yield Uni.createFrom().item(result);
            }
            case ProcessTransactionUpdatedUseCase.Result.Error error -> {
                log.error("❌ Failed to process transaction updated event: {} - {}", error.error(), error.message());
                acknowledgeMessage(messageId);
                replayAttempts.remove(messageId);
                errorCounter.increment();
                yield Uni.createFrom().item(result);
            }
        };
    }

    public boolean isRunning() {
        return running.get();
    }

    private void scheduleRestart() {
        Uni.createFrom().voidItem()
                .onItem().delayIt().by(java.time.Duration.ofSeconds(5))
                .subscribe().with(__ -> {
                    if (running.compareAndSet(true, false)) {
                        log.info("Restarting update consumer after error");
                        consumerSubscription = startConsuming();
                    }
                });
    }
}
