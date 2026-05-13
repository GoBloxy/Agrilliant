package smartfarm.service;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class SettingsManager {

    private static final SettingsManager INSTANCE = new SettingsManager();
    public static SettingsManager getInstance() { return INSTANCE; }

    private final BooleanProperty useFahrenheit = new SimpleBooleanProperty(false);

    private SettingsManager() {}

    public boolean isUseFahrenheit() { return useFahrenheit.get(); }
    public void setUseFahrenheit(boolean value) { useFahrenheit.set(value); }
    public BooleanProperty useFahrenheitProperty() { return useFahrenheit; }

    public String formatTemp(float celsius) {
        if (useFahrenheit.get()) {
            float f = celsius * 9f / 5f + 32f;
            return String.format("%.1f °F", f);
        }
        return String.format("%.1f °C", celsius);
    }

    public String formatTempShort(float celsius) {
        if (useFahrenheit.get()) {
            float f = celsius * 9f / 5f + 32f;
            return String.format("%.0f°F", f);
        }
        return String.format("%.0f°C", celsius);
    }
}
