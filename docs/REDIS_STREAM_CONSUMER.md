## Redis Stream Consumer Design

This document describes the Redis Stream consumer for the Portfolio Assistant API. It defines the consumer group, streams, idempotent processing strategy, out-of-order handling, configuration, and testing approach.

### Goals
- Consume transaction lifecycle events to build and persist `Position` state
- Be idempotent and resilient to out-of-order delivery
- Expose the latest database state to clients after processing

### Streams and Consumer Group
- Streams:
  - `transaction:created`
  - `transaction:updated`
  - `transaction:deleted`
- One consumer group, e.g. `portfolio-consumers`
- Multiple consumers can be added for scalability via Redis consumer groups. Each consumer reads pending messages, acknowledges processed entries, and uses claim/retry for failures.

### Event Schema
All events share the common envelope:
- `eventId` (UUID): unique event identifier
- `occurredAt` (ISO-8601 UTC): business time when the change happened
- `messageCreatedAt` (ISO-8601 UTC): time the message was created/enqueued
- `payload` (object): full data

Examples are provided in `redisStreamEvents/`:
- Created: `redisStreamEvents/transactionCreatedEvent.json`
- Updated: `redisStreamEvents/transactionUpdated.json`
- Deleted: `redisStreamEvents/transactionDeleted.json`

### Idempotent and Out-of-Order Strategy
Design assumptions:
- Created events are upserts (create if not exists)
- Updated events include full state (no deltas)
- Deleted events are soft deletes and can be reapplied

Processing rules per ticker/transaction:
1. Look up current `Position` by ticker.
2. Compare event `occurredAt` with the stored `Position`’s last-applied timestamp.
3. If the event is older (occurredAt <= last-applied), ignore it (idempotency and out-of-order tolerance).
4. Otherwise, apply per type:
   - Created: upsert `Position` with computed fields derived from payload
   - Updated: overwrite relevant fields from full payload-derived state
   - Deleted: mark `Position.isActive = false` (soft delete)
5. Persist changes and store the new last-applied timestamp.
6. Return the resulting `Position` state to the caller (for synchronous request/response flows) or provide it via downstream APIs.

Implementation note: Persist the last-applied timestamp per `Position` (e.g., `lastEventAppliedAt`). Use it solely for event ordering, distinct from display fields like `lastUpdated`.

### Error Handling and Retries
- Use consumer group pending entries list (PEL) and dead-letter strategies:
  - On transient errors, do not ACK; allow re-delivery or use XCLAIM after a timeout
  - After N retries, move to a dead-letter stream `transaction:dlq`
- Validate envelope and payload fields; invalid messages go directly to DLQ with reason
- Log with correlation: `eventId`, stream, group, consumer name

### Startup and Bootstrapping
- On startup, create streams and the consumer group if they do not exist (idempotent XGROUP CREATE with MKSTREAM)
- Consumers start with `>` to receive new messages and use `0` when scanning PEL during recovery
- Use a configurable consumer name (e.g., hostname + instance id)

### Configuration (Quarkus)
Properties (example):
```
quarkus.redis.hosts=redis://localhost:6379
app.redis.streams[0].name=transaction:created
app.redis.streams[1].name=transaction:updated
app.redis.streams[2].name=transaction:deleted
app.redis.group=portfolio-consumers
app.redis.consumer-name=${HOSTNAME:local}-${random.value}
app.redis.block-ms=5000
app.redis.read-count=50
app.redis.max-retries=5
```

### Processing Flow (Reactive)
1. XREADGROUP from all three streams with BLOCK = `block-ms`, COUNT = `read-count`
2. For each message:
   - Parse envelope and payload
   - Map to a domain command that computes a `PositionUpdatedEvent`-like structure
   - Execute reactive use case to upsert `Position`
   - ACK on success; on failure, leave pending for retry

### Mapping to Domain
- For created/updated: extract ticker, quantity, price, currency, etc., compute average price and totals; produce a consistent domain update
- For deleted: set `isActive = false`
- Always set the event ordering timestamp from `occurredAt`

### Testing Strategy
- Unit tests:
  - Idempotency: newer vs older `occurredAt` ordering
  - Upsert behavior for created/updated
  - Soft delete applied regardless; older create ignored after delete
  - Use Mutiny `UniAssertSubscriber` per project conventions
- Integration tests (optional):
  - Spin up Redis (Testcontainers), push events, assert DB state and ACKs

### Observability
- Metrics: processed count per stream, ACK rate, retry count, DLQ count
- Tracing: include `eventId` and stream name as span attributes

### Future Considerations
- Schema validation via JSON Schema
- Backfill/replay with `occurredAt` safeguards
- Partitioning strategy for high-throughput tickers



