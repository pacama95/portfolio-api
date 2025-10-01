-- Portfolio Database Schema
-- This script initializes the database schema for the portfolio management system

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create enum types
CREATE TYPE currency_type AS ENUM ('USD', 'EUR', 'GBP');

-- Create positions table (independent position data)
CREATE TABLE positions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticker VARCHAR(20) UNIQUE NOT NULL,
    shares_owned DECIMAL(18, 6) NOT NULL DEFAULT 0.00,
    average_cost_per_share DECIMAL(18, 4) NOT NULL DEFAULT 0.00,
    currency currency_type NOT NULL DEFAULT 'USD',
    total_invested_amount DECIMAL(18, 4) NOT NULL DEFAULT 0.00,
    total_transaction_fees DECIMAL(18, 4) NOT NULL DEFAULT 0.00,
    first_purchase_date DATE NOT NULL,
    unrealized_gain_loss DECIMAL(18, 4) DEFAULT 0.00,
    total_market_value DECIMAL(18, 4) DEFAULT 0.00,
    latest_market_price DECIMAL(18, 4) DEFAULT 0.00,
    market_price_last_updated TIMESTAMP WITH TIME ZONE,
    last_event_applied_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_positions_ticker ON positions(ticker);
CREATE INDEX idx_positions_shares_owned ON positions(shares_owned);

-- Create a trigger to update the updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_positions_updated_at BEFORE UPDATE ON positions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create a view for portfolio summary
CREATE VIEW portfolio_summary AS
SELECT 
    COUNT(*) as total_positions,
    SUM(total_market_value) as total_market_value,
    SUM(total_invested_amount) as total_invested_amount,
    SUM(unrealized_gain_loss) as total_unrealized_gain_loss,
    CASE 
        WHEN SUM(total_invested_amount) > 0 
        THEN (SUM(unrealized_gain_loss) / SUM(total_invested_amount)) * 100
        ELSE 0
    END as total_return_percentage
FROM positions 
WHERE shares_owned > 0;

-- Create a view for position details with calculations
CREATE VIEW position_details AS
SELECT 
    p.*,
    (p.shares_owned * p.latest_market_price) as calculated_market_value,
    (p.shares_owned * p.latest_market_price) - p.total_invested_amount as calculated_unrealized_pnl,
    CASE 
        WHEN p.total_invested_amount > 0 
        THEN (((p.shares_owned * p.latest_market_price) - p.total_invested_amount) / p.total_invested_amount) * 100
        ELSE 0 
    END as calculated_return_percentage
FROM positions p
WHERE p.shares_owned > 0; 