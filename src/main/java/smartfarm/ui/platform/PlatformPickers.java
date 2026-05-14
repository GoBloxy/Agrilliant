package smartfarm.ui.platform;

import com.gluonhq.attach.pictures.PicturesService;
import com.gluonhq.attach.storage.StorageService;
import com.gluonhq.attach.util.Services;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import smartfarm.util.Constants;
import smartfarm.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Cross-platform image picker for 3bdelbary's UI lane.
 *
 * <p>On desktop, falls back to {@link FileChooser#showOpenDialog(Window)}
 * so existing developer workflows are unchanged. On Android the
 * Gluon Attach {@code PicturesService} opens the system gallery picker;
 * because that API returns a JavaFX {@link Image} (not a path), the
 * bitmap is encoded via {@link PngEncoder} and persisted under the
 * app's private storage so callers that need a path (e.g. {@code
 * smartfarm.service.PlantIdService#analyzeImage(String)}) keep working
 * without contract changes.
 *
 * <p>Lane note: this class lives in {@code smartfarm.ui.platform},
 * which is 3bdelbary's lane per §6 of {@code ANDROID_MIGRATION.md}.
 * It read-only-imports {@code smartfarm.util.Constants} (H9) and
 * {@code smartfarm.util.Logger} (H5/H10), both Hagag-owned utilities.
 */
public final class PlatformPickers {

    private static final String TAG = "PlatformPickers";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private PlatformPickers() {}

    /**
     * Prompts the user to pick an image and returns the resulting file.
     *
     * <p>On Android, returns a PNG written to the app's private storage
     * (so the file is guaranteed readable by the same process). On
     * desktop, returns whatever the user picked through the JavaFX
     * {@link FileChooser}.
     *
     * @param owner the window to anchor the desktop chooser to. May be
     *              {@code null}; on Android it is ignored.
     * @return the picked file, or {@link Optional#empty()} if the user
     *         cancelled or the platform-specific picker failed.
     */
    public static Optional<File> pickImage(Window owner) {
        if (Constants.IS_ANDROID) {
            return pickImageAndroid();
        }
        return pickImageDesktop(owner);
    }

    // ---------------------------------------------------------------
    // Desktop branch
    // ---------------------------------------------------------------

    private static Optional<File> pickImageDesktop(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Crop Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files",
                        "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        return Optional.ofNullable(chooser.showOpenDialog(owner));
    }

    // ---------------------------------------------------------------
    // Android branch
    // ---------------------------------------------------------------

    private static Optional<File> pickImageAndroid() {
        Optional<PicturesService> svc;
        try {
            svc = Services.get(PicturesService.class);
        } catch (Throwable t) {
            Logger.e(TAG, "PicturesService unavailable", t);
            return Optional.empty();
        }
        if (svc.isEmpty()) {
            Logger.w(TAG, "PicturesService not registered on this platform");
            return Optional.empty();
        }
        Optional<Image> picked = svc.flatMap(PicturesService::loadImageFromGallery);
        if (picked.isEmpty()) {
            Logger.i(TAG, "User cancelled gallery pick or no image returned");
            return Optional.empty();
        }
        try {
            File out = writePng(picked.get());
            Logger.i(TAG, "Wrote picked image → " + out);
            return Optional.of(out);
        } catch (IOException e) {
            Logger.e(TAG, "Failed to persist picked image", e);
            return Optional.empty();
        }
    }

    private static File writePng(Image img) throws IOException {
        File dir = pickPrivateDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory " + dir);
        }
        File target = new File(dir, "picked_" + LocalDateTime.now().format(STAMP) + ".png");
        Files.write(target.toPath(), PngEncoder.encode(img));
        return target;
    }

    private static File pickPrivateDir() {
        try {
            Optional<File> priv = Services.get(StorageService.class)
                    .flatMap(StorageService::getPrivateStorage);
            if (priv.isPresent()) {
                return new File(priv.get(), "picked-images");
            }
        } catch (Throwable t) {
            Logger.w(TAG, "StorageService unavailable, falling back to tmpdir", t);
        }
        return new File(System.getProperty("java.io.tmpdir", "/tmp"), "agrilliant-picked");
    }
}
