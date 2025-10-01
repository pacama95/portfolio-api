package com.portfolio.infrastructure.stream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Transaction updated event payload containing previous and new transaction data
 * To apply the update, reverse the previous transaction and then apply the new transaction
 */
@RegisterForReflection
public record TransactionUpdatedEvent(
    @JsonProperty("previousTransaction") TransactionData previousTransaction,
    @JsonProperty("newTransaction") TransactionData newTransaction
) implements TransactionEvent {}
