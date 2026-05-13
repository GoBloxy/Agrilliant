-- Agrilliant — Smart Farm Management System
-- Database Schema v2.0
-- Run this script in MySQL to create the database and all tables.

CREATE DATABASE IF NOT EXISTS agrilliant;
USE agrilliant;

-- ═══════════════ ADMIN ═══════════════
CREATE TABLE admin (
    admin_id      INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ═══════════════ MANAGER ═══════════════
CREATE TABLE manager (
    manager_id    INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ═══════════════ WORKER ═══════════════
-- Workers have NO login credentials; managed by a manager.
CREATE TABLE worker (
    worker_id      INT AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    job_title      VARCHAR(50),
    skills         TEXT,
    on_duty        BOOLEAN      NOT NULL DEFAULT TRUE,
    fingerprint_id INT          NULL,
    manager_id     INT          NOT NULL,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (manager_id) REFERENCES manager(manager_id) ON DELETE CASCADE
);

-- ═══════════════ PLOTS ═══════════════
CREATE TABLE plots (
    plot_id    INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(255),
    size_acres DOUBLE       NOT NULL DEFAULT 0,
    soil_type  VARCHAR(50),
    manager_id INT          NOT NULL,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (manager_id) REFERENCES manager(manager_id) ON DELETE CASCADE
);

-- ═══════════════ DEVICES (ESP32 sensors) ═══════════════
CREATE TABLE devices (
    device_id        INT AUTO_INCREMENT PRIMARY KEY,
    device_code      VARCHAR(50)  NOT NULL UNIQUE,
    type             ENUM('TEMP_HUM','SOIL_MOISTURE','LIGHT','WEATHER_STATION') NOT NULL DEFAULT 'TEMP_HUM',
    status           ENUM('ONLINE','OFFLINE','MAINTENANCE') NOT NULL DEFAULT 'OFFLINE',
    plot_id          INT          NOT NULL,
    firmware_version VARCHAR(20),
    last_seen_at     TIMESTAMP    NULL,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
);

-- ═══════════════ CROPS ═══════════════
CREATE TABLE crops (
    crop_id        INT AUTO_INCREMENT PRIMARY KEY,
    crop_name      VARCHAR(100) NOT NULL,
    planting_date  DATE,
    harvest_date   DATE,
    growth_stage   ENUM('SEED','SEEDLING','VEGETATIVE','FLOWERING','FRUITING','HARVESTED') NOT NULL DEFAULT 'SEED',
    plot_id        INT          NOT NULL,
    expected_yield DOUBLE       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
);

-- ═══════════════ SENSOR READINGS ═══════════════
CREATE TABLE sensor_readings (
    reading_id    INT AUTO_INCREMENT PRIMARY KEY,
    device_id     INT          NOT NULL,
    temperature   FLOAT,
    humidity      FLOAT,
    soil_moisture FLOAT,
    timestamp     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
);

-- ═══════════════ ALERTS ═══════════════
CREATE TABLE alerts (
    alert_id   INT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50)  NOT NULL,
    severity   ENUM('CRITICAL','WARNING','INFO') NOT NULL DEFAULT 'INFO',
    message    TEXT         NOT NULL,
    plot_id    INT          NOT NULL,
    resolved   BOOLEAN      NOT NULL DEFAULT FALSE,
    timestamp  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
);

-- ═══════════════ TASKS ═══════════════
CREATE TABLE tasks (
    task_id            INT AUTO_INCREMENT PRIMARY KEY,
    description        TEXT         NOT NULL,
    status             ENUM('PENDING','IN_PROGRESS','DONE') NOT NULL DEFAULT 'PENDING',
    due_date           DATE,
    plot_id            INT          NOT NULL,
    alert_id           INT          NULL,
    assigned_by_mgr_id INT          NOT NULL,
    alert_type         VARCHAR(50),
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (plot_id)            REFERENCES plots(plot_id)   ON DELETE CASCADE,
    FOREIGN KEY (alert_id)           REFERENCES alerts(alert_id) ON DELETE SET NULL,
    FOREIGN KEY (assigned_by_mgr_id) REFERENCES manager(manager_id) ON DELETE CASCADE
);

-- ═══════════════ WORKER ↔ TASK (M:N) ═══════════════
CREATE TABLE worker_task (
    worker_id         INT NOT NULL,
    task_id           INT NOT NULL,
    assigned_by_mgr_id INT NOT NULL,
    assigned_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (worker_id, task_id),
    FOREIGN KEY (worker_id)         REFERENCES worker(worker_id)   ON DELETE CASCADE,
    FOREIGN KEY (task_id)           REFERENCES tasks(task_id)      ON DELETE CASCADE,
    FOREIGN KEY (assigned_by_mgr_id) REFERENCES manager(manager_id) ON DELETE CASCADE
);

-- ═══════════════ HARVEST RECORDS ═══════════════
CREATE TABLE harvest_records (
    record_id    INT AUTO_INCREMENT PRIMARY KEY,
    harvest_date DATE    NOT NULL,
    quantity_kg  DOUBLE  NOT NULL DEFAULT 0,
    grade        ENUM('A','B','C','REJECT') NOT NULL DEFAULT 'B',
    crop_id      INT     NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (crop_id) REFERENCES crops(crop_id) ON DELETE CASCADE
);

-- ═══════════════ ATTENDANCE (R307 Fingerprint) ═══════════════
CREATE TABLE attendance (
    attendance_id INT AUTO_INCREMENT PRIMARY KEY,
    worker_id     INT          NOT NULL,
    check_in      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    check_out     TIMESTAMP    NULL,
    device_code   VARCHAR(50),
    FOREIGN KEY (worker_id) REFERENCES worker(worker_id) ON DELETE CASCADE
);
