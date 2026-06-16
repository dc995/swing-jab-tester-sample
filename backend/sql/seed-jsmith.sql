-- ============================================
-- FinanApp Seed Data - Profile: JSMITH
-- James Smith - Aggressive Growth Portfolio
-- Run: sqlcmd -S localhost -E -d finanapp -i sql/seed-jsmith.sql
-- ============================================

USE finanapp;
GO

-- Clear existing data for this profile
DELETE FROM dbo.orders WHERE profile = 'JSMITH';
DELETE FROM dbo.holdings WHERE profile = 'JSMITH';
GO

-- Holdings: aggressive tech-heavy portfolio
INSERT INTO dbo.holdings (profile, symbol, quantity, avg_price, updated_at) VALUES
('JSMITH', 'NVDA',  500,  138.4200, GETDATE()),
('JSMITH', 'AAPL',  800,  192.7500, GETDATE()),
('JSMITH', 'MSFT',  350,  415.2000, GETDATE()),
('JSMITH', 'TSLA',  400,  248.6000, GETDATE()),
('JSMITH', 'AMZN',  250,  201.3300, GETDATE()),
('JSMITH', 'META',  300,  585.2500, GETDATE());
GO

-- Order history showing the build-up
INSERT INTO dbo.orders (profile, symbol, order_type, quantity, price, created_at) VALUES
('JSMITH', 'NVDA',  'BUY',  300, 125.5000, '2025-09-15 09:31:22'),
('JSMITH', 'AAPL',  'BUY',  500, 185.0000, '2025-09-22 10:05:11'),
('JSMITH', 'MSFT',  'BUY',  200, 398.5000, '2025-10-01 09:45:33'),
('JSMITH', 'TSLA',  'BUY',  600, 222.0000, '2025-10-10 11:12:44'),
('JSMITH', 'AMZN',  'BUY',  250, 201.3300, '2025-10-18 14:22:00'),
('JSMITH', 'NVDA',  'BUY',  200, 157.8000, '2025-11-05 09:30:15'),
('JSMITH', 'AAPL',  'BUY',  300, 205.6000, '2025-11-12 10:44:21'),
('JSMITH', 'MSFT',  'BUY',  150, 437.8000, '2025-12-02 09:33:50'),
('JSMITH', 'TSLA',  'SELL', 200, 289.4000, '2025-12-15 15:01:33'),
('JSMITH', 'META',  'BUY',  300, 585.2500, '2026-01-08 09:30:05'),
('JSMITH', 'NVDA',  'SELL', 100, 162.0000, '2026-02-10 13:45:22'),
('JSMITH', 'AAPL',  'SELL', 100, 227.0000, '2026-03-01 10:15:00');
GO

PRINT 'JSMITH profile seeded: 6 holdings, 12 orders';
GO
