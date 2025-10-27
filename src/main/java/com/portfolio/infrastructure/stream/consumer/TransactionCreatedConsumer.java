package com.portfolio.infrastructure.stream.consumer;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.usecase.ProcessTransactionCreatedUseCase;
import com.portfolio.infrastructure.stream.config.RedisStreamConfig;
import com.portfolio.infrastructure.stream.dto.TransactionCreatedEvent;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumer for transaction:created stream events
 */
@ApplicationScoped
public class TransactionCreatedConsumer extends BaseRedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionCreatedConsumer.class);
    private static final String STREAM_NAME = "transaction:created";

    private final Map<String, String> streamOffsets;
    private final XReadGroupArgs xReadGroupArgs;
    private final ProcessTransactionCreatedUseCase processTransactionCreatedUseCase;
    private final ConcurrentHashMap<String, AtomicInteger> replayAttempts = new ConcurrentHashMap<>();

    private volatile Cancellable consumerSubscription;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeProcessing = new AtomicInteger(0);

    public TransactionCreatedConsumer(ProcessTransactionCreatedUseCase processTransactionCreatedUseCase,
                                      RedisStreamConfig config) {
        this.processTransactionCreatedUseCase = processTransactionCreatedUseCase;
        streamOffsets = new java.util.HashMap<>();
        streamOffsets.put(STREAM_NAME, ">"); // Read only new messages not yet delivered
        xReadGroupArgs = new io.quarkus.redis.datasource.stream.XReadGroupArgs()
                .count(config.readCount())
                .block(java.time.Duration.ofMillis(config.blockMs()));
    }

    @PostConstruct
    void init() {
        initializeStreamCommands();
        initializeMetrics(STREAM_NAME);
        log.info("TransactionCreatedConsumer initialized for stream: {}", STREAM_NAME);
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

    public Multi<ProcessTransactionCreatedUseCase.Result> createConsumerPipeline() {
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
                .onFailure().retry().withBackOff(Duration.ofSeconds(1)).atMost(3)
                .onFailure().recoverWithItem(List.of());
    }

    private Uni<ProcessTransactionCreatedUseCase.Command> parseStreamMessage(StreamMessage<String, String, String> message) {
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
                    } else if (envelope.getPayload() instanceof TransactionCreatedEvent transactionCreatedEvent) {
                        return new ProcessTransactionCreatedUseCase.Command(
                                transactionCreatedEvent.id(),
                                transactionCreatedEvent.ticker(),
                                transactionCreatedEvent.transactionType(),
                                transactionCreatedEvent.quantity(),
                                transactionCreatedEvent.price(),
                                transactionCreatedEvent.fees(),
                                transactionCreatedEvent.currency(),
                                transactionCreatedEvent.transactionDate(),
                                envelope.getOccurredAt(),
                                transactionCreatedEvent.exchange(),
                                transactionCreatedEvent.country()
                        );
                    }

                    throw new IllegalArgumentException("Invalid envelope");
                })
                .invoke(() -> log.info("Command for transaction created parsed successfully"))
                .onFailure().transform(error -> {
                    log.error("Failed to parse message {} from stream: {} with fields: {}", messageId, STREAM_NAME, fields);
                    throw new IllegalArgumentException("Invalid envelope");
                });
    }

    private Uni<ProcessTransactionCreatedUseCase.Result> processMessage(StreamMessage<String, String, String> message) {
        return processMessageWithCommand(message, null);
    }

    private Uni<ProcessTransactionCreatedUseCase.Result> processMessageWithCommand(
            StreamMessage<String, String, String> message,
            ProcessTransactionCreatedUseCase.Command cachedCommand) {

        final Context context = VertxContext.getOrCreateDuplicatedContext();
        final Executor executor = action -> context.runOnContext(ignored -> action.run());
        final String messageId = message.id();

        // Get or create command
        Uni<ProcessTransactionCreatedUseCase.Command> commandUni = cachedCommand != null
                ? Uni.createFrom().item(cachedCommand)
                : parseStreamMessage(message);

        return processingTimer.record(() -> Uni.createFrom().voidItem()
                .emitOn(executor)
                .onItem().transformToUni(ignored -> commandUni)
                .flatMap(command -> processTransactionCreatedUseCase.execute(command)
                        .onItem().transformToUni(result -> handleProcessingResult(message, command, result)))
                .onFailure().recoverWithUni(throwable -> {
                    log.error("Exception while processing message {}: {}", messageId, throwable.getMessage(), throwable);
                    acknowledgeMessage(messageId); // ACK to avoid infinite retries on unexpected errors
                    replayAttempts.remove(messageId);
                    return Uni.createFrom().item(
                            new ProcessTransactionCreatedUseCase.Result.Error(
                                    Errors.ProcessTransactionEvent.UNEXPECTED_ERROR,
                                    throwable.getMessage()));
                }));
    }

    private Uni<ProcessTransactionCreatedUseCase.Result> handleProcessingResult(
            StreamMessage<String, String, String> message,
            ProcessTransactionCreatedUseCase.Command command,
            ProcessTransactionCreatedUseCase.Result result) {

        final String messageId = message.id();

        return switch (result) {
            case ProcessTransactionCreatedUseCase.Result.Success success -> {
                log.info("✅ Successfully applied transaction, position: {} now has {} shares",
                        success.position().getTicker(), success.position().getSharesOwned());
                acknowledgeMessage(messageId);
                replayAttempts.remove(messageId);
                processedCounter.increment();
                yield Uni.createFrom().item(result);
            }
            case ProcessTransactionCreatedUseCase.Result.Ignored ignored -> {
                log.info("⏭️ Ignored out-of-order transaction created event: {}", ignored.reason());
                acknowledgeMessage(messageId);
                replayAttempts.remove(messageId);
                yield Uni.createFrom().item(result);
            }
            case ProcessTransactionCreatedUseCase.Result.Error error -> {
                log.error("❌ Failed to process transaction created event: {} - {}", error.error(), error.message());
                acknowledgeMessage(messageId); // ACK to avoid infinite retries
                replayAttempts.remove(messageId);
                errorCounter.increment();
                yield Uni.createFrom().item(result);
            }
            case ProcessTransactionCreatedUseCase.Result.Replay replay -> {
                int attempts = replayAttempts.computeIfAbsent(messageId, k -> new AtomicInteger(0))
                        .incrementAndGet();

                if (attempts >= config.maxReplayAttempts()) {
                    log.warn("⚠️ Max replay attempts ({}) reached for message {}. Acknowledging to prevent infinite loop.",
                            config.maxReplayAttempts(), messageId);
                    acknowledgeMessage(messageId);
                    replayAttempts.remove(messageId);
                    errorCounter.increment();
                    yield Uni.createFrom().item(result);
                } else {
                    log.info("🔄 Scheduling replay #{} for transaction {} after {}s",
                            attempts, replay.transactionId(), config.replayDelaySeconds());

                    // Schedule delayed replay - don't ACK yet
                    yield scheduleReplay(message, command, attempts)
                            .replaceWith(result);
                }
            }
        };
    }

    private Uni<Void> scheduleReplay(
            StreamMessage<String, String, String> message,
            ProcessTransactionCreatedUseCase.Command command,
            int attemptNumber) {

        return Uni.createFrom().voidItem()
                .onItem().delayIt().by(Duration.ofSeconds(config.replayDelaySeconds()))
                .onItem().transformToUni(ignored -> {
                    log.info("⏰ Replaying message {} (attempt #{})", message.id(), attemptNumber);
                    return processMessageWithCommand(message, command).replaceWithVoid();
                })
                .onFailure().invoke(throwable ->
                        log.error("Failed to replay message {}: {}", message.id(), throwable.getMessage()));
    }

    public boolean isRunning() {
        return running.get();
    }

    private void scheduleRestart() {
        Uni.createFrom().voidItem()
                .onItem().delayIt().by(Duration.ofSeconds(5))
                .subscribe().with(__ -> {
                    if (running.compareAndSet(true, false)) {
                        log.info("Restarting create consumer after error");
                        consumerSubscription = startConsuming();
                    }
                });
    }
}
