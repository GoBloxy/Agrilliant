package smartfarm.model;

import java.time.LocalDateTime;

public class Alert {
    public enum Severity { INFO, WARNING, CRITICAL }

    private int alertId;
    private String alertType;
    private Severity severity;
    private String message;
    private boolean resolved;
    private LocalDateTime timestamp;
    private int plotId;

    // TODO: Constructor, resolve(), getters, setters
}
