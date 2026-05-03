-- Smart Farm Management System — Database Schema
-- Run this script in MySQL to create the database and all tables.

CREATE DATABASE IF NOT EXISTS smart_farm;
USE smart_farm;

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

-- Farm workers
CREATE TABLE workers (

);

-- Tasks assigned to workers
CREATE TABLE tasks (

);

-- Harvest events
CREATE TABLE harvest_records (

);
