package smartfarm.util;

import smartfarm.model.HarvestRecord;
import smartfarm.model.SensorReading;

import com.gluonhq.attach.storage.StorageService;
import com.gluonhq.attach.util.Platform;
import com.gluonhq.attach.util.Services;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/**
 * CSV serialization + cross-platform "save to disk" for dashboard
 * exports.
 *
 * <p>The pure formatting functions {@link #exportSensorData(List)} and
 * {@link #exportHarvestData(List)} are untouched from pre-H8 — they
 * still just stringify the input. 3bdelbary's controllers that already
 * call them keep working.
 *
 * <p>{@link #saveCsv(String, String)} is the new H8 addition. It picks
 * an appropriate target directory based on the platform:
 * <ul>
 *   <li><b>Android</b> — via Gluon Attach
 *       {@code StorageService.getPublicStorage("Documents")}. The file
 *       lands in the OS-visible public Documents folder so the user
 *       can find it through any file manager. Falls back to the
 *       app-private storage if the public folder isn't writable (rare
 *       SD-card scenarios).</li>
 *   <li><b>Desktop</b> — writes into {@code ~/Downloads/}, creating
 *       the directory if it doesn't exist. Matches the plan §H8
 *       requirement.</li>
 * </ul>
 *
 * <p>The method returns the final {@link File} so callers can show a
 * "Saved to ..." toast / dialog with the actual path.
 */
public class CSVExporter {

    private static final String TAG = "CSVExporter";

    private CSVExporter() {}

    // ---------------------------------------------------------------------
    // Pure CSV serialization (signatures unchanged from pre-H8).
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // H8: cross-platform file save.
    // ---------------------------------------------------------------------

    /**
     * Writes {@code content} to disk under a platform-appropriate folder
     * and returns the resulting {@link File}.
     *
     * @param content       the file body (typically from
     *                      {@link #exportSensorData(List)} or
     *                      {@link #exportHarvestData(List)}).
     * @param suggestedName the file name including extension, e.g.
     *                      {@code "harvest_2026.csv"}. The name is used
     *                      verbatim; the directory is platform-chosen.
     * @return the written file (already on disk).
     * @throws IOException if the file system rejected the write.
     */
    public static File saveCsv(String content, String suggestedName) throws IOException {
        File target = pickSaveLocation(suggestedName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent);
        }
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
        Logger.i(TAG, "Wrote " + content.length() + " bytes → " + target);
        return target;
    }

    /**
     * Picks where the next {@link #saveCsv(String, String)} call will
     * write. Exposed (package-private) only so tests can reason about
     * the result without actually writing.
     */
    static File pickSaveLocation(String suggestedName) {
        // Android: public Documents folder via Gluon Storage.
        if (isAndroid()) {
            Optional<File> publicDir = Services.get(StorageService.class)
                    .flatMap(svc -> svc.getPublicStorage("Documents"));
            if (publicDir.isPresent()) {
                return new File(publicDir.get(), suggestedName);
            }
            Logger.w(TAG, "Public Documents storage unavailable on Android — falling back to private storage");
            Optional<File> priv = Services.get(StorageService.class)
                    .flatMap(StorageService::getPrivateStorage);
            if (priv.isPresent()) {
                return new File(priv.get(), suggestedName);
            }
            // Last-ditch — should never happen on a real Android device.
            return new File(System.getProperty("java.io.tmpdir", "/tmp"), suggestedName);
        }

        // Desktop: ~/Downloads (per plan §H8).
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        return new File(downloads, suggestedName);
    }

    /**
     * {@code true} if Gluon Attach reports we're on Android. Safe to call
     * even when Attach isn't on the classpath (we just return false).
     */
    private static boolean isAndroid() {
        try {
            return Platform.isAndroid();
        } catch (Throwable t) {
            return false;
        }
    }
}
