package smartfarm.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SystemLog {

    public enum LogType { INFO, WARNING, ERROR }

    private final LocalDateTime timestamp;
    private final LogType type;
    private final String source;
    private final String message;
    private final String user;

    public SystemLog(LogType type, String source, String message, String user) {
        this.timestamp = LocalDateTime.now();
        this.type = type;
        this.source = source;
        this.message = message;
        this.user = user;
    }

    public SystemLog(LogType type, String source, String message, String user, LocalDateTime timestamp) {
        this.timestamp = timestamp;
        this.type = type;
        this.source = source;
        this.message = message;
        this.user = user;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public LogType getType() { return type; }
    public String getSource() { return source; }
    public String getMessage() { return message; }
    public String getUser() { return user; }

    public String getFormattedTimestamp() {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
