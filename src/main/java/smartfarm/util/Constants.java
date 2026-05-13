package smartfarm.util;

import com.gluonhq.attach.util.Platform;

/**
 * Cross-platform runtime constants used by code that compiles for both
 * desktop and Android but needs to differ in behaviour at runtime.
 *
 * <p>Lives in {@code smartfarm.util} (Hagag's lane) so any caller can
 * read it without pulling in Gluon Attach directly — the Attach
 * lookup is cached in a static final boolean below, then platform
 * checks elsewhere in the app become a single field reference.
 */
public final class Constants {

    private Constants() {}

    /**
     * {@code true} when this process is running on Android (Gluon Attach
     * reports {@code Platform.ANDROID}). {@code false} on every desktop
     * OS, including a desktop JVM where Attach jars are missing.
     *
     * <p>Typical use — gate a desktop-only side effect from a class
     * that compiles for both targets:
     * <pre>
     *   if (!Constants.IS_ANDROID) {
     *       Thread t = new Thread(() -&gt; new FarmServer().start());
     *       t.setDaemon(true);
     *       t.start();
     *   }
     * </pre>
     *
     * <p>Once 3bdelbary's B1 drops the {@code import
     * smartfarm.server.FarmServer;} from {@code Main.java}, the
     * matching {@code maven-compiler-plugin} exclude in {@code pom.xml}'s
     * {@code android} profile fully takes effect and the AOT compile
     * stops emitting {@code FarmServer.class}. Until then, javac's
     * implicit-compile (driven by Main's import) keeps the class on
     * the Android classpath even though the exclude is configured —
     * this runtime gate is what stops it from actually running.
     */
    public static final boolean IS_ANDROID = detectAndroid();

    private static boolean detectAndroid() {
        try {
            return Platform.isAndroid();
        } catch (Throwable t) {
            // Attach isn't available (e.g. pre-GluonFX dev environment).
            // Treat as not-Android — the file-based / desktop paths are
            // a safe default everywhere else in the codebase.
            return false;
        }
    }
}
