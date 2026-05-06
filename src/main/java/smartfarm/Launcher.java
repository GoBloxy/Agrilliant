package smartfarm;

/**
 * Non-JavaFX launcher class required for jpackage / fat JAR execution.
 * JavaFX Application.launch() fails when called directly from a shaded JAR
 * because the module system can't find the JavaFX runtime. This wrapper
 * class avoids that by not extending Application itself.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
