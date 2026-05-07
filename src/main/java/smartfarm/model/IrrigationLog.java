package smartfarm.model;

import java.time.LocalDateTime;

/**
 * TODO: IrrigationLog tracks every irrigation event on a plot.
 * - Can be triggered manually by a worker, automatically by sensor thresholds, or by a drone.
 * - Records water volume, duration, method (drip/sprinkler/drone), and source trigger.
 * - Links to: Plot (where), Worker (who initiated, nullable), Drone (if drone-irrigated, nullable).
 * - Used for water usage reports, scheduling optimization, and cost analysis.
 */
public class IrrigationLog {
    public enum Method { DRIP, SPRINKLER, FLOOD, DRONE }
    public enum TriggerSource { MANUAL, SENSOR_AUTO, SCHEDULED, DRONE_AUTO }

    private int logId;
    private int plotId;
    private Integer workerId;       // nullable — null if auto-triggered
    private Integer droneId;        // nullable — set only if drone performed irrigation
    private Method method;
    private TriggerSource triggerSource;
    private double waterVolumeLiters;
    private int durationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String notes;

    // TODO: Implement constructors, getters, setters
    // TODO: Implement DAO (IrrigationLogDAO) with save/getById/getAll/getByPlot/getByDateRange
    // TODO: Implement Service (IrrigationService) with scheduling logic, water budget tracking
}
