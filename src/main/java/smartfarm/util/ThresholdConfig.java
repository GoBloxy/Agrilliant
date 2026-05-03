package smartfarm.util;

/**
 * Configurable thresholds for the alert system.
 * Adjust these values to match your farm's requirements.
 */
public class ThresholdConfig {

    // Temperature thresholds (Celsius)
    public static final float TEMP_CRITICAL_HIGH = 35.0f;
    public static final float TEMP_WARNING_HIGH  = 30.0f;
    public static final float TEMP_CRITICAL_LOW  = 5.0f;

    // Humidity thresholds (%)
    public static final float HUM_WARNING_LOW  = 30.0f;
    public static final float HUM_WARNING_HIGH = 85.0f;

    // TODO: Add more thresholds as needed
}
