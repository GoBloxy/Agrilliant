package smartfarm.ui.views;

import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.mvc.View;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import smartfarm.model.User;
import smartfarm.service.AuthService;
import smartfarm.service.SessionManager;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.nav.NavContext;
import smartfarm.util.Logger;

/**
 * Splash screen + session-restore router. Set as HOME_VIEW.
 *
 * <p>On show: hides the Gluon AppBar, then kicks off the async session
 * restore. On hide: restores the AppBar.
 *
 * <p>Routes to {@link AppView#SHELL} if a session was restored,
 * otherwise to {@link AppView#SIGNIN}.
 *
 * <p>Honours a minimum 800 ms visible time so the brand is visible
 * even on a hot-DB cold start.
 */
public class SplashView extends View {

    static final long MIN_VISIBLE_MS = 800;

    private volatile boolean restoreStarted = false;

    public SplashView() {
        ImageView logo = new ImageView(
                new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        logo.setFitWidth(200);
        logo.setPreserveRatio(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        VBox box = new VBox(24, logo, spinner, new Label("Loading…"));
        box.setAlignment(Pos.CENTER);
        setCenter(box);

        setOnShowing(e -> {
            Logger.d("SplashView", "showing");
            AppManager.getInstance().getAppBar().setVisible(false);
            kickOffSessionRestore();
        });
        setOnHiding(e -> {
            Logger.d("SplashView", "hiding");
            AppManager.getInstance().getAppBar().setVisible(true);
        });
    }

    private void kickOffSessionRestore() {
        if (restoreStarted) {
            return; // guard: only run once per JVM
        }
        restoreStarted = true;

        long start = System.currentTimeMillis();

        Thread t = new Thread(() -> {
            User restored = null;
            String savedEmail = SessionManager.loadSession();
            if (savedEmail != null) {
                try {
                    restored = new AuthService().restoreSession(savedEmail);
                } catch (Exception ex) {
                    SessionManager.clearSession();
                }
            }

            // Honour minimum splash visible time
            long elapsed = System.currentTimeMillis() - start;
            long remaining = MIN_VISIBLE_MS - elapsed;
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            final User finalUser = restored;
            Platform.runLater(() -> {
                if (finalUser != null) {
                    Logger.d("SplashView", "session restored → SHELL");
                    NavContext.get().setCurrentUser(finalUser);
                    AppView.SHELL.switchTo();
                } else {
                    Logger.d("SplashView", "no session → SIGNIN");
                    AppView.SIGNIN.switchTo();
                }
            });
        }, "splash-session-restore");
        t.setDaemon(true);
        t.start();
    }
}
