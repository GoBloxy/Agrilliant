package smartfarm.util;

import smartfarm.model.HarvestRecord;
import smartfarm.model.SensorReading;

import java.util.List;

public class CSVExporter {

    public static String exportSensorData(List<SensorReading> readings) {
        if (readings == null || readings.isEmpty()) return "";
        
        StringBuilder csv = new StringBuilder("Timestamp,DeviceID,Temperature,Humidity\n");
        for (SensorReading r : readings) {
            csv.append(r.getTimestamp()).append(",")
               .append(r.getDeviceId()).append(",")
               .append(r.getTemperature()).append(",")
               .append(r.getHumidity()).append("\n");
        }
        return csv.toString();
    }

    public static String exportHarvestData(List<HarvestRecord> records) {
        if (records == null || records.isEmpty()) return "";

        StringBuilder csv = new StringBuilder("Date,CropID,QuantityKg,Grade\n");
        for (HarvestRecord r : records) {
            csv.append(r.getHarvestDate()).append(",")
               .append(r.getCropId()).append(",")
               .append(r.getQuantityKg()).append(",")
               .append(r.getGrade()).append("\n");
        }
        return csv.toString();
    }
}
