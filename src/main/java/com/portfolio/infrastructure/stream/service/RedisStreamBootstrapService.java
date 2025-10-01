package com.portfolio.infrastructure.stream.service;

import com.portfolio.infrastructure.stream.config.RedisStreamConfig;
import com.portfolio.infrastructure.stream.consumer.TransactionCreatedConsumer;
import com.portfolio.infrastructure.stream.consumer.TransactionDeletedConsumer;
import com.portfolio.infrastructure.stream.consumer.TransactionUpdatedConsumer;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.ReactiveStreamCommands;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
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

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    RedisStreamConfig config;

    @Inject
    TransactionCreatedConsumer createdConsumer;

    @Inject
    TransactionUpdatedConsumer updatedConsumer;

    @Inject
    TransactionDeletedConsumer deletedConsumer;

    private ReactiveStreamCommands<String, String, String> streamCommands;

    /**
     * Initialize on application startup
     */
    void onStart(@Observes StartupEvent ev) {
        log.info("Starting Redis Stream bootstrap process");

        streamCommands = redisDataSource.stream(String.class, String.class, String.class);

        initializeStreamsAndConsumerGroup()
                .onItem().transformToUni(ignored -> startAllConsumers())
                .subscribe().with(
                        ignored -> log.info("Redis Stream bootstrap completed successfully"),
                        throwable -> log.error("Redis Stream bootstrap failed", throwable)
                );
    }

    /**
     * Initialize streams and consumer group
     */
    private Uni<Void> initializeStreamsAndConsumerGroup() {
        log.info("Initializing streams and consumer group: {}", config.group());

        List<String> streamNames = List.of(
                "transaction:created",
                "transaction:updated",
                "transaction:deleted"
        );

        return Uni.join().all(
                        streamNames.stream()
                                .map(this::createConsumerGroup)
                                .toList()
                ).andFailFast()
                .onItem().transform(results -> {
                    log.info("Successfully initialized {} streams with consumer group: {}",
                            streamNames.size(), config.group());
                    return (Void) null;
                });
    }

    /**
     * Create consumer group for a stream (idempotent)
     * Uses MKSTREAM to automatically create the stream if it doesn't exist
     */
    private Uni<Void> createConsumerGroup(String streamName) {
        log.debug("Creating consumer group {} for stream: {} (with MKSTREAM)", config.group(), streamName);

        io.quarkus.redis.datasource.stream.XGroupCreateArgs args =
                new io.quarkus.redis.datasource.stream.XGroupCreateArgs()
                        .mkstream();

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
                        log.debug("Consumer group {} already exists for stream: {}",
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
    private Uni<Void> startAllConsumers() {
        log.info("Starting all Redis Stream consumers");

        return Uni.join().all(
                        createdConsumer.startConsuming(),
                        updatedConsumer.startConsuming(),
                        deletedConsumer.startConsuming()
                ).andFailFast()
                .onItem().transform(results -> {
                    log.info("All Redis Stream consumers started successfully");
                    return (Void) null;
                })
                .onFailure().invoke(throwable ->
                        log.error("Failed to start Redis Stream consumers", throwable))
                .onFailure().recoverWithItem((Void) null);
    }
}
