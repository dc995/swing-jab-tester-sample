-- ============================================
-- FinanApp - Reset and Seed All Profiles
-- Run: sqlcmd -S localhost -E -i sql/reset-all.sql
-- ============================================

:r schema.sql
:r sql\seed-jsmith.sql
:r sql\seed-mwilson.sql

PRINT '=== All profiles reset and seeded ===';
GO
