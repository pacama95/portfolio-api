-- Create table to store transaction IDs linked to positions
-- Ensures unique transaction_id and index on (position_id, transaction_id)

CREATE TABLE IF NOT EXISTS position_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    position_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_position_transactions_position FOREIGN KEY (position_id)
        REFERENCES positions(id) ON DELETE CASCADE,
    CONSTRAINT uk_position_transactions_transaction_id UNIQUE (transaction_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_position_transactions_position_id ON position_transactions(position_id);
CREATE INDEX IF NOT EXISTS idx_position_transactions_composite ON position_transactions(position_id, transaction_id);

-- Trigger to maintain updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_transactions_updated_at ON position_transactions;
CREATE TRIGGER update_transactions_updated_at BEFORE UPDATE ON position_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();