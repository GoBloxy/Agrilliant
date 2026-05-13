package smartfarm.util;

/**
 * Tiny logging facade with an Android-style API.
 *
 * <p>The public surface mirrors {@code android.util.Log} so that
 * H10 can transparently route messages through Android's logcat
 * (via reflection) when running on a device, without touching call
 * sites. On desktop the messages go to {@link System#out} /
 * {@link System#err} prefixed with the tag and severity.
 *
 * <p>Usage:
 * <pre>
 *   Logger.i("SignIn", "user pressed login");
 *   Logger.e("DBConnection", "query failed", sqlEx);
 * </pre>
 *
 * <p>Level mapping (mirrors logcat):
 * <ul>
 *   <li>{@code d} — debug (verbose, dev only)</li>
 *   <li>{@code i} — info  (normal user-visible event)</li>
 *   <li>{@code w} — warn  (something unusual happened, app still works)</li>
 *   <li>{@code e} — error (something failed, surface to user)</li>
 * </ul>
 */
public final class Logger {

    private Logger() {}

    public static void d(String tag, String msg) {
        write(System.out, "D", tag, msg, null);
    }

    public static void i(String tag, String msg) {
        write(System.out, "I", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        write(System.err, "W", tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable t) {
        write(System.err, "W", tag, msg, t);
    }

    public static void e(String tag, String msg) {
        write(System.err, "E", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        write(System.err, "E", tag, msg, t);
    }

    // --------------------------------------------------------------------
    // Implementation
    //
    // Keep this routine intentionally small. H10 will overlay an
    // android.util.Log delegate (loaded via reflection so the desktop
    // build keeps compiling) — both paths share the same call sites.
    // --------------------------------------------------------------------

    private static void write(java.io.PrintStream stream,
                              String level, String tag, String msg, Throwable t) {
        // Format roughly matches "I/Tag: message" from logcat so logs
        // read consistently on both platforms.
        stream.println(level + "/" + tag + ": " + msg);
        if (t != null) t.printStackTrace(stream);
    }
}
