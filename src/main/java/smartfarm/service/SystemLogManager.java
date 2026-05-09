package smartfarm.service;

import smartfarm.model.SystemLog;
import smartfarm.model.SystemLog.LogType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SystemLogManager {

    private static final SystemLogManager INSTANCE = new SystemLogManager();
    private final List<SystemLog> logs = new ArrayList<>();

    private SystemLogManager() {
        seedSampleLogs();
    }

    public static SystemLogManager getInstance() {
        return INSTANCE;
    }

    public void log(LogType type, String source, String message, String user) {
        logs.add(new SystemLog(type, source, message, user));
    }

    public void info(String source, String message, String user) {
        log(LogType.INFO, source, message, user);
    }

    public void warning(String source, String message, String user) {
        log(LogType.WARNING, source, message, user);
    }

    public void error(String source, String message, String user) {
        log(LogType.ERROR, source, message, user);
    }

    public List<SystemLog> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    public void clear() {
        logs.clear();
    }

    private void seedSampleLogs() {
        info("AuthService", "Admin user logged in successfully", "admin");
        info("SensorHandler", "Sensor plot1_sensor connected and streaming", "system");
        info("TaskService", "Task #1 assigned to worker John", "manager1");
        warning("SensorHandler", "Sensor plot3_sensor battery low (15%)", "system");
        info("HarvestService", "Harvest recorded for crop Corn (45.2 kg)", "manager1");
        error("DeviceDAO", "Failed to reach device TEMP_003 — timeout after 5s", "system");
        info("AuthService", "Manager logged in", "manager1");
        warning("CropDAO", "Crop Wheat is overdue for harvest by 3 days", "system");
        info("WorkerService", "Worker Jane marked on duty", "manager1");
        info("AlertService", "Alert #5 resolved: High temperature in Plot 2", "manager1");
        error("DBConnection", "Connection pool exhausted — retrying", "system");
        info("AuthService", "User session refreshed", "admin");
    }
}