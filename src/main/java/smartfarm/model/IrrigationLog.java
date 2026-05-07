package smartfarm.model;

import java.time.LocalDate;

public class IrrigationLog {
    public enum Method { DRIP, SPRINKLER, FLOOD, DRONE }

    private int logId;
    private int plotId;
    private LocalDate date;
    private double volumeLitres;
    private Method method;
    private int durationMinutes;
    private String triggeredBy;

    public IrrigationLog(int logId, int plotId, LocalDate date, double volumeLitres, Method method,
                         int durationMinutes, String triggeredBy) {
        this.logId = logId;
        this.plotId = plotId;
        this.date = date;
        this.volumeLitres = volumeLitres;
        this.method = method;
        this.durationMinutes = durationMinutes;
        this.triggeredBy = triggeredBy;
    }

    public IrrigationLog(int plotId, LocalDate date, double volumeLitres, Method method,
                         int durationMinutes, String triggeredBy) {
        this.logId = -1;
        this.plotId = plotId;
        this.date = date;
        this.volumeLitres = volumeLitres;
        this.method = method;
        this.durationMinutes = durationMinutes;
        this.triggeredBy = triggeredBy;
    }

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }
    public int getPlotId() { return plotId; }
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public double getVolumeLitres() { return volumeLitres; }
    public void setVolumeLitres(double volumeLitres) { this.volumeLitres = volumeLitres; }
    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
}
