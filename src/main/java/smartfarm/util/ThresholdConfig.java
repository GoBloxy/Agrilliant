package smartfarm.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configurable thresholds for the alert system. Values are loaded once
 * at class-load time from {@code thresholds.properties} on the
 * classpath (which on Android is the APK's {@code assets/} bundle and
 * on desktop is {@code src/main/resources/}). Missing or unparseable
 * keys fall back to the hard-coded defaults below — those match the
 * values shipped in {@code thresholds.properties} so the runtime
 * behaviour is identical when the file is present and untouched.
 *
 * <p>Public API is unchanged from the pre-H10 implementation: the
 * eight constants stay {@code public static final float}, and the
 * existing call sites in {@link smartfarm.service.AlertService}
 * compile and behave the same.
 */
public class ThresholdConfig {

    private static final String TAG = "ThresholdConfig";

    /** Resource name we look up; the classpath/assets convention works on both targets. */
    private static final String CONFIG = "thresholds.properties";

    // ---------------------------------------------------------------------
    // Public constants — same names + types as before H10.
    // ---------------------------------------------------------------------

    public static final float TEMP_CRITICAL_HIGH;
    public static final float TEMP_WARNING_HIGH;
    public static final float TEMP_CRITICAL_LOW;

    public static final float HUM_WARNING_LOW;
    public static final float HUM_WARNING_HIGH;

    public static final float SOIL_CRITICAL_DRY;
    public static final float SOIL_WARNING_DRY;
    public static final float SOIL_WARNING_WET;

    static {
        Properties p = loadProperties();

        TEMP_CRITICAL_HIGH = readFloat(p, "temp.critical.high", 35.0f);
        TEMP_WARNING_HIGH  = readFloat(p, "temp.warning.high",  30.0f);
        TEMP_CRITICAL_LOW  = readFloat(p, "temp.critical.low",   5.0f);

        HUM_WARNING_LOW    = readFloat(p, "hum.warning.low",    30.0f);
        HUM_WARNING_HIGH   = readFloat(p, "hum.warning.high",   85.0f);

        SOIL_CRITICAL_DRY  = readFloat(p, "soil.critical.dry",  20.0f);
        SOIL_WARNING_DRY   = readFloat(p, "soil.warning.dry",   30.0f);
        SOIL_WARNING_WET   = readFloat(p, "soil.warning.wet",   85.0f);
    }

    private ThresholdConfig() {}

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = ThresholdConfig.class.getClassLoader().getResourceAsStream(CONFIG)) {
            if (in == null) {
                Logger.w(TAG, CONFIG + " not on classpath — using built-in defaults");
                return props;
            }
            props.load(in);
            Logger.i(TAG, "Loaded " + CONFIG + " (" + props.size() + " keys)");
        } catch (IOException e) {
            Logger.w(TAG, "Failed to read " + CONFIG + " — using built-in defaults", e);
        }
        return props;
    }

    private static float readFloat(Properties p, String key, float fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            Logger.w(TAG, "Could not parse '" + key + "=" + v + "' — using default " + fallback);
            return fallback;
        }
    }
}
