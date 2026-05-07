-- Smart Farm Management System — Database Schema
-- Run this script in MySQL to create the database and all tables.

CREATE DATABASE IF NOT EXISTS agrilliant;
USE agrilliant;

-- Application users (sign-in / sign-up)
-- Workers are also users with role='worker' and a phone number
CREATE TABLE users (
    user_id           INT AUTO_INCREMENT PRIMARY KEY,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    full_name         VARCHAR(100) NOT NULL,
    role              ENUM('ADMIN','MANAGER','WORKER') NOT NULL DEFAULT 'WORKER',
    phone             VARCHAR(20),
    active_task_count INT DEFAULT 0,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Plots of land on the farm
CREATE TABLE plots (

);

-- Crops planted on each plot
CREATE TABLE crops (

);

-- Every reading sent by an ESP32 device
CREATE TABLE sensor_readings (

);

-- Alerts generated when thresholds are crossed
CREATE TABLE alerts (

);

-- (Workers are stored in the users table with role='worker')

-- Tasks assigned to workers (references users table)
CREATE TABLE tasks (

);

-- Harvest events
CREATE TABLE harvest_records (

);
