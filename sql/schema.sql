-- Agrilliant — Smart Farm Management System
-- Database Schema v3.0  (synced with production DB)
-- Run this script in MySQL to create the database and all tables.

CREATE DATABASE IF NOT EXISTS agrilliant;
USE agrilliant;

-- ═══════════════ ADMIN ═══════════════
CREATE TABLE admin (
    admin_id      INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ═══════════════ MANAGER ═══════════════
CREATE TABLE manager (
    manager_id    INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ═══════════════ WORKER ═══════════════
CREATE TABLE worker (
    worker_id      INT AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    email          VARCHAR(100),
    password_hash  VARCHAR(255),
    job_title      VARCHAR(100),
    skills         VARCHAR(255),
    on_duty        TINYINT(1)   NOT NULL DEFAULT 0,
    fingerprint_id INT          NULL,
    manager_id     INT          NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_worker_manager FOREIGN KEY (manager_id) REFERENCES manager(manager_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ PLOTS ═══════════════
CREATE TABLE plots (
    plot_id    INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(255),
    size_acres DOUBLE,
    soil_type  VARCHAR(100),
    manager_id INT          NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_plots_manager FOREIGN KEY (manager_id) REFERENCES manager(manager_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ DEVICES (ESP32 sensors) ═══════════════
CREATE TABLE devices (
    device_id        INT AUTO_INCREMENT PRIMARY KEY,
    device_code      VARCHAR(100) NOT NULL UNIQUE,
    type             ENUM('TEMP_HUM','SOIL') NOT NULL,
    status           ENUM('ONLINE','OFFLINE','MAINT') NOT NULL DEFAULT 'OFFLINE',
    plot_id          INT          NOT NULL,
    firmware_version VARCHAR(50),
    last_seen_at     DATETIME,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_devices_plot FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ CROPS ═══════════════
CREATE TABLE crops (
    crop_id        INT AUTO_INCREMENT PRIMARY KEY,
    crop_name      VARCHAR(100) NOT NULL,
    planting_date  DATE,
    harvest_date   DATE,
    growth_stage   ENUM('PLANTED','GROWING','READY','HARVESTED') NOT NULL DEFAULT 'PLANTED',
    expected_yield DOUBLE,
    plot_id        INT          NOT NULL,
    CONSTRAINT fk_crops_plot FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ SENSOR READINGS ═══════════════
CREATE TABLE sensor_readings (
    reading_id    INT AUTO_INCREMENT PRIMARY KEY,
    device_id     INT          NOT NULL,
    temperature   FLOAT,
    humidity      FLOAT,
    soil_moisture FLOAT,
    timestamp     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_readings_device FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE ON UPDATE CASCADE
);

-- ═══════════════ ALERTS ═══════════════
CREATE TABLE alerts (
    alert_id   INT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(100) NOT NULL,
    severity   ENUM('INFO','WARNING','CRITICAL') NOT NULL DEFAULT 'INFO',
    message    TEXT,
    resolved   TINYINT(1)   NOT NULL DEFAULT 0,
    timestamp  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    plot_id    INT          NOT NULL,
    CONSTRAINT fk_alerts_plot FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE RESTRICT ON UPDATE CASCADE
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
    alert_type         VARCHAR(100),
    CONSTRAINT fk_tasks_plot    FOREIGN KEY (plot_id)            REFERENCES plots(plot_id)       ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tasks_alert   FOREIGN KEY (alert_id)           REFERENCES alerts(alert_id)     ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_tasks_manager FOREIGN KEY (assigned_by_mgr_id) REFERENCES manager(manager_id)  ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ WORKER ↔ TASK (M:N) ═══════════════
CREATE TABLE worker_task (
    worker_id          INT NOT NULL,
    task_id            INT NOT NULL,
    assigned_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_mgr_id INT NOT NULL,
    PRIMARY KEY (worker_id, task_id),
    CONSTRAINT fk_wt_worker  FOREIGN KEY (worker_id)          REFERENCES worker(worker_id)    ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_wt_task    FOREIGN KEY (task_id)             REFERENCES tasks(task_id)       ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_wt_manager FOREIGN KEY (assigned_by_mgr_id)  REFERENCES manager(manager_id)  ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ HARVEST RECORDS ═══════════════
CREATE TABLE harvest_records (
    record_id    INT AUTO_INCREMENT PRIMARY KEY,
    harvest_date DATE    NOT NULL,
    quantity_kg  DOUBLE  NOT NULL,
    grade        ENUM('A','B','C') NOT NULL,
    crop_id      INT     NOT NULL,
    CONSTRAINT fk_harvest_crop FOREIGN KEY (crop_id) REFERENCES crops(crop_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ DISEASE DETECTIONS (Crop.health API) ═══════════════
CREATE TABLE disease_detections (
    detection_id         INT AUTO_INCREMENT PRIMARY KEY,
    plot_id              INT          NOT NULL,
    crop_id              INT          NOT NULL,
    image_path           VARCHAR(500),
    disease_name         VARCHAR(150),
    confidence           ENUM('HIGH','MEDIUM','LOW') NOT NULL,
    recommendation       TEXT,
    status               ENUM('PENDING','CONFIRMED','DISMISSED') NOT NULL DEFAULT 'PENDING',
    detected_by_worker_id INT         NOT NULL,
    CONSTRAINT fk_disease_plot   FOREIGN KEY (plot_id)              REFERENCES plots(plot_id)   ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_disease_crop   FOREIGN KEY (crop_id)              REFERENCES crops(crop_id)   ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_disease_worker FOREIGN KEY (detected_by_worker_id) REFERENCES worker(worker_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ DRONES ═══════════════
CREATE TABLE drones (
    drone_id              INT AUTO_INCREMENT PRIMARY KEY,
    serial_number         VARCHAR(100) NOT NULL UNIQUE,
    model                 VARCHAR(100),
    status                ENUM('IDLE','MAPPING','IRRIGATING','RETURNING','MAINT') NOT NULL DEFAULT 'IDLE',
    battery_percent       DOUBLE,
    assigned_plot_id      INT,
    operated_by_worker_id INT          NOT NULL,
    CONSTRAINT fk_drones_plot   FOREIGN KEY (assigned_plot_id)      REFERENCES plots(plot_id)   ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_drones_worker FOREIGN KEY (operated_by_worker_id) REFERENCES worker(worker_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ GIS PLOTS ═══════════════
CREATE TABLE gis_plots (
    gis_id              INT AUTO_INCREMENT PRIMARY KEY,
    plot_id             INT NOT NULL UNIQUE,
    center_latitude     DOUBLE,
    center_longitude    DOUBLE,
    boundary_geojson    MEDIUMTEXT,
    satellite_image_url VARCHAR(500),
    soil_heatmap_url    VARCHAR(500),
    CONSTRAINT fk_gis_plot FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE ON UPDATE CASCADE
);

-- ═══════════════ IRRIGATION LOGS ═══════════════
CREATE TABLE irrigation_logs (
    log_id           INT AUTO_INCREMENT PRIMARY KEY,
    plot_id          INT          NOT NULL,
    date             DATE         NOT NULL,
    volume_litres    DOUBLE,
    method           ENUM('DRIP','SPRINKLER','FLOOD','DRONE') NOT NULL,
    duration_minutes INT,
    triggered_by     VARCHAR(100),
    CONSTRAINT fk_irrigation_plot FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ═══════════════ NOTIFICATIONS ═══════════════
CREATE TABLE notifications (
    notification_id      INT AUTO_INCREMENT PRIMARY KEY,
    recipient_id         INT          NOT NULL,
    recipient_role       ENUM('ADMIN','MANAGER','WORKER') NOT NULL,
    channel              ENUM('PUSH','SMS','WHATSAPP','EMAIL') NOT NULL,
    subject              VARCHAR(255),
    body                 TEXT,
    status               ENUM('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'QUEUED',
    related_entity_type  VARCHAR(100),
    related_entity_id    INT
);

-- ═══════════════ ATTENDANCE (R307 Fingerprint) ═══════════════
CREATE TABLE attendance (
    attendance_id INT AUTO_INCREMENT PRIMARY KEY,
    worker_id     INT          NOT NULL,
    check_in      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    check_out     TIMESTAMP    NULL,
    device_code   VARCHAR(50),
    CONSTRAINT attendance_ibfk_1 FOREIGN KEY (worker_id) REFERENCES worker(worker_id) ON DELETE CASCADE
);
