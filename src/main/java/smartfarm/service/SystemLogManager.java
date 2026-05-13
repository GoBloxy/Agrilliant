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

    public int size() {
        return logs.size();
    }
}