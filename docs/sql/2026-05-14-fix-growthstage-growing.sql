-- ============================================================================
-- Phase 1 workaround: Crop.GrowthStage 'GROWING' → 'VEGETATIVE'
-- ============================================================================
--
-- Problem
-- -------
-- The crops table contains rows with growth_stage = 'GROWING', but the Java
-- enum smartfarm.model.Crop.GrowthStage only defines:
--   { SEED, SEEDLING, VEGETATIVE, FLOWERING, FRUITING, HARVESTED }
-- so CropDAO.extractCrop() throws IllegalArgumentException on Enum.valueOf()
-- and dashboard.fxml + reports.fxml fail to load.
--
-- Three fix options were considered (per docs/STATUS.md "Known Errors" #1):
--   (a) Add 'GROWING' to Crop.GrowthStage     — model/, frozen for Phase 1
--   (b) UPDATE the DB rows                     — this script
--   (c) Make CropDAO.extractCrop() handle      — dao/, Hagag's lane
--       unknown enum values gracefully
--
-- Phase 1 chose (b): the DB-side fix is the smallest, fastest unblock and
-- doesn't perturb the model freeze or the DAO layer. 'VEGETATIVE' is the
-- semantically closest substitute — it's the general "actively growing,
-- pre-flowering" phase in the agronomy literature, which matches the
-- everyday meaning of "growing".
--
-- How to run
-- ----------
-- Apply once against the project's MySQL instance (typically the dev/staging
-- host configured via DB_URL / db.properties — see Hagag's H4 DBConnection
-- for credential layering):
--
--   mysql -h <host> -u <user> -p <database> < docs/sql/2026-05-14-fix-growthstage-growing.sql
--
-- Or paste the body into your client of choice.
--
-- The script is idempotent: re-running it on a clean DB is a zero-row UPDATE.
--
-- Verification
-- ------------
-- After applying, this query should return 0:
--
--   SELECT COUNT(*) FROM crops WHERE growth_stage = 'GROWING';
--
-- And dashboard.fxml + reports.fxml should load cleanly under the next
-- desktop run (mvn -Pdesktop javafx:run).
-- ============================================================================

-- Snapshot the count before the change so the migration log shows the impact.
SELECT COUNT(*) AS rows_to_update
FROM   crops
WHERE  growth_stage = 'GROWING';

-- Apply the fix.
UPDATE crops
SET    growth_stage = 'VEGETATIVE'
WHERE  growth_stage = 'GROWING';

-- Verify post-state.
SELECT COUNT(*) AS rows_still_growing
FROM   crops
WHERE  growth_stage = 'GROWING';
-- ↑ Expected: 0
