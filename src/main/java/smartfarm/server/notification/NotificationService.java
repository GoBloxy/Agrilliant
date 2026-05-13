package smartfarm.server.notification;

import smartfarm.service.LiveSensorData;
import smartfarm.dao.AlertDAO;
import smartfarm.model.Alert;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Notification service for real-time alerts and updates
 * Provides push notifications for critical farm events
 */
// Desktop build only — paired with FarmServer (see its header comment).
public class NotificationService {
    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());
    
    private static NotificationService instance;
    private final AlertDAO alertDAO = new AlertDAO();
    private final LiveSensorData liveSensorData = LiveSensorData.getInstance();
    
    // Notification listeners
    private final List<NotificationListener> listeners = new CopyOnWriteArrayList<>();
    
    // Scheduled tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private NotificationService() {
        startMonitoringTasks();
    }
    
    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }
    
    public void addListener(NotificationListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(NotificationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Send immediate notification to all listeners
     */
    public void sendNotification(NotificationType type, String title, String message, String details) {
        Notification notification = new Notification(type, title, message, details, System.currentTimeMillis());
        
        // Broadcast to all listeners
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotification(notification);
            } catch (Exception e) {
                logger.warning("Error notifying listener: " + e.getMessage());
            }
        }
        
        logger.info("Notification sent: " + title + " - " + message);
    }
    
    /**
     * Send sensor alert based on threshold violations
     */
    public void sendSensorAlert(String deviceId, String sensorType, double value, double threshold, Alert.Severity severity) {
        String title = "Sensor Alert";
        String message = String.format("%s sensor %s: %.2f (threshold: %.2f)", 
            deviceId, sensorType, value, threshold);
        String details = String.format("Device: %s\nSensor: %s\nCurrent: %.2f\nThreshold: %.2f\nSeverity: %s", 
            deviceId, sensorType, value, threshold, severity);
        
        NotificationType type = severity == Alert.Severity.CRITICAL ? NotificationType.CRITICAL : NotificationType.WARNING;
        sendNotification(type, title, message, details);
        
        // Create alert in database
        try {
            Alert alert = new Alert(
                "SENSOR_THRESHOLD",
                severity,
                message,
                false,
                java.time.LocalDateTime.now(),
                0 // plot_id would be determined from device
            );
            alertDAO.save(alert);
        } catch (Exception e) {
            logger.severe("Failed to save alert: " + e.getMessage());
        }
    }
    
    /**
     * Send device status notification
     */
    public void sendDeviceStatusNotification(String deviceId, String status, String details) {
        String title = "Device Status";
        String message = String.format("Device %s is %s", deviceId, status);
        
        NotificationType type = "OFFLINE".equals(status) ? NotificationType.CRITICAL : NotificationType.INFO;
        sendNotification(type, title, message, details);
    }
    
    /**
     * Send task notification
     */
    public void sendTaskNotification(String taskTitle, String assignedTo, String dueDate) {
        String title = "Task Assigned";
        String message = String.format("Task '%s' assigned to %s (Due: %s)", taskTitle, assignedTo, dueDate);
        String details = String.format("Task: %s\nAssigned to: %s\nDue date: %s", taskTitle, assignedTo, dueDate);
        
        sendNotification(NotificationType.TASK, title, message, details);
    }
    
    /**
     * Send harvest notification
     */
    public void sendHarvestNotification(String cropName, double quantity, String quality) {
        String title = "Harvest Completed";
        String message = String.format("Harvested %.2f kg of %s (Quality: %s)", quantity, cropName, quality);
        String details = String.format("Crop: %s\nQuantity: %.2f kg\nQuality: %s\nTime: %s", 
            cropName, quantity, quality, java.time.LocalDateTime.now());
        
        sendNotification(NotificationType.SUCCESS, title, message, details);
    }
    
    private void startMonitoringTasks() {
        // Monitor sensor data every 30 seconds
        scheduler.scheduleAtFixedRate(this::checkSensorThresholds, 30, 30, TimeUnit.SECONDS);
        
        // Check for unresolved alerts every 5 minutes
        scheduler.scheduleAtFixedRate(this::checkPendingAlerts, 300, 300, TimeUnit.SECONDS);
        
        // Cleanup old notifications every hour
        scheduler.scheduleAtFixedRate(this::cleanupOldNotifications, 3600, 3600, TimeUnit.SECONDS);
    }
    
    private void checkSensorThresholds() {
        try {
            // Get latest sensor data from LiveSensorData
            var sensorData = liveSensorData.getAllLatestReadings();
            
            for (var entry : sensorData.entrySet()) {
                String deviceId = entry.getKey();
                var reading = entry.getValue();
                
                // Check temperature thresholds
                if (reading.getTemperature() > 40.0) {
                    sendSensorAlert(deviceId, "Temperature", reading.getTemperature(), 40.0, Alert.Severity.CRITICAL);
                } else if (reading.getTemperature() < 5.0) {
                    sendSensorAlert(deviceId, "Temperature", reading.getTemperature(), 5.0, Alert.Severity.CRITICAL);
                }
                
                // Check humidity thresholds
                if (reading.getHumidity() > 90.0) {
                    sendSensorAlert(deviceId, "Humidity", reading.getHumidity(), 90.0, Alert.Severity.WARNING);
                } else if (reading.getHumidity() < 20.0) {
                    sendSensorAlert(deviceId, "Humidity", reading.getHumidity(), 20.0, Alert.Severity.WARNING);
                }
                
                // Check soil moisture thresholds
                if (reading.getSoilMoisture() < 30.0) {
                    sendSensorAlert(deviceId, "Soil Moisture", reading.getSoilMoisture(), 30.0, Alert.Severity.WARNING);
                }
            }
        } catch (Exception e) {
            logger.severe("Error checking sensor thresholds: " + e.getMessage());
        }
    }
    
    private void checkPendingAlerts() {
        try {
            List<Alert> unresolvedAlerts = alertDAO.getUnresolved();
            
            if (!unresolvedAlerts.isEmpty()) {
                String title = "Pending Alerts";
                String message = String.format("You have %d unresolved alerts requiring attention", unresolvedAlerts.size());
                String details = "Critical alerts need immediate attention to prevent crop damage.";
                
                sendNotification(NotificationType.WARNING, title, message, details);
            }
        } catch (Exception e) {
            logger.severe("Error checking pending alerts: " + e.getMessage());
        }
    }
    
    private void cleanupOldNotifications() {
        // Clean up old notifications (older than 24 hours)
        // This would be implemented based on your notification storage strategy
        logger.info("Notification cleanup completed");
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
    
    // Notification data classes
    public enum NotificationType {
        INFO, WARNING, CRITICAL, SUCCESS, TASK, SYSTEM
    }
    
    public static class Notification {
        private final NotificationType type;
        private final String title;
        private final String message;
        private final String details;
        private final long timestamp;
        
        public Notification(NotificationType type, String title, String message, String details, long timestamp) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.details = details;
            this.timestamp = timestamp;
        }
        
        // Getters
        public NotificationType getType() { return type; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }
        public long getTimestamp() { return timestamp; }
    }
    
    public interface NotificationListener {
        void onNotification(Notification notification);
    }
}
