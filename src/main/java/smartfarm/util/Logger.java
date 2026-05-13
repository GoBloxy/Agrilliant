package smartfarm.util;

import java.io.PrintStream;
import java.lang.reflect.Method;

/**
 * Tiny logging facade with an Android-style API.
 *
 * <p>On Android the calls are routed through {@code android.util.Log}
 * via reflection so they show up in {@code adb logcat} just like
 * native Android logs. On desktop (or any JVM where
 * {@code android.util.Log} isn't on the classpath) they fall back to
 * {@link System#out} / {@link System#err} formatted as
 * {@code "<LEVEL>/<tag>: <msg>"} (matching the logcat shape so logs
 * read the same on both platforms).
 *
 * <p>Reflection (not a direct {@code import android.util.Log}) is used
 * on purpose — that way the desktop Maven build keeps compiling
 * cleanly without an Android SDK on the classpath, and the AOT
 * compile on Android can wire up the real class at runtime.
 *
 * <p>Usage:
 * <pre>
 *   Logger.i("SignIn", "user pressed login");
 *   Logger.e("DBConnection", "query failed", sqlEx);
 * </pre>
 */
public final class Logger {

    private Logger() {}

    // --------------------------------------------------------------------
    // Public API — Android logcat-style levels.
    // --------------------------------------------------------------------

    public static void d(String tag, String msg) {
        if (!routeToAndroid("d", tag, msg, null)) {
            write(System.out, "D", tag, msg, null);
        }
    }

    public static void i(String tag, String msg) {
        if (!routeToAndroid("i", tag, msg, null)) {
            write(System.out, "I", tag, msg, null);
        }
    }

    public static void w(String tag, String msg) {
        if (!routeToAndroid("w", tag, msg, null)) {
            write(System.err, "W", tag, msg, null);
        }
    }

    public static void w(String tag, String msg, Throwable t) {
        if (!routeToAndroid("w", tag, msg, t)) {
            write(System.err, "W", tag, msg, t);
        }
    }

    public static void e(String tag, String msg) {
        if (!routeToAndroid("e", tag, msg, null)) {
            write(System.err, "E", tag, msg, null);
        }
    }

    public static void e(String tag, String msg, Throwable t) {
        if (!routeToAndroid("e", tag, msg, t)) {
            write(System.err, "E", tag, msg, t);
        }
    }

    // --------------------------------------------------------------------
    // Android routing — reflection so the desktop build stays clean.
    // --------------------------------------------------------------------

    /** {@code android.util.Log} class — non-null when running under
     *  the Android runtime, null on every desktop JVM. Captured once. */
    private static final Class<?> ANDROID_LOG = lookupAndroidLogClass();

    private static Class<?> lookupAndroidLogClass() {
        try {
            return Class.forName("android.util.Log");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Attempts to dispatch a log line through {@code android.util.Log}.
     * Returns {@code true} when the call went through (so the caller
     * skips the System.out fallback), {@code false} otherwise.
     */
    private static boolean routeToAndroid(String level, String tag, String msg, Throwable t) {
        if (ANDROID_LOG == null) return false;
        try {
            if (t == null) {
                Method m = ANDROID_LOG.getMethod(level, String.class, String.class);
                m.invoke(null, tag, msg);
            } else {
                Method m = ANDROID_LOG.getMethod(level, String.class, String.class, Throwable.class);
                m.invoke(null, tag, msg, t);
            }
            return true;
        } catch (Throwable invocation) {
            // android.util.Log refused our args. Don't loop into
            // ourselves — just fall back to the stream writer below.
            return false;
        }
    }

    // --------------------------------------------------------------------
    // Desktop fallback writer.
    // --------------------------------------------------------------------

    private static void write(PrintStream stream,
                              String level, String tag, String msg, Throwable t) {
        stream.println(level + "/" + tag + ": " + msg);
        if (t != null) t.printStackTrace(stream);
    }
}
