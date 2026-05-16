package smartfarm.service;

import smartfarm.dao.LogDAO;
import smartfarm.model.SystemLog;
import smartfarm.model.SystemLog.LogType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SystemLogManager {

    private static final SystemLogManager INSTANCE = new SystemLogManager();
    private final List<SystemLog> logs = new ArrayList<>();
    private final LogDAO logDAO = new LogDAO();

    private SystemLogManager() {
        // Load persisted logs from database on startup
        try {
            List<SystemLog> persisted = logDAO.getAll();
            // getAll() returns newest-first; reverse so oldest is first in our list
            Collections.reverse(persisted);
            logs.addAll(persisted);
        } catch (Exception e) {
            System.err.println("[SystemLogManager] Failed to load logs from DB: " + e.getMessage());
        }
    }

    public static SystemLogManager getInstance() {
        return INSTANCE;
    }

    public void log(LogType type, String source, String message, String user) {
        SystemLog entry = new SystemLog(type, source, message, user);
        logs.add(entry);
        logDAO.save(entry);
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
        logDAO.clearAll();
    }

    public int size() {
        return logs.size();
    }
}