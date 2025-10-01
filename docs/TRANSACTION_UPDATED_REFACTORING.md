# Transaction Updated Event Refactoring

## Overview
The `TransactionUpdatedEvent` has been refactored to support differential updates. Instead of sending only the updated transaction data, the event now contains both the previous and new transaction states, allowing the system to calculate and apply the exact difference to positions.

## Changes Made

### 1. New `TransactionData` Record
**File**: `src/main/java/com/portfolio/infrastructure/stream/dto/TransactionData.java`

A new record that represents a single transaction with all its properties:
- `id`: UUID
- `ticker`: String
- `transactionType`: String (BUY/SELL)
- `quantity`: BigDecimal
- `price`: BigDecimal
- `fees`: BigDecimal
- `currency`: String
- `transactionDate`: LocalDate
- `notes`: String
- `isFractional`: Boolean
- `fractionalMultiplier`: BigDecimal
- `commissionCurrency`: String

### 2. Refactored `TransactionUpdatedEvent`
**File**: `src/main/java/com/portfolio/infrastructure/stream/dto/TransactionUpdatedEvent.java`

The event now contains two `TransactionData` objects:
```java
public record TransactionUpdatedEvent(
    TransactionData previousTransaction,
    TransactionData newTransaction
) implements TransactionEvent {}
```

### 3. Added `reverseTransaction()` to Position Model
**File**: `src/main/java/com/portfolio/domain/model/Position.java`

Added methods to properly reverse transactions including fee handling:

**`reverseBuy()`**: Undoes a BUY transaction
- Removes shares from position
- Removes invested amount (quantity × price + fees)
- **Subtracts fees from total transaction fees**

**`reverseSell()`**: Undoes a SELL transaction
- Adds shares back to position
- Adds invested amount back (proportional cost + fees)
- **Subtracts fees from total transaction fees**

**`reverseTransaction()`**: Public method that delegates to the appropriate reverse method
```java
public void reverseTransaction(String transactionType, BigDecimal quantity, BigDecimal price, BigDecimal fees)
```

**Important**: Fees are properly reversed (subtracted) rather than added again, ensuring accurate position state.

### 4. Updated `ProcessTransactionUpdatedUseCase`
**File**: `src/main/java/com/portfolio/domain/usecase/ProcessTransactionUpdatedUseCase.java`

The Command now accepts both transactions:
```java
record Command(
    TransactionData previousTransaction,
    TransactionData newTransaction,
    Instant occurredAt
)
```

Also added a nested `TransactionData` record to represent transaction data at the domain level.

### 5. Updated `ProcessTransactionUpdatedService`
**File**: `src/main/java/com/portfolio/application/usecase/transaction/ProcessTransactionUpdatedService.java`

The service now handles two scenarios:

#### Scenario A: Same Ticker Update
When the ticker remains the same (quantity, price, fees, type changes):
1. Validates that the position exists
2. Checks for out-of-order events
3. Reverses the previous transaction
4. Applies the new transaction
5. Updates the event timestamp

**Logic Flow**:
```
1. Find existing position
2. Check for out-of-order events
3. Reverse previous transaction (removes old values)
4. Apply new transaction (adds new values)
5. Update last event applied timestamp
6. Save position
```

#### Scenario B: Ticker Change (Ticker Correction)
When the ticker is changed (e.g., correcting APPL → AAPL):
1. **Removes transaction from OLD ticker's position**
2. **Adds transaction to NEW ticker's position** (creates if needed)

**Logic Flow**:
```
1. Find old position (previous ticker)
2. Check for out-of-order events on old position
3. Reverse transaction from old position
4. Update old position's timestamp
5. Save old position
6. Find or create new position (new ticker)
7. Check for out-of-order events on new position (if exists)
8. Apply transaction to new position
9. Update new position's timestamp
10. Save new position
```

**Important**: Ticker changes affect TWO positions and require atomic updates to both.

### 6. Updated `TransactionUpdatedConsumer`
**File**: `src/main/java/com/portfolio/infrastructure/stream/consumer/TransactionUpdatedConsumer.java`

The consumer now:
1. Extracts both `previousTransaction` and `newTransaction` from the event
2. Logs details of both transactions for debugging
3. Creates the command with both transaction data objects
4. Passes the command to the use case

## Event Format

### New Event Structure
```json
{
  "eventId": "7f9c4e28-5d1b-49de-8b8a-2a6c5c7e3f22",
  "occurredAt": "2025-09-24T10:20:05Z",
  "messageCreatedAt": "2025-09-24T10:20:06Z",
  "payload": {
    "previousTransaction": {
      "id": "a1b2c3d4-1111-2222-3333-444455556666",
      "ticker": "MSFT",
      "transactionType": "BUY",
      "quantity": 10.0,
      "price": 250.0,
      "fees": 2.0,
      "currency": "GBP",
      "transactionDate": "2024-08-01",
      "notes": "Original purchase",
      "isFractional": false,
      "fractionalMultiplier": 1.0,
      "commissionCurrency": "USD"
    },
    "newTransaction": {
      "id": "a1b2c3d4-1111-2222-3333-444455556666",
      "ticker": "MSFT",
      "transactionType": "BUY",
      "quantity": 15.0,
      "price": 260.0,
      "fees": 2.5,
      "currency": "GBP",
      "transactionDate": "2024-08-01",
      "notes": "Updated quantity and price",
      "isFractional": false,
      "fractionalMultiplier": 1.0,
      "commissionCurrency": "USD"
    }
  }
}
```

### Sample Event File
Updated: `redisStreamEvents/transactionUpdated.json`

## Example Scenarios

### Scenario 1: Quantity Update
**Previous**: BUY 10 shares @ $250
**New**: BUY 15 shares @ $250

**Processing**:
1. Reverse: SELL 10 shares @ $250 (removes the original transaction)
2. Apply: BUY 15 shares @ $250 (applies the new transaction)
3. **Net Effect**: +5 shares @ $250

### Scenario 2: Price Update
**Previous**: BUY 10 shares @ $250
**New**: BUY 10 shares @ $260

**Processing**:
1. Reverse: SELL 10 shares @ $250
2. Apply: BUY 10 shares @ $260
3. **Net Effect**: Shares remain same, but cost basis adjusts

### Scenario 3: Transaction Type Change
**Previous**: BUY 10 shares @ $250
**New**: SELL 10 shares @ $250

**Processing**:
1. Reverse: SELL 10 shares @ $250
2. Apply: SELL 10 shares @ $250
3. **Net Effect**: -20 shares (removes buy, applies sell)

### Scenario 4: Fee Handling (CRITICAL FIX)
**Previous**: BUY 10 shares @ $250 with $2.00 fees
**New**: BUY 10 shares @ $250 with $3.50 fees

**Position State Before Update**:
- Shares: 10
- Invested Amount: $2,502.00 (10 × $250 + $2.00)
- Total Fees: $2.00

**Processing**:
1. **Reverse BUY**:
   - Remove 10 shares
   - Remove $2,502.00 from invested amount
   - **Subtract $2.00 from total fees** (not add!)
   - State: Shares=0, Invested=$0, Fees=$0

2. **Apply BUY**:
   - Add 10 shares
   - Add $2,503.50 to invested amount (10 × $250 + $3.50)
   - Add $3.50 to total fees
   - State: Shares=10, Invested=$2,503.50, Fees=$3.50

3. **Net Effect**: Same shares, invested amount increased by $1.50, total fees increased by $1.50 (correct!)

**Without the fix**, reversing would have incorrectly added the $2.00 fees again instead of subtracting them, resulting in total fees of $5.50 instead of $3.50.

### Scenario 5: Ticker Correction (NEW FEATURE!)
**Previous**: BUY 10 shares of **APPL** @ $250 (wrong ticker!)
**New**: BUY 10 shares of **AAPL** @ $250 (corrected ticker)

**Position State Before Update**:
- **APPL Position**: Shares=10, Invested=$2,500, Fees=$2.00
- **AAPL Position**: Does not exist (or exists with other transactions)

**Processing**:
1. **Remove from APPL position**:
   - Reverse BUY: Remove 10 shares from APPL
   - APPL State: Shares=0, Invested=$0, Fees=$0

2. **Add to AAPL position**:
   - If AAPL position doesn't exist, create it
   - Apply BUY: Add 10 shares to AAPL
   - AAPL State: Shares=10, Invested=$2,500, Fees=$2.00

3. **Net Effect**: 
   - Transaction moved from wrong ticker (APPL) to correct ticker (AAPL)
   - **Two positions affected**: APPL and AAPL

**Use Cases**:
- Correcting typos in ticker symbols
- Moving transactions entered under wrong ticker
- Fixing data entry mistakes

## Validation Rules

1. ~~**Ticker Consistency**: Both previous and new transactions must have the same ticker~~ **UPDATED**: Ticker changes are now supported for correction scenarios
2. **Position Existence**: 
   - For same-ticker updates: Position must exist for the ticker
   - For ticker changes: Position must exist for the OLD ticker (new ticker position will be created if needed)
3. **Event Ordering**: Out-of-order events are ignored based on `occurredAt` timestamp (checked independently for each affected position)
4. **Transaction ID**: Both transactions should typically have the same ID (representing the same logical transaction being updated)
5. **Ticker Change Validation**: When ticker changes, the old position must exist to reverse the transaction from it

## Error Handling

### Error Codes
- `INVALID_INPUT`: Used when position not found or invalid data
- `PERSISTENCE_ERROR`: Used when database operations fail

### Error Scenarios
1. ~~**Ticker Mismatch**: Returns error if `previousTransaction.ticker != newTransaction.ticker`~~ **REMOVED**: Now handled as ticker correction feature
2. **Position Not Found (Same Ticker)**: Returns error if no position exists for the ticker being updated
3. **Old Position Not Found (Ticker Change)**: Returns error if the old ticker's position doesn't exist when attempting ticker correction
4. **Out-of-Order Event**: Returns ignored result if event is older than last applied event for the affected position(s)
5. **Persistence Failure**: Returns error with appropriate message

### Ticker Change Specific Errors
- **Old ticker position missing**: Cannot reverse transaction if the old position doesn't exist
- **Out-of-order on old position**: Event ignored if older than last applied event on old ticker
- **Out-of-order on new position**: Event ignored if older than last applied event on new ticker (if position already exists)

## Benefits

1. **Accuracy**: Calculates exact difference between transactions
2. **Flexibility**: Supports any type of update (quantity, price, type, fees, **and ticker**)
3. **Auditability**: Both previous and new states are logged
4. **Consistency**: Ensures position state is always correct regardless of update type
5. **Idempotency**: Out-of-order events are properly handled
6. **Correct Fee Reversal**: Fees are properly subtracted (not added) when reversing transactions, maintaining accurate fee totals
7. **Ticker Correction**: Supports correcting ticker entry mistakes by moving transactions between positions

## Testing Considerations

When testing this refactoring, verify:

### Same Ticker Updates
1. Quantity changes update shares owned correctly
2. Price changes update average cost per share correctly
3. Transaction type changes work properly (BUY to SELL, SELL to BUY)
4. **Fees are properly reversed**: When reversing a transaction, fees should be SUBTRACTED from total fees, not added
5. **Fee updates work correctly**: When updating only fees, the net change should reflect the difference (new fees - old fees)
6. Invested amount calculations include fees correctly in both apply and reverse operations
7. Out-of-order events are properly ignored
8. Position not found validation works for same ticker updates

### Ticker Change Updates
9. **Ticker correction works**: Transaction is removed from old ticker and added to new ticker
10. **Old position is updated correctly**: Transaction is reversed from old ticker's position
11. **New position is created if needed**: If new ticker has no position, one is created
12. **New position is updated if exists**: If new ticker already has a position, transaction is added to it
13. **Two positions are saved atomically**: Both old and new positions are updated in the same transaction
14. **Out-of-order events checked for both positions**: Events are validated against both old and new position timestamps
15. **Old position not found error**: Returns appropriate error if old ticker position doesn't exist
16. **Fees handled correctly in ticker changes**: Fees are removed from old position and added to new position

