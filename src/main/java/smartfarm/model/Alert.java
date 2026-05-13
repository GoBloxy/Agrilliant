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


    public Alert(int alertId, String alertType, Severity severity,String message, boolean resolved, LocalDateTime timestamp, int plotId) 
    {
        this.alertId   = alertId;
        this.alertType = alertType;
        this.severity  = severity;
        this.message   = message;
        this.resolved  = resolved;
        this.timestamp = timestamp;
        this.plotId    = plotId;
    }


    public Alert(String alertType, Severity severity, String message, int plotId) 
    {
        this.alertId   = -1;
        this.alertType = alertType;
        this.severity  = severity;
        this.message   = message;
        this.resolved  = false;
        this.timestamp = LocalDateTime.now();
        this.plotId    = plotId;
    }
    
    // Constructor with timestamp for NotificationService
    public Alert(String alertType, Severity severity, String message, boolean resolved, LocalDateTime timestamp, int plotId) 
    {
        this.alertId   = -1;
        this.alertType = alertType;
        this.severity  = severity;
        this.message   = message;
        this.resolved  = resolved;
        this.timestamp = timestamp;
        this.plotId    = plotId;
    }


    public void resolve() { this.resolved = true; }
   
    public boolean isCritical() {return severity == Severity.CRITICAL;}
    
    public boolean isResolved() { return resolved; }
    

    // ── Getters ──

    public int getAlertId() { return alertId; }
    public String getAlertType() { return alertType; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public int getPlotId() { return plotId; }

    // ── Setters ──

    public void setAlertId(int alertId) { this.alertId = alertId; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setMessage(String message) { this.message = message; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setPlotId(int plotId) { this.plotId = plotId; }

    // ── toString ──

    @Override
    public String toString() {
        return "[" + severity + "] " + alertType + " — " + message + " (plot=" + plotId + ", resolved=" + resolved + ", " + timestamp + ")";
    }
}
