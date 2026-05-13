package smartfarm.service;

import smartfarm.util.Logger;

import java.lang.reflect.Constructor;

/**
 * Thin facade over the fingerprint reader.
 *
 * <p>The class compiles unchanged on both desktop and Android builds.
 * The actual hardware impl ({@code FingerprintServiceDesktop}) lives in
 * {@code smartfarm.service.desktop} and depends on {@code jSerialComm},
 * which does not link on Android — the Maven {@code android} profile
 * excludes that package from compile, so the class isn't on the device.
 *
 * <p>The desktop backend's no-arg constructor is resolved <em>once</em>
 * at class load via {@link Class#forName} + {@link Class#getDeclaredConstructor}
 * and cached in {@link #DESKTOP_BACKEND_CTOR}. Each {@code new
 * FingerprintService()} just instantiates from the cached constructor
 * (or returns the no-op stub if the desktop class is absent), so
 * Android startup doesn't re-emit the "running in stub mode" warning
 * once per controller.
 *
 * <p>If the desktop class is missing (Android, or a desktop build
 * where the desktop sub-package failed to load), every method falls
 * through to a no-op stub that returns {@code false} / {@code -1} /
 * {@code "N/A"}. Callers get the exact same public surface they had
 * before the Android migration — see {@link Backend} for the
 * per-method contract.
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

    /** Listing serial ports is a static convenience; uses the cached
     *  backend constructor so we don't pay reflection cost per call. */
    public static String[] getAvailablePorts() {
        return createBackend().listAvailablePorts();
    }

    // ---------------------------------------------------------------------
    // Backend selection — Constructor handle cached at class load,
    // stub-mode warning logged once.
    // ---------------------------------------------------------------------

    /** Cached no-arg constructor of the desktop backend. {@code null}
     *  on Android or wherever the desktop class is absent. */
    private static final Constructor<? extends Backend> DESKTOP_BACKEND_CTOR =
            resolveDesktopCtor();

    @SuppressWarnings("unchecked")
    private static Constructor<? extends Backend> resolveDesktopCtor() {
        try {
            Class<?> cls = Class.forName(DESKTOP_IMPL);
            if (!Backend.class.isAssignableFrom(cls)) {
                Logger.w(TAG, DESKTOP_IMPL + " is on the classpath but doesn't implement Backend — using stub");
                return null;
            }
            return (Constructor<? extends Backend>) cls.getDeclaredConstructor();
        } catch (ClassNotFoundException notHere) {
            // Expected on Android (where the desktop package is excluded
            // from compile). Single-line warning at class load — not
            // once per `new FingerprintService()`.
            Logger.w(TAG, "Fingerprint hardware not available — running in stub mode");
            return null;
        } catch (Throwable t) {
            Logger.w(TAG, "Fingerprint backend failed to start — running in stub mode", t);
            return null;
        }
    }

    private static Backend createBackend() {
        if (DESKTOP_BACKEND_CTOR == null) return NULL_BACKEND;
        try {
            return DESKTOP_BACKEND_CTOR.newInstance();
        } catch (Throwable t) {
            // Constructor failed at runtime (rare — would have to be a
            // post-class-load error like a DLL not loadable). Log per
            // call here; this is genuinely unexpected.
            Logger.w(TAG, "Fingerprint backend instance failed — using stub for this call", t);
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
