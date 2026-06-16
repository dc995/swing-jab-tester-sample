-- ============================================
-- FinanApp Seed Data - Profile: MWILSON
-- Maria Wilson - Conservative Value Portfolio
-- Run: sqlcmd -S localhost -E -d finanapp -i sql/seed-mwilson.sql
-- ============================================

USE finanapp;
GO

-- Clear existing data for this profile
DELETE FROM dbo.orders WHERE profile = 'MWILSON';
DELETE FROM dbo.holdings WHERE profile = 'MWILSON';
GO

-- Holdings: conservative blue-chip + dividend portfolio
INSERT INTO dbo.holdings (profile, symbol, quantity, avg_price, updated_at) VALUES
('MWILSON', 'JNJ',   1200, 156.8000, GETDATE()),
('MWILSON', 'JPM',   600,  242.5000, GETDATE()),
('MWILSON', 'PG',    800,  168.3000, GETDATE()),
('MWILSON', 'KO',    1500, 62.4500,  GETDATE()),
('MWILSON', 'BRK.B', 200,  462.1000, GETDATE()),
('MWILSON', 'GOOGL', 150,  174.6500, GETDATE()),
('MWILSON', 'V',     400,  312.7000, GETDATE()),
('MWILSON', 'XOM',   700,  108.2000, GETDATE());
GO

-- Order history showing steady accumulation
INSERT INTO dbo.orders (profile, symbol, order_type, quantity, price, created_at) VALUES
('MWILSON', 'JNJ',   'BUY',  500,  148.2000, '2025-08-10 09:30:15'),
('MWILSON', 'JPM',   'BUY',  300,  228.0000, '2025-08-20 10:12:33'),
('MWILSON', 'PG',    'BUY',  400,  162.5000, '2025-09-05 09:45:00'),
('MWILSON', 'KO',    'BUY',  1000, 60.2000,  '2025-09-12 11:00:44'),
('MWILSON', 'BRK.B', 'BUY',  100,  445.0000, '2025-09-25 09:31:22'),
('MWILSON', 'JNJ',   'BUY',  700,  162.6000, '2025-10-15 10:22:11'),
('MWILSON', 'JPM',   'BUY',  300,  257.0000, '2025-10-28 14:05:33'),
('MWILSON', 'GOOGL', 'BUY',  150,  174.6500, '2025-11-10 09:30:55'),
('MWILSON', 'PG',    'BUY',  400,  174.1000, '2025-11-22 10:45:12'),
('MWILSON', 'KO',    'BUY',  500,  66.9500,  '2025-12-05 09:30:00'),
('MWILSON', 'V',     'BUY',  400,  312.7000, '2026-01-15 09:32:44'),
('MWILSON', 'BRK.B', 'BUY',  100,  479.2000, '2026-01-28 10:15:33'),
('MWILSON', 'XOM',   'BUY',  700,  108.2000, '2026-02-12 09:30:22');
GO

PRINT 'MWILSON profile seeded: 8 holdings, 13 orders';
GO
