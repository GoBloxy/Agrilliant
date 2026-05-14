-- ═══════════════════════════════════════════════════════════════════════════
--  Agrilliant — Mock Seed Data
--  Tables: manager · worker · plots · crops · tasks · worker_task
--
--  ⚠  All password hashes = bcrypt("Admin@123") — change before production.
--  ⚠  Run inside MySQL after schema.sql has been applied.
--  ⚠  Uses INSERT IGNORE — safe to re-run; won't duplicate existing rows.
--  ⚠  If your manager table already has rows, adjust manager_id references
--     in plots/tasks to match your existing IDs.
-- ═══════════════════════════════════════════════════════════════════════════

USE agrilliant;

-- ───────────────────────────────────────────────────────────────────────────
--  MANAGERS  (prerequisite for plots and tasks)
-- ───────────────────────────────────────────────────────────────────────────
-- Password plain-text: Admin@123
INSERT IGNORE INTO manager (manager_id, full_name, username, email, password_hash, phone) VALUES
(1, 'Ahmed Hassan',    'ahmed.hassan',  'ahmed@agrilliant.farm',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '+201012345678'),
(2, 'Sara Al-Rashid',  'sara.rashid',   'sara@agrilliant.farm',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '+201098765432');

-- ───────────────────────────────────────────────────────────────────────────
--  WORKERS  (prerequisite for worker_task)
-- ───────────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO worker (worker_id, full_name, phone, email, password_hash, job_title, skills, on_duty, manager_id) VALUES
(1, 'Khalid Ibrahim',  '+201111111111', 'khalid@agrilliant.farm',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Field Technician',  'Irrigation, Soil Testing',        1, 1),
(2, 'Nour Elsayed',    '+201222222222', 'nour@agrilliant.farm',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Crop Specialist',   'Pest Control, Harvesting',        1, 1),
(3, 'Hassan Farouk',   '+201333333333', 'hassan@agrilliant.farm',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Equipment Tech',    'ESP32 Sensors, Maintenance',      0, 1),
(4, 'Mariam Tawfik',   '+201444444444', 'mariam@agrilliant.farm',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Irrigation Lead',   'Drip Systems, Water Management',  1, 2),
(5, 'Omar Shalaby',    '+201555555555', 'omar@agrilliant.farm',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Harvest Operator',  'Combine Harvesting, Grading',     0, 2);

-- ───────────────────────────────────────────────────────────────────────────
--  PLOTS  (5 distinct farm sections)
-- ───────────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO plots (plot_id, name, location, size_acres, soil_type, manager_id) VALUES
(1, 'North Field A',    'North Sector – Block 1',  12.5,  'Sandy Loam',  1),
(2, 'South Field B',    'South Sector – Block 2',   9.3,  'Clay Loam',   1),
(3, 'East Greenhouse',  'East Wing – Block 3',       2.0,  'Loamy Sand',  1),
(4, 'West Field C',     'West Sector – Block 4',   15.8,  'Silty Loam',  2),
(5, 'Central Orchard',  'Central Zone – Block 5',   6.4,  'Loam',        2);

-- ───────────────────────────────────────────────────────────────────────────
--  CROPS  (10 crops across all 5 plots, mixed growth stages)
-- ───────────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO crops (crop_id, crop_name, planting_date, harvest_date, growth_stage, expected_yield, plot_id) VALUES
-- North Field A — row crops
(1,  'Tomatoes',      '2025-02-10', '2025-05-30', 'GROWING',   8500.0,  1),
(2,  'Bell Peppers',  '2025-03-01', '2025-06-15', 'PLANTED',   4200.0,  1),

-- South Field B — cereal / oilseed
(3,  'Wheat',         '2024-11-15', '2025-04-25', 'READY',    18000.0,  2),
(4,  'Sunflowers',    '2025-01-20', '2025-07-05', 'GROWING',   6700.0,  2),

-- East Greenhouse — high-value short-cycle
(5,  'Lettuce',       '2025-03-20', '2025-05-05', 'GROWING',   1200.0,  3),
(6,  'Strawberries',  '2025-01-10', '2025-04-30', 'READY',     2800.0,  3),

-- West Field C — broad-acre
(7,  'Maize',         '2025-02-25', '2025-07-20', 'PLANTED',  22000.0,  4),
(8,  'Soybeans',      '2025-03-10', '2025-08-20', 'PLANTED',  11500.0,  4),

-- Central Orchard — perennial trees
(9,  'Mango',         '2024-09-01', '2025-08-01', 'GROWING',   9500.0,  5),
(10, 'Guava',         '2024-10-15', '2025-07-20', 'GROWING',   5300.0,  5);

-- ───────────────────────────────────────────────────────────────────────────
--  TASKS  (15 tasks, mix of statuses, spread across all plots)
--  IDs are explicit so worker_task references below are stable.
-- ───────────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO tasks (task_id, description, status, due_date, plot_id, alert_id, assigned_by_mgr_id, alert_type) VALUES

-- North Field A (plot 1)
(1,  'Calibrate DHT11 sensor — readings spiking above 45°C, verify wiring and firmware',
     'PENDING',     '2026-05-18', 1, NULL, 1, 'SENSOR_CHECK'),

(2,  'Apply NPK fertilizer to tomato rows (ratio 20-10-10) — use 3 kg per 10 m row',
     'IN_PROGRESS', '2026-05-16', 1, NULL, 1, 'FERTILIZATION'),

(3,  'Prune and stake tomato plants that have grown beyond the support frame height',
     'IN_PROGRESS', '2026-05-14', 1, NULL, 1, 'CROP_MGMT'),

(4,  'Manual weeding along pepper plot perimeter — chemical-free zone, hand tools only',
     'DONE',        '2026-05-09', 1, NULL, 1, 'WEEDING'),

-- South Field B (plot 2)
(5,  'Inspect wheat for powdery mildew — record field coverage %, flag any hot spots',
     'IN_PROGRESS', '2026-05-15', 2, NULL, 1, 'PEST_INSPECTION'),

(6,  'Soil moisture calibration for FC-28 probe on South Field B — compare to manual reading',
     'DONE',        '2026-05-10', 2, NULL, 1, 'SENSOR_CHECK'),

(7,  'Prepare wheat harvest schedule — estimate total yield, coordinate combine harvester',
     'PENDING',     '2026-05-22', 2, NULL, 1, 'HARVEST'),

-- East Greenhouse (plot 3)
(8,  'Harvest mature strawberries — target 280 kg batch, grade A/B/C and move to cold room',
     'PENDING',     '2026-05-19', 3, NULL, 1, 'HARVEST'),

(9,  'Replace FC-28 soil probe on Greenhouse rack 2 — cable sheath is cracked, reading 0%',
     'IN_PROGRESS', '2026-05-15', 3, NULL, 1, 'MAINTENANCE'),

(10, 'Transplant lettuce seedlings from seedling tray to second greenhouse rack (capacity 80 pots)',
     'DONE',        '2026-05-08', 3, NULL, 1, 'CROP_MGMT'),

(11, 'Install shade netting over greenhouse rack 3 — reduce heat stress after midday',
     'PENDING',     '2026-05-20', 3, NULL, 1, 'MAINTENANCE'),

-- West Field C (plot 4)
(12, 'Clear blocked drip irrigation lines on rows 4–9 — flush and check emitter flow rate',
     'PENDING',     '2026-05-17', 4, NULL, 2, 'IRRIGATION'),

(13, 'Thin maize seedlings — target 30 cm plant spacing, remove weakest in each cluster',
     'PENDING',     '2026-05-22', 4, NULL, 2, 'CROP_MGMT'),

-- Central Orchard (plot 5)
(14, 'Apply copper-based fungicide spray to mango trees — check anthracnose on new leaves',
     'PENDING',     '2026-05-21', 5, NULL, 2, 'PEST_INSPECTION'),

(15, 'Apply organic compost to guava tree beds — 5 kg per tree, work into top 15 cm of soil',
     'PENDING',     '2026-05-23', 5, NULL, 2, 'FERTILIZATION');

-- ───────────────────────────────────────────────────────────────────────────
--  WORKER ↔ TASK  (assigns each task to one or more workers)
-- ───────────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO worker_task (worker_id, task_id, assigned_by_mgr_id) VALUES
-- task 1  – sensor calibration → Hassan (Equipment Tech)
(3,  1,  1),
-- task 2  – fertilizer → Nour (Crop Specialist)
(2,  2,  1),
-- task 3  – tomato pruning → Nour
(2,  3,  1),
-- task 4  – weeding → Khalid + Nour (two-person job)
(1,  4,  1),
(2,  4,  1),
-- task 5  – wheat pest inspection → Nour
(2,  5,  1),
-- task 6  – soil sensor calibration → Hassan
(3,  6,  1),
-- task 7  – harvest scheduling → Khalid + Omar
(1,  7,  1),
(5,  7,  1),
-- task 8  – strawberry harvest → Omar (Harvest Operator)
(5,  8,  1),
-- task 9  – replace FC-28 probe → Hassan
(3,  9,  1),
-- task 10 – lettuce transplant → Nour
(2,  10, 1),
-- task 11 – shade netting → Khalid
(1,  11, 1),
-- task 12 – drip line clearing → Mariam (Irrigation Lead)
(4,  12, 2),
-- task 13 – maize thinning → Khalid
(1,  13, 2),
-- task 14 – mango fungicide → Mariam + Nour
(4,  14, 2),
(2,  14, 2),
-- task 15 – guava compost → Mariam
(4,  15, 2);

-- ───────────────────────────────────────────────────────────────────────────
--  HARVEST RECORDS  (past harvests to populate the Reports chart)
-- ───────────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO harvest_records (record_id, harvest_date, quantity_kg, grade, crop_id) VALUES
-- 2024 wheat cycle
(1,  '2024-04-22', 17200.0, 'A', 3),
-- 2024 sunflower seed
(2,  '2024-07-10',  6100.0, 'B', 4),
-- 2024 mango (partial early harvest)
(3,  '2024-08-05',  4800.0, 'A', 9),
-- 2024 guava
(4,  '2024-07-18',  3900.0, 'B', 10),
-- Greenhouse strawberries — rolling harvests
(5,  '2025-02-14',   920.0, 'A', 6),
(6,  '2025-03-07',   740.0, 'A', 6),
(7,  '2025-04-01',   610.0, 'B', 6),
-- Lettuce (fast cycle)
(8,  '2025-02-28',   480.0, 'A', 5),
-- Tomatoes (first pick, partial)
(9,  '2025-04-15',  2100.0, 'A', 1);

-- ═══════════════════════════════════════════════════════════════════════════
--  Quick verification queries (run after insert to confirm row counts)
-- ═══════════════════════════════════════════════════════════════════════════
-- SELECT 'manager'        AS tbl, COUNT(*) AS rows FROM manager        UNION ALL
-- SELECT 'worker',              COUNT(*) FROM worker                   UNION ALL
-- SELECT 'plots',               COUNT(*) FROM plots                    UNION ALL
-- SELECT 'crops',               COUNT(*) FROM crops                    UNION ALL
-- SELECT 'tasks',               COUNT(*) FROM tasks                    UNION ALL
-- SELECT 'worker_task',         COUNT(*) FROM worker_task              UNION ALL
-- SELECT 'harvest_records',     COUNT(*) FROM harvest_records;
