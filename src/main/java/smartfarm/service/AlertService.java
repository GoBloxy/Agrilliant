package smartfarm.service;

import smartfarm.model.Alert;
import smartfarm.model.SensorReading;

public class AlertService {

    // Checks sensor reading against thresholds and creates alerts if needed
    public void checkAndAlert(SensorReading reading, int plotId) {
        if (reading.getTemperature() > 40) {
            System.out.println("[ALERT] High temperature: " + reading.getTemperature() + "°C on plot " + plotId);
        }
        if (reading.getHumidity() < 20) {
            System.out.println("[ALERT] Low humidity: " + reading.getHumidity() + "% on plot " + plotId);
        }
    }

    // TODO: createAlertWithTask, createAlertOnly
}
