package smartfarm.service;

import smartfarm.dao.AlertDAO;
import smartfarm.model.Alert;
import smartfarm.model.Alert.Severity;
import smartfarm.model.SensorReading;
import smartfarm.model.Task;
import smartfarm.util.Logger;
import smartfarm.util.ThresholdConfig;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AlertService {

    private static final String TAG = "AlertService";

    private final AlertDAO alertDAO;
    private final TaskService taskService;

    public AlertService(AlertDAO alertDAO, TaskService taskService) {
        this.alertDAO = alertDAO;
        this.taskService = taskService;
    }

    // Convenience constructor (no auto-task creation)
    public AlertService() {
        this.alertDAO = new AlertDAO();
        this.taskService = null;
    }

    // Core: check a sensor reading against thresholds
    public void checkAndAlert(SensorReading reading, int plotId) {
        float temp = reading.getTemperature();
        float hum  = reading.getHumidity();

        // Temperature checks
        if (temp >= ThresholdConfig.TEMP_CRITICAL_HIGH) {
            createAlertWithTask(
                "HIGH_TEMP", Severity.CRITICAL,
                "Temperature critically high: " + temp + "°C",
                plotId
            );
        } else if (temp >= ThresholdConfig.TEMP_WARNING_HIGH) {
            createAlertOnly(
                "HIGH_TEMP", Severity.WARNING,
                "Temperature above normal: " + temp + "°C",
                plotId
            );
        } else if (temp <= ThresholdConfig.TEMP_CRITICAL_LOW) {
            createAlertWithTask(
                "LOW_TEMP", Severity.CRITICAL,
                "Temperature critically low: " + temp + "°C",
                plotId
            );
        }

        // Humidity checks
        if (hum >= ThresholdConfig.HUM_WARNING_HIGH) {
            createAlertOnly(
                "HIGH_HUMIDITY", Severity.WARNING,
                "Humidity above normal: " + hum + "%",
                plotId
            );
        } else if (hum <= ThresholdConfig.HUM_WARNING_LOW) {
            createAlertOnly(
                "LOW_HUMIDITY", Severity.WARNING,
                "Humidity below normal: " + hum + "%",
                plotId
            );
        }

        // Soil moisture checks (FC-28)
        float soil = reading.getSoilMoisture();
        if (!Float.isNaN(soil)) {
            if (soil <= ThresholdConfig.SOIL_CRITICAL_DRY) {
                createAlertWithTask(
                    "DRY_SOIL", Severity.CRITICAL,
                    "Soil critically dry: " + soil + "% — irrigation needed",
                    plotId
                );
            } else if (soil <= ThresholdConfig.SOIL_WARNING_DRY) {
                createAlertOnly(
                    "DRY_SOIL", Severity.WARNING,
                    "Soil moisture low: " + soil + "%",
                    plotId
                );
            } else if (soil >= ThresholdConfig.SOIL_WARNING_WET) {
                createAlertOnly(
                    "WET_SOIL", Severity.WARNING,
                    "Soil too wet: " + soil + "% — possible overwatering",
                    plotId
                );
            }
        }
    }

    // Save an alert AND auto-create a task for it
    public void createAlertWithTask(String alertType, Severity severity, String message, int plotId) {
        // Step 1 — save the alert
        Alert alert = new Alert(alertType, severity, message, plotId);
        try {
            alertDAO.save(alert);
            Logger.i(TAG, "[ALERT] " + alert);
        } catch (SQLException e) {
            Logger.e(TAG, "Failed to save alert", e);
            return;
        }

        // Step 2 — auto-create an urgent task (if TaskService is available)
        if (taskService != null) {
            try {
                Task task = new Task(
                    "AUTO: " + message,
                    LocalDate.now(),
                    plotId,
                    alert.getAlertId() > 0 ? alert.getAlertId() : null,
                    /* assignedByMgrId */ 0,
                    alertType
                );
                taskService.autoCreateTask(task);
                Logger.i(TAG, "[TASK] Auto-created task for alert: " + alertType);
            } catch (RuntimeException e) {
                Logger.e(TAG, "Auto-task creation failed", e);
            }
        }
    }

    // Save an alert only (no task needed)
    public void createAlertOnly(String alertType, Severity severity, String message, int plotId) {
        Alert alert = new Alert(alertType, severity, message, plotId);
        try {
            alertDAO.save(alert);
            Logger.i(TAG, "[ALERT] " + alert);
        } catch (SQLException e) {
            Logger.e(TAG, "Failed to save alert", e);
        }
    }

    //  Resolve an alert by ID
    public void resolveAlert(int alertId) {
        try {
            alertDAO.markResolved(alertId);
        } catch (SQLException e) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    //Retrieve alerts
    public List<Alert> getAllAlerts() {
        try {
            return alertDAO.getAll();
        } catch (SQLException e) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Alert> getUnresolvedAlerts() {
        try {
            return alertDAO.getUnresolved();
        } catch (SQLException e) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Alert> getAlertsByPlot(int plotId) {
        try {
            return alertDAO.getAlertsByPlot(plotId);
        } catch (SQLException e) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Alert> getCriticalAlerts() {
        List<Alert> all = getUnresolvedAlerts();
        List<Alert> critical = new ArrayList<>();
        for (Alert alert : all) {
            if (alert.isCritical()) {
                critical.add(alert);
            }
        }
        return critical;
    }
}
