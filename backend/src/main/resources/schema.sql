-- ============================================
-- FinanApp Stock Trader - Database Schema
-- Server: localhost
-- Database: finanapp
-- Auth: Windows Integrated Security
-- ============================================

USE master;
GO

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'finanapp')
BEGIN
    CREATE DATABASE finanapp;
END
GO

USE finanapp;
GO

-- Drop and recreate for clean state
IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.audit_log') AND type = 'U')
    DROP TABLE dbo.audit_log;
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.orders') AND type = 'U')
    DROP TABLE dbo.orders;
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.holdings') AND type = 'U')
    DROP TABLE dbo.holdings;
GO

-- Orders table: records every buy/sell transaction
CREATE TABLE dbo.orders (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    profile     VARCHAR(20)     NOT NULL DEFAULT 'DEFAULT',
    symbol      VARCHAR(10)     NOT NULL,
    order_type  VARCHAR(4)      NOT NULL CHECK (order_type IN ('BUY', 'SELL')),
    quantity    INT             NOT NULL CHECK (quantity > 0),
    price       DECIMAL(18, 4)  NOT NULL CHECK (price > 0),
    created_at  DATETIME2       NOT NULL DEFAULT GETDATE(),
    status      VARCHAR(20)     NULL DEFAULT 'FILLED',
    notes       VARCHAR(500)    NULL
);
CREATE NONCLUSTERED INDEX IX_orders_symbol ON dbo.orders (symbol);
CREATE NONCLUSTERED INDEX IX_orders_profile ON dbo.orders (profile);
GO

-- Holdings table: current portfolio positions (one row per profile+symbol)
CREATE TABLE dbo.holdings (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    profile     VARCHAR(20)     NOT NULL DEFAULT 'DEFAULT',
    symbol      VARCHAR(10)     NOT NULL,
    quantity    INT             NOT NULL CHECK (quantity >= 0),
    avg_price   DECIMAL(18, 4)  NOT NULL,
    updated_at  DATETIME2       NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UQ_holdings_profile_symbol UNIQUE (profile, symbol)
);
GO

-- Audit log table: records all significant actions for compliance/audit trail
CREATE TABLE dbo.audit_log (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    profile     VARCHAR(20)     NOT NULL,
    action      VARCHAR(20)     NOT NULL,
    entity_type VARCHAR(50)     NOT NULL,
    entity_id   BIGINT          NULL,
    details     VARCHAR(500)    NULL,
    created_at  DATETIME2       NOT NULL DEFAULT GETDATE()
);
CREATE NONCLUSTERED INDEX IX_audit_log_profile ON dbo.audit_log (profile);
GO
