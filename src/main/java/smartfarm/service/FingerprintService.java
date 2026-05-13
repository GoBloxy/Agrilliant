package smartfarm.service;

import smartfarm.util.Logger;

/**
 * Thin facade over the fingerprint reader.
 *
 * <p>The class compiles unchanged on both desktop and Android builds.
 * The actual hardware impl ({@code FingerprintServiceDesktop}) lives in
 * {@code smartfarm.service.desktop} and depends on {@code jSerialComm},
 * which does not link on Android — the Maven {@code android} profile
 * excludes that package from compile, so the class isn't on the device.
 *
 * <p>This facade resolves the backend lazily via {@link Class#forName}.
 * If the desktop class is missing (Android, or a desktop build with
 * the hardware deliberately disabled), every method falls through to a
 * no-op stub that returns {@code false} / {@code -1} / {@code "N/A"}
 * and logs a single startup warning. Callers get the exact same public
 * surface they had before the Android migration — see {@link Backend}
 * for the per-method contract.
 *
 * <p>Public API is unchanged from the pre-H7 implementation:
 * <pre>
 *   FingerprintService fps = new FingerprintService();
 *   if (fps.autoConnect()) {
 *       int id = fps.scanAndMatch();
 *       fps.disconnect();
 *   }
 * </pre>
 */
public final class FingerprintService {

    private static final String TAG = "FingerprintService";
    private static final String DESKTOP_IMPL =
            "smartfarm.service.desktop.FingerprintServiceDesktop";

    /** Backend SPI. Implemented by the desktop class via reflection.
     *  Public because the desktop class lives in another package. */
    public interface Backend {
        boolean connect(String portName);
        boolean autoConnect();
        void disconnect();
        boolean isConnected();
        String getPortName();
        int scanAndMatch();
        int getTemplateCount();
        boolean deleteTemplate(int slotId);
        boolean enroll(int slotId, EnrollCallback callback);
        String[] listAvailablePorts();
    }

    /** Progress/status messages emitted during enrollment. Same SAM
     *  type the controllers have always used. */
    public interface EnrollCallback {
        void onStatus(String message);
    }

    private final Backend backend;

    public FingerprintService() {
        this.backend = createBackend();
    }

    // ---------------------------------------------------------------------
    // Public API — every method just forwards to the chosen backend.
    // ---------------------------------------------------------------------

    public boolean connect(String portName)        { return backend.connect(portName); }
    public boolean autoConnect()                   { return backend.autoConnect(); }
    public void    disconnect()                    { backend.disconnect(); }
    public boolean isConnected()                   { return backend.isConnected(); }
    public String  getPortName()                   { return backend.getPortName(); }
    public int     scanAndMatch()                  { return backend.scanAndMatch(); }
    public int     getTemplateCount()              { return backend.getTemplateCount(); }
    public boolean deleteTemplate(int slotId)      { return backend.deleteTemplate(slotId); }
    public boolean enroll(int slotId, EnrollCallback cb) { return backend.enroll(slotId, cb); }

    /** Listing serial ports is a static convenience; spins up a
     *  temporary backend just to ask. */
    public static String[] getAvailablePorts() {
        return createBackend().listAvailablePorts();
    }

    // ---------------------------------------------------------------------
    // Backend selection
    // ---------------------------------------------------------------------

    private static Backend createBackend() {
        try {
            Class<?> cls = Class.forName(DESKTOP_IMPL);
            return (Backend) cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException notHere) {
            // Expected on Android (where the desktop package is excluded
            // from compile). Single-line warning, not a stack trace.
            Logger.w(TAG, "Fingerprint hardware not available — running in stub mode");
            return NULL_BACKEND;
        } catch (Throwable t) {
            // Class is on the classpath but couldn't be instantiated
            // (e.g. jSerialComm native lib missing on this OS). Treat
            // as "not available" so the rest of the app keeps working.
            Logger.w(TAG, "Fingerprint backend failed to start — running in stub mode", t);
            return NULL_BACKEND;
        }
    }

    /** Stub used when the real backend is absent. All methods are
     *  no-ops so caller code paths that gate on {@code isConnected()}
     *  simply fail fast. */
    private static final Backend NULL_BACKEND = new Backend() {
        @Override public boolean connect(String portName)   { return false; }
        @Override public boolean autoConnect()              { return false; }
        @Override public void    disconnect()               { /* no-op */ }
        @Override public boolean isConnected()              { return false; }
        @Override public String  getPortName()              { return "N/A"; }
        @Override public int     scanAndMatch()             { return -1; }
        @Override public int     getTemplateCount()         { return -1; }
        @Override public boolean deleteTemplate(int s)      { return false; }
        @Override public boolean enroll(int s, EnrollCallback cb) {
            if (cb != null) cb.onStatus("Fingerprint hardware not available on this device.");
            return false;
        }
        @Override public String[] listAvailablePorts()      { return new String[0]; }
    };
}
