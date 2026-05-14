package smartfarm.ui.tools;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import smartfarm.ui.platform.PngEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * B7 — One-off Android launcher icon generator.
 *
 * <p>Reads {@code src/main/resources/images/logo.png} and renders ten
 * launcher PNGs covering all five Android density buckets in both the
 * standard and round variants:
 *
 * <pre>
 *   src/android/res/mipmap-mdpi/ic_launcher.png         (48 × 48)
 *   src/android/res/mipmap-mdpi/ic_launcher_round.png   (48 × 48)
 *   src/android/res/mipmap-hdpi/ic_launcher.png         (72 × 72)
 *   src/android/res/mipmap-hdpi/ic_launcher_round.png   (72 × 72)
 *   src/android/res/mipmap-xhdpi/ic_launcher.png        (96 × 96)
 *   src/android/res/mipmap-xhdpi/ic_launcher_round.png  (96 × 96)
 *   src/android/res/mipmap-xxhdpi/ic_launcher.png       (144 × 144)
 *   src/android/res/mipmap-xxhdpi/ic_launcher_round.png (144 × 144)
 *   src/android/res/mipmap-xxxhdpi/ic_launcher.png      (192 × 192)
 *   src/android/res/mipmap-xxxhdpi/ic_launcher_round.png(192 × 192)
 * </pre>
 *
 * <p>Each icon is the brand-green {@code #2e7d32} background (matching
 * {@code -color-accent-emphasis} in {@code farm-theme.css}) with the
 * source logo centred at ~72% of the icon's width, preserving aspect
 * ratio. The round variant is clipped to a circle so launchers that do
 * not auto-mask still display a proper round icon.
 *
 * <p>This is a <b>one-off dev tool</b>, not application code. It's never
 * invoked from {@code Main}; Android's launcher loads the produced PNGs
 * directly via the {@code @mipmap/...} references in
 * {@code AndroidManifest.xml}. Re-run only when the source logo or the
 * launcher styling changes.
 *
 * <h2>How to run</h2>
 *
 * <pre>
 *   # From the project root (where pom.xml lives):
 *   mvn -Pdesktop compile exec:java \
 *       -Dexec.mainClass=smartfarm.ui.tools.LauncherIconGenerator
 *
 *   # OR with a custom output root:
 *   mvn -Pdesktop compile exec:java \
 *       -Dexec.mainClass=smartfarm.ui.tools.LauncherIconGenerator \
 *       -Dexec.args="some/other/res"
 * </pre>
 *
 * <p>The tool writes paths <em>relative to the JVM's current working
 * directory</em>, so always run it from the project root. Output dirs
 * are created if missing. Commit the resulting PNGs.
 *
 * <p>Lane note: {@code smartfarm.ui.tools} is a subpath of
 * {@code smartfarm.ui.**}, which is 3bdelbary's §6 lane in
 * {@code ANDROID_MIGRATION.md}. The tool reads
 * {@code /images/logo.png} (3bdelbary lane) and writes under
 * {@code src/android/res/} — same parent as Hagag's
 * {@code AndroidManifest.xml}, but the icons themselves are 3bdelbary
 * content per §B7 of the migration doc.
 */
public class LauncherIconGenerator extends Application {

    /** Brand green; lifted from {@code -color-accent-emphasis} in
     *  farm-theme.css so the launcher icon matches the in-app palette. */
    private static final Color BACKGROUND = Color.web("#2e7d32");

    /** Logo box as a fraction of the icon edge. 0.72 leaves a ~14%
     *  margin on every side, which reads cleanly under Android's
     *  per-launcher mask without clipping the logo glyph. */
    private static final double LOGO_SCALE = 0.72;

    /** Android density bucket → target pixel size (square). */
    private static final int[]    DENSITIES_PX   = { 48, 72, 96, 144, 192 };
    private static final String[] DENSITY_NAMES  = { "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi" };

    private static final Path   DEFAULT_OUT_ROOT = Paths.get("src", "android", "res");
    private static final String SOURCE_LOGO     = "/images/logo.png";

    @Override
    public void start(Stage primaryStage) {
        try {
            run();
        } catch (Exception e) {
            System.err.println("LauncherIconGenerator failed: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
            // Non-zero exit so CI / scripts can detect failure.
            System.exit(1);
        }
        Platform.exit();
    }

    private void run() throws IOException {
        List<String> args = getParameters().getRaw();
        Path outRoot = args.isEmpty() ? DEFAULT_OUT_ROOT : Paths.get(args.get(0));

        Image logo = loadLogo();
        System.out.println("Source logo: " + SOURCE_LOGO
                + " (" + (int) logo.getWidth() + "×" + (int) logo.getHeight() + ")");
        System.out.println("Output root: " + outRoot.toAbsolutePath());
        System.out.println();

        int written = 0;
        for (int i = 0; i < DENSITIES_PX.length; i++) {
            int px = DENSITIES_PX[i];
            String density = DENSITY_NAMES[i];
            Path dir = outRoot.resolve("mipmap-" + density);
            Files.createDirectories(dir);

            Image square = renderIcon(logo, px, false);
            Path squarePath = dir.resolve("ic_launcher.png");
            Files.write(squarePath, PngEncoder.encode(square));

            Image round = renderIcon(logo, px, true);
            Path roundPath = dir.resolve("ic_launcher_round.png");
            Files.write(roundPath, PngEncoder.encode(round));

            written += 2;
            System.out.printf("  [%-8s %3d px] %s, %s%n",
                    density, px, squarePath.getFileName(), roundPath.getFileName());
        }

        System.out.println();
        System.out.println("Done. Wrote " + written + " icon files.");
    }

    private Image loadLogo() throws IOException {
        try (InputStream in = LauncherIconGenerator.class.getResourceAsStream(SOURCE_LOGO)) {
            if (in == null) {
                throw new IOException("Source logo not on classpath: " + SOURCE_LOGO
                        + " (run from the project root after `mvn compile`)");
            }
            Image img = new Image(in);
            if (img.isError()) {
                throw new IOException("Failed to decode " + SOURCE_LOGO + ": " + img.getException());
            }
            if (img.getWidth() <= 0 || img.getHeight() <= 0) {
                throw new IOException("Source logo has zero dimensions");
            }
            return img;
        }
    }

    /**
     * Builds an icon at the given pixel size. Background fills the
     * canvas in {@link #BACKGROUND}; logo is centred and scaled to fit
     * {@link #LOGO_SCALE} × edge while preserving aspect ratio. Round
     * variants get a circular clip via {@code fillOval} background +
     * {@code clip} on the graphics context.
     */
    private Image renderIcon(Image logo, int px, boolean circular) {
        Canvas canvas = new Canvas(px, px);
        GraphicsContext g = canvas.getGraphicsContext2D();

        // Background — fillOval gives an anti-aliased circle on the round variant.
        g.setFill(BACKGROUND);
        if (circular) {
            g.fillOval(0, 0, px, px);
        } else {
            g.fillRect(0, 0, px, px);
        }

        // Centred logo, preserve aspect ratio.
        double targetEdge = px * LOGO_SCALE;
        double srcW = logo.getWidth();
        double srcH = logo.getHeight();
        double scale = Math.min(targetEdge / srcW, targetEdge / srcH);
        double drawW = srcW * scale;
        double drawH = srcH * scale;
        double drawX = (px - drawW) / 2.0;
        double drawY = (px - drawH) / 2.0;
        g.drawImage(logo, drawX, drawY, drawW, drawH);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
