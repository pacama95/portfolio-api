package com.portfolio.infrastructure.stream.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "app.processing")
public interface ProcessingConfig {
    /**
     * Number of concurrent message processors
     */
    @WithDefault("4")
    int parallelism();

    /**
     * Buffer size for the processing pipeline
     */
    @WithDefault("256")
    int bufferSize();

    /**
     * Prefetch size for upstream requests
     */
    @WithDefault("2")
    int prefetch();
}
