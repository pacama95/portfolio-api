-- Add exchange and country columns to positions table
-- These fields store the exchange and country information for each position

ALTER TABLE positions ADD COLUMN exchange VARCHAR(100);
ALTER TABLE positions ADD COLUMN country VARCHAR(100);

-- Create indexes for better performance
CREATE INDEX idx_positions_exchange ON positions(exchange);
CREATE INDEX idx_positions_country ON positions(country);

COMMENT ON COLUMN positions.exchange IS 'The stock exchange where the position is traded (e.g., NASDAQ, NYSE)';
COMMENT ON COLUMN positions.country IS 'The country of the exchange';

