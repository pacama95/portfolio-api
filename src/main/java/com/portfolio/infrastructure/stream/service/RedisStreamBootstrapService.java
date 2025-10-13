package com.portfolio.infrastructure.stream.service;

import com.portfolio.infrastructure.stream.config.RedisStreamConfig;
import com.portfolio.infrastructure.stream.consumer.TransactionCreatedConsumer;
import com.portfolio.infrastructure.stream.consumer.TransactionDeletedConsumer;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.ReactiveStreamCommands;
import io.quarkus.redis.datasource.stream.XGroupCreateArgs;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service responsible for bootstrapping Redis Streams and starting consumers
 */
@ApplicationScoped
public class RedisStreamBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamBootstrapService.class);
    public static final String TRANSACTION_DELETED_STREAM = "transaction:deleted";
    public static final String TRANSACTION_CREATED_STREAM = "transaction:created";
    private static final List<String> TRANSACTION_STREAMS =
            List.of(TRANSACTION_CREATED_STREAM, TRANSACTION_DELETED_STREAM);

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    RedisStreamConfig config;

    @Inject
    TransactionCreatedConsumer createdConsumer;

    @Inject
    TransactionDeletedConsumer deletedConsumer;

    private Cancellable transactionCreatedConsumer;
    private Cancellable transactionDeletedConsumer;

    private ReactiveStreamCommands<String, String, String> streamCommands;

    /**
     * Initialize on application startup
     * IMPORTANT: This method blocks to ensure consumers are started before the application is ready.
     * This is critical for native image builds where async subscriptions may not complete before startup.
     */
    void onStart(@Observes StartupEvent ev) {
        log.info("Starting Redis Stream bootstrap process");

        streamCommands = redisDataSource.stream(String.class, String.class, String.class);

        try {
            // Block and await the initialization to complete before starting consumers
            // This ensures consumers are running before the application is considered started
            initializeStreamsAndConsumerGroup()
                    .await().indefinitely();
            
            log.info("Consumer group {} ready", config.group());
            startAllConsumers();
            log.info("All Redis Stream consumers started successfully");
        } catch (Exception e) {
            log.error("Failed to initialize consumer group and start consumers", e);
            throw new RuntimeException("Redis Stream initialization failed", e);
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        log.info("Stopping Redis Stream bootstrap process");

        if (transactionCreatedConsumer != null) {
            transactionCreatedConsumer.cancel();
        }

        if (transactionDeletedConsumer != null) {
            transactionDeletedConsumer.cancel();
        }
    }

    /**
     * Initialize streams and consumer group
     */
    private Uni<Void> initializeStreamsAndConsumerGroup() {
        log.info("Initializing streams and consumer group: {}", config.group());

        return Uni.join().all(
                        TRANSACTION_STREAMS.stream()
                                .map(this::createConsumerGroup)
                                .toList()
                ).andFailFast()
                .onItem().transform(results -> {
                    log.info("Successfully initialized {} streams with consumer group: {}",
                            TRANSACTION_STREAMS.size(), config.group());
                    return null;
                });
    }

    /**
     * Create consumer group for a stream (idempotent)
     * Uses MKSTREAM to automatically create the stream if it doesn't exist
     */
    private Uni<Void> createConsumerGroup(String streamName) {
        log.info("Creating consumer group {} for stream: {} (MKSTREAM)", config.group(), streamName);

        XGroupCreateArgs args = new XGroupCreateArgs().mkstream();
        
        return streamCommands.xgroupCreate(streamName, config.group(), "0", args)
                .onItem().transform(result -> {
                    log.info("Consumer group {} and stream {} created/verified successfully",
                            config.group(), streamName);
                    return (Void) null;
                })
                .onFailure().recoverWithUni(throwable -> {
                    // BUSYGROUP error means the group already exists, which is fine
                    if (throwable.getMessage() != null &&
                            throwable.getMessage().contains("BUSYGROUP")) {
                        log.info("Consumer group {} already exists for stream: {}. Skipping creation.",
                                config.group(), streamName);
                        return Uni.createFrom().voidItem();
                    }

                    log.error("Failed to create consumer group {} for stream: {}",
                            config.group(), streamName, throwable);
                    return Uni.createFrom().failure(throwable);
                });
    }

    /**
     * Start all consumers
     */
    private void startAllConsumers() {
        log.info("Starting all Redis Stream consumers");

        transactionCreatedConsumer = createdConsumer.startConsuming();
        transactionDeletedConsumer = deletedConsumer.startConsuming();
    }
}
