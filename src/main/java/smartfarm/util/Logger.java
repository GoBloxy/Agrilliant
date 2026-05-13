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
 * <p>Method handles are resolved <em>once</em> at class load — every
 * subsequent call is a {@code null} check + a single
 * {@link Method#invoke}, no {@code getMethod} lookups in the hot path.
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
        if (!route(LOG_D, tag, msg, null)) write(System.out, "D", tag, msg, null);
    }

    public static void i(String tag, String msg) {
        if (!route(LOG_I, tag, msg, null)) write(System.out, "I", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        if (!route(LOG_W, tag, msg, null)) write(System.err, "W", tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable t) {
        if (!route(LOG_W_T, tag, msg, t)) write(System.err, "W", tag, msg, t);
    }

    public static void e(String tag, String msg) {
        if (!route(LOG_E, tag, msg, null)) write(System.err, "E", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        if (!route(LOG_E_T, tag, msg, t)) write(System.err, "E", tag, msg, t);
    }

    // --------------------------------------------------------------------
    // Android routing — Method handles cached at class load.
    // --------------------------------------------------------------------

    private static final Method LOG_D   = lookup2("d");
    private static final Method LOG_I   = lookup2("i");
    private static final Method LOG_W   = lookup2("w");
    private static final Method LOG_E   = lookup2("e");
    private static final Method LOG_W_T = lookup3("w");
    private static final Method LOG_E_T = lookup3("e");

    private static Method lookup2(String name) {
        try {
            return Class.forName("android.util.Log")
                    .getMethod(name, String.class, String.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method lookup3(String name) {
        try {
            return Class.forName("android.util.Log")
                    .getMethod(name, String.class, String.class, Throwable.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Dispatches a log line through a cached {@code android.util.Log}
     * method. Returns {@code true} when the call went through (so the
     * caller skips the System.out fallback), {@code false} otherwise.
     */
    private static boolean route(Method m, String tag, String msg, Throwable t) {
        if (m == null) return false;
        try {
            if (t == null) m.invoke(null, tag, msg);
            else           m.invoke(null, tag, msg, t);
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
