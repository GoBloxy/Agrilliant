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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * CSV serialization + cross-platform "save to disk" for dashboard
 * exports.
 *
 * <p>The pure formatting functions {@link #exportSensorData(List)} and
 * {@link #exportHarvestData(List)} keep the same signatures they had
 * pre-H8. Field values are now RFC-4180 quoted when they contain a
 * comma, double quote, or newline.
 *
 * <p>{@link #saveCsv(String, String)} is the H8 addition. It picks an
 * appropriate target directory based on the platform:
 * <ul>
 *   <li><b>Android</b> — via Gluon Attach
 *       {@code StorageService.getPublicStorage("Documents")}. The file
 *       lands in the OS-visible public Documents folder so the user
 *       can find it through any file manager. Falls back to the
 *       app-private storage if no public storage location is reported
 *       by the service (rare SD-card scenarios).</li>
 *   <li><b>Desktop</b> — writes into {@code ~/Downloads/}, creating
 *       the directory if it doesn't exist.</li>
 * </ul>
 *
 * <p>The supplied {@code suggestedName} is sanitized: any path
 * components are stripped (so a hostile caller can't escape the chosen
 * directory). If a file with the resulting name already exists, a
 * timestamp suffix ({@code "_yyyyMMdd-HHmmss"}) is inserted before
 * the extension so the previous export isn't silently overwritten.
 *
 * <p>The method returns the final {@link File} so callers can show a
 * "Saved to ..." toast / dialog with the actual path.
 */
public class CSVExporter {

    private static final String TAG = "CSVExporter";

    /** Single Storage lookup, cached at class load so we don't re-run
     *  the service factory (and re-emit its warning) per call. */
    private static final Optional<StorageService> STORAGE = lookupStorage();

    private static Optional<StorageService> lookupStorage() {
        try {
            return Services.get(StorageService.class);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private CSVExporter() {}

    // ---------------------------------------------------------------------
    // Pure CSV serialization (signatures unchanged from pre-H8).
    // ---------------------------------------------------------------------

    public static String exportSensorData(List<SensorReading> readings) {
        if (readings == null || readings.isEmpty()) return "";

        StringBuilder csv = new StringBuilder("Timestamp,DeviceID,Temperature,Humidity\n");
        for (SensorReading r : readings) {
            appendCsvField(csv, r.getTimestamp());     csv.append(',');
            appendCsvField(csv, r.getDeviceId());      csv.append(',');
            appendCsvField(csv, r.getTemperature());   csv.append(',');
            appendCsvField(csv, r.getHumidity());
            csv.append('\n');
        }
        return csv.toString();
    }

    public static String exportHarvestData(List<HarvestRecord> records) {
        if (records == null || records.isEmpty()) return "";

        StringBuilder csv = new StringBuilder("Date,CropID,QuantityKg,Grade\n");
        for (HarvestRecord r : records) {
            appendCsvField(csv, r.getHarvestDate()); csv.append(',');
            appendCsvField(csv, r.getCropId());      csv.append(',');
            appendCsvField(csv, r.getQuantityKg());  csv.append(',');
            appendCsvField(csv, r.getGrade());
            csv.append('\n');
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
     *                      {@code "harvest_2026.csv"}. Path components
     *                      (e.g. {@code "../"}) are stripped. If a file
     *                      with this name already exists, a timestamp
     *                      suffix is appended before the extension.
     * @return the written file (already on disk).
     * @throws IOException if the file system rejected the write.
     */
    public static File saveCsv(String content, String suggestedName) throws IOException {
        String safeName = sanitizeFilename(suggestedName);
        File parent = pickSaveDirectory();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent);
        }
        File target = uniqueChild(parent, safeName);
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
        Logger.i(TAG, "Wrote " + content.length() + " bytes → " + target);
        return target;
    }

    // ---------------------------------------------------------------------
    // Helpers — exposed package-private only for whatever exercises the
    // smoke harness lives in next to this class.
    // ---------------------------------------------------------------------

    /** Picks the directory the next {@link #saveCsv(String, String)}
     *  call will write into. */
    static File pickSaveDirectory() {
        if (isAndroid()) {
            Optional<File> publicDir = STORAGE.flatMap(svc -> svc.getPublicStorage("Documents"));
            if (publicDir.isPresent()) return publicDir.get();
            Logger.w(TAG, "Public Documents storage unavailable on Android — falling back to private storage");
            Optional<File> priv = STORAGE.flatMap(StorageService::getPrivateStorage);
            if (priv.isPresent()) return priv.get();
            // Last-ditch — should never happen on a real Android device.
            return new File(System.getProperty("java.io.tmpdir", "/tmp"));
        }
        // Desktop: ~/Downloads (per plan §H8).
        return new File(System.getProperty("user.home"), "Downloads");
    }

    /** Strips any path component from {@code name}; falls back to a
     *  generic timestamped name if the input is null/blank/all-path. */
    static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "export_" + LocalDateTime.now().format(TIMESTAMP_FMT) + ".csv";
        }
        // Take only the trailing path component (handles ../, foo/bar,
        // C:\foo\bar — File handles all of these uniformly).
        String basename = new File(name.trim()).getName();
        if (basename.isBlank() || ".".equals(basename) || "..".equals(basename)) {
            return "export_" + LocalDateTime.now().format(TIMESTAMP_FMT) + ".csv";
        }
        return basename;
    }

    /** If {@code parent/name} doesn't exist, returns it. Otherwise
     *  inserts a {@code _yyyyMMdd-HHmmss} suffix before the extension
     *  so the existing file is left intact. */
    static File uniqueChild(File parent, String name) {
        File first = new File(parent, name);
        if (!first.exists()) return first;

        String stamped;
        int dot = name.lastIndexOf('.');
        String suffix = "_" + LocalDateTime.now().format(TIMESTAMP_FMT);
        if (dot <= 0) {
            stamped = name + suffix;
        } else {
            stamped = name.substring(0, dot) + suffix + name.substring(dot);
        }
        return new File(parent, stamped);
    }

    /** RFC-4180-ish field formatting: stringify; if the result contains
     *  comma, double quote, or newline, double-up internal quotes and
     *  wrap the whole field in double quotes. */
    private static void appendCsvField(StringBuilder out, Object value) {
        String s = value == null ? "" : value.toString();
        if (s.indexOf(',')  < 0
                && s.indexOf('"')  < 0
                && s.indexOf('\n') < 0
                && s.indexOf('\r') < 0) {
            out.append(s);
            return;
        }
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') out.append('"').append('"');
            else          out.append(c);
        }
        out.append('"');
    }

    /** {@code true} if Gluon Attach reports we're on Android. Safe to call
     *  even when Attach isn't on the classpath (we just return false). */
    private static boolean isAndroid() {
        try {
            return Platform.isAndroid();
        } catch (Throwable t) {
            return false;
        }
    }

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
}
