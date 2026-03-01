-- 5.6: Remove REPLICA IDENTITY FULL from high-write tables
-- FULL causes the entire row to be written to WAL on every UPDATE/DELETE,
-- which bloats WAL for tables with frequent writes (messages, typing_indicators).
-- DEFAULT only writes the PK, which is sufficient since clients re-fetch full rows.

ALTER TABLE messages REPLICA IDENTITY DEFAULT;
ALTER TABLE typing_indicators REPLICA IDENTITY DEFAULT;
