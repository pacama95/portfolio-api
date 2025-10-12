package com.portfolio.infrastructure.stream.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/**
 * Configuration properties for Redis Stream consumers
 */
@ConfigMapping(prefix = "app.redis")
public interface RedisStreamConfig {

    /**
     * List of stream configurations
     */
    List<StreamConfig> streams();

    /**
     * Consumer group name
     */
    @WithDefault("portfolio-consumers")
    String group();

    /**
     * Consumer name (should be unique per instance)
     */
    @WithDefault("${HOSTNAME:local}-${random.value}")
    String consumerName();

    /**
     * Block time in milliseconds for XREADGROUP
     */
    @WithDefault("5000")
    Long blockMs();

    /**
     * Number of messages to read per batch
     */
    @WithDefault("50")
    Integer readCount();

    /**
     * Maximum number of retries before moving to DLQ
     */
    @WithDefault("5")
    Integer maxRetries();

    /**
     * Dead letter queue stream suffix
     */
    @WithDefault("dlq")
    String dlqSuffix();

    /**
     * Delay in seconds before replaying a message
     */
    @WithDefault("10")
    Long replayDelaySeconds();

    /**
     * Maximum number of replay attempts before considering it a failure
     */
    @WithDefault("3")
    Integer maxReplayAttempts();

    /**
     * Configuration for individual streams
     */
    interface StreamConfig {
        /**
         * Stream name
         */
        String name();
    }
}
