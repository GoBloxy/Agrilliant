CREATE TABLE `admin` (
  `admin_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `username` varchar(50) NOT NULL,
  `email` varchar(150) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `alerts` (
  `alert_id` int NOT NULL AUTO_INCREMENT,
  `alert_type` varchar(100) NOT NULL,
  `severity` enum('INFO','WARNING','CRITICAL') NOT NULL DEFAULT 'INFO',
  `message` text,
  `resolved` tinyint(1) NOT NULL DEFAULT '0',
  `timestamp` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `plot_id` int NOT NULL,
  PRIMARY KEY (`alert_id`),
  KEY `fk_alerts_plot` (`plot_id`),
  CONSTRAINT `fk_alerts_plot` FOREIGN KEY (`plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=232 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `attendance` (
  `attendance_id` int NOT NULL AUTO_INCREMENT,
  `worker_id` int NOT NULL,
  `check_in` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `check_out` timestamp NULL DEFAULT NULL,
  `device_code` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`attendance_id`),
  KEY `worker_id` (`worker_id`),
  CONSTRAINT `attendance_ibfk_1` FOREIGN KEY (`worker_id`) REFERENCES `worker` (`worker_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `crops` (
  `crop_id` int NOT NULL AUTO_INCREMENT,
  `crop_name` varchar(100) NOT NULL,
  `planting_date` date DEFAULT NULL,
  `harvest_date` date DEFAULT NULL,
  `growth_stage` enum('PLANTED','GROWING','READY','HARVESTED') NOT NULL DEFAULT 'PLANTED',
  `expected_yield` double DEFAULT NULL,
  `plot_id` int NOT NULL,
  PRIMARY KEY (`crop_id`),
  KEY `fk_crops_plot` (`plot_id`),
  CONSTRAINT `fk_crops_plot` FOREIGN KEY (`plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `devices` (
  `device_id` int NOT NULL AUTO_INCREMENT,
  `device_code` varchar(100) NOT NULL,
  `type` enum('TEMP_HUM','SOIL') NOT NULL,
  `status` enum('ONLINE','OFFLINE','MAINT') NOT NULL DEFAULT 'OFFLINE',
  `plot_id` int NOT NULL,
  `firmware_version` varchar(50) DEFAULT NULL,
  `last_seen_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `device_code` (`device_code`),
  KEY `fk_devices_plot` (`plot_id`),
  CONSTRAINT `fk_devices_plot` FOREIGN KEY (`plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `disease_detections` (
  `detection_id` int NOT NULL AUTO_INCREMENT,
  `plot_id` int NOT NULL,
  `crop_id` int NOT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `disease_name` varchar(150) DEFAULT NULL,
  `confidence` enum('HIGH','MEDIUM','LOW') NOT NULL,
  `recommendation` text,
  `status` enum('PENDING','CONFIRMED','DISMISSED') NOT NULL DEFAULT 'PENDING',
  `detected_by_worker_id` int NOT NULL,
  PRIMARY KEY (`detection_id`),
  KEY `fk_disease_plot` (`plot_id`),
  KEY `fk_disease_crop` (`crop_id`),
  KEY `fk_disease_worker` (`detected_by_worker_id`),
  CONSTRAINT `fk_disease_crop` FOREIGN KEY (`crop_id`) REFERENCES `crops` (`crop_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_disease_plot` FOREIGN KEY (`plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_disease_worker` FOREIGN KEY (`detected_by_worker_id`) REFERENCES `worker` (`worker_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `drones` (
  `drone_id` int NOT NULL AUTO_INCREMENT,
  `serial_number` varchar(100) NOT NULL,
  `model` varchar(100) DEFAULT NULL,
  `status` enum('IDLE','MAPPING','IRRIGATING','RETURNING','MAINT') NOT NULL DEFAULT 'IDLE',
  `battery_percent` double DEFAULT NULL,
  `assigned_plot_id` int DEFAULT NULL,
  `operated_by_worker_id` int NOT NULL,
  PRIMARY KEY (`drone_id`),
  UNIQUE KEY `serial_number` (`serial_number`),
  KEY `fk_drones_plot` (`assigned_plot_id`),
  KEY `fk_drones_worker` (`operated_by_worker_id`),
  CONSTRAINT `fk_drones_plot` FOREIGN KEY (`assigned_plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_drones_worker` FOREIGN KEY (`operated_by_worker_id`) REFERENCES `worker` (`worker_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `gis_plots` (
  `gis_id` int NOT NULL AUTO_INCREMENT,
  `plot_id` int NOT NULL,
  `center_latitude` double DEFAULT NULL,
  `center_longitude` double DEFAULT NULL,
  `boundary_geojson` mediumtext,
  `satellite_image_url` varchar(500) DEFAULT NULL,
  `soil_heatmap_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`gis_id`),
  UNIQUE KEY `plot_id` (`plot_id`),
  CONSTRAINT `fk_gis_plot` FOREIGN KEY (`plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `harvest_records` (
  `record_id` int NOT NULL AUTO_INCREMENT,
  `harvest_date` date NOT NULL,
  `quantity_kg` double NOT NULL,
  `grade` enum('A','B','C') NOT NULL,
  `crop_id` int NOT NULL,
  PRIMARY KEY (`record_id`),
  KEY `fk_harvest_crop` (`crop_id`),
  CONSTRAINT `fk_harvest_crop` FOREIGN KEY (`crop_id`) REFERENCES `crops` (`crop_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `irrigation_logs` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `plot_id` int NOT NULL,
  `date` date NOT NULL,
  `volume_litres` double DEFAULT NULL,
  `method` enum('DRIP','SPRINKLER','FLOOD','DRONE') NOT NULL,
  `duration_minutes` int DEFAULT NULL,
  `triggered_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `fk_irrigation_plot` (`plot_id`),
  CONSTRAINT `fk_irrigation_plot` FOREIGN KEY (`plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `manager` (
  `manager_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `username` varchar(50) NOT NULL,
  `email` varchar(150) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`manager_id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `notification_id` int NOT NULL AUTO_INCREMENT,
  `recipient_id` int NOT NULL,
  `recipient_role` enum('ADMIN','MANAGER','WORKER') NOT NULL,
  `channel` enum('PUSH','SMS','WHATSAPP','EMAIL') NOT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `body` text,
  `status` enum('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'QUEUED',
  `related_entity_type` varchar(100) DEFAULT NULL,
  `related_entity_id` int DEFAULT NULL,
  PRIMARY KEY (`notification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plots` (
  `plot_id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `size_acres` double DEFAULT NULL,
  `soil_type` varchar(100) DEFAULT NULL,
  `manager_id` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`plot_id`),
  KEY `fk_plots_manager` (`manager_id`),
  CONSTRAINT `fk_plots_manager` FOREIGN KEY (`manager_id`) REFERENCES `manager` (`manager_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sensor_readings` (
  `reading_id` int NOT NULL AUTO_INCREMENT,
  `device_id` int NOT NULL,
  `temperature` float DEFAULT NULL,
  `humidity` float DEFAULT NULL,
  `soil_moisture` float DEFAULT NULL,
  `timestamp` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`reading_id`),
  KEY `fk_readings_device` (`device_id`),
  CONSTRAINT `fk_readings_device` FOREIGN KEY (`device_id`) REFERENCES `devices` (`device_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1696 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tasks` (
  `task_id` int NOT NULL AUTO_INCREMENT,
  `description` text NOT NULL,
  `status` enum('PENDING','IN_PROGRESS','DONE') NOT NULL DEFAULT 'PENDING',
  `due_date` date DEFAULT NULL,
  `plot_id` int NOT NULL,
  `alert_id` int DEFAULT NULL,
  `assigned_by_mgr_id` int NOT NULL,
  `alert_type` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`task_id`),
  KEY `fk_tasks_plot` (`plot_id`),
  KEY `fk_tasks_alert` (`alert_id`),
  KEY `fk_tasks_manager` (`assigned_by_mgr_id`),
  CONSTRAINT `fk_tasks_alert` FOREIGN KEY (`alert_id`) REFERENCES `alerts` (`alert_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_tasks_manager` FOREIGN KEY (`assigned_by_mgr_id`) REFERENCES `manager` (`manager_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_tasks_plot` FOREIGN KEY (`plot_id`) REFERENCES `plots` (`plot_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `worker` (
  `worker_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `job_title` varchar(100) DEFAULT NULL,
  `skills` varchar(255) DEFAULT NULL,
  `on_duty` tinyint(1) NOT NULL DEFAULT '0',
  `fingerprint_id` int DEFAULT NULL,
  `manager_id` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`worker_id`),
  KEY `fk_worker_manager` (`manager_id`),
  CONSTRAINT `fk_worker_manager` FOREIGN KEY (`manager_id`) REFERENCES `manager` (`manager_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `worker_task` (
  `worker_id` int NOT NULL,
  `task_id` int NOT NULL,
  `assigned_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `assigned_by_mgr_id` int NOT NULL,
  PRIMARY KEY (`worker_id`,`task_id`),
  KEY `fk_wt_task` (`task_id`),
  KEY `fk_wt_manager` (`assigned_by_mgr_id`),
  CONSTRAINT `fk_wt_manager` FOREIGN KEY (`assigned_by_mgr_id`) REFERENCES `manager` (`manager_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_wt_task` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_wt_worker` FOREIGN KEY (`worker_id`) REFERENCES `worker` (`worker_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
