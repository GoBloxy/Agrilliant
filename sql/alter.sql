-- ═══════════════════════════════════════════════════════════════════
-- Agrilliant — Schema Migration
-- From: original currentschema.sql  →  To: schema.sql (updated)
-- Run this against the live agrilliant database.
--
-- Changes summary:
--   1. devices.type  — remove LIGHT (only DHT11 & FC-28 exist)
--   2. schema.sql doc-only fix: removed phantom created_at/updated_at
--      from crops table definition (columns never existed in live DB,
--      so NO ALTER needed for that)
-- ═══════════════════════════════════════════════════════════════════

USE agrilliant;

-- ───────────────────────────────────────────────────────────────
-- 1. DEVICES: Remove LIGHT from type ENUM
--    Hardware: ESP32 + DHT11 (TEMP_HUM) + FC-28 (SOIL) only
-- ───────────────────────────────────────────────────────────────

-- Safety check: ensure no rows reference LIGHT before altering
SELECT COUNT(*) AS light_device_count FROM devices WHERE type = 'LIGHT';
-- ↑ Must return 0. If not, UPDATE or DELETE those rows first.

ALTER TABLE `devices`
  MODIFY COLUMN `type` ENUM('TEMP_HUM','SOIL') NOT NULL;

-- ───────────────────────────────────────────────────────────────
-- 2. PLOTS: Add irrigation_type column
--    Tracks the irrigation method used for each plot
-- ───────────────────────────────────────────────────────────────

ALTER TABLE `plots`
  ADD COLUMN `irrigation_type` VARCHAR(50) DEFAULT NULL AFTER `soil_type`;

-- ───────────────────────────────────────────────────────────────
-- 3. PLOTS: Add status column
--    Tracks the plot status (Under Cultivation, Available, Fallow)
-- ───────────────────────────────────────────────────────────────

ALTER TABLE `plots`
  ADD COLUMN `status` VARCHAR(50) DEFAULT NULL AFTER `irrigation_type`;
