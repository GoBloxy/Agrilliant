package smartfarm.ui.views;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.mvc.View;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import smartfarm.model.User;
import smartfarm.service.AuthService;
import smartfarm.service.SessionManager;
import smartfarm.ui.async.AsyncCalls;
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

        final long start = System.currentTimeMillis();

        // P2.4: runs SessionManager.loadSession() + AuthService.restoreSession()
        // on the shared DB executor instead of a one-off raw Thread. The 5s
        // timeout means a hung JDBC connection no longer freezes the splash
        // indefinitely — the user falls through to the sign-in screen and
        // can retry. The minimum splash visible time is enforced on the FX
        // thread via PauseTransition rather than Thread.sleep.
        AsyncCalls.runAndApply(
                () -> {
                    String email = SessionManager.loadSession();
                    return email == null ? null : new AuthService().restoreSession(email);
                },
                user -> scheduleAfterMinVisible(start, () -> {
                    if (user != null) {
                        Logger.d("SplashView", "session restored → SHELL");
                        NavContext.get().setCurrentUser(user);
                        AppView.SHELL.switchTo();
                    } else {
                        Logger.d("SplashView", "no saved session → SIGNIN");
                        AppView.SIGNIN.switchTo();
                    }
                }),
                err -> scheduleAfterMinVisible(start, () -> {
                    if (err instanceof TimeoutException) {
                        Logger.d("SplashView", "session restore timed out → SIGNIN");
                    } else {
                        Logger.d("SplashView", "session restore failed (" + err.getClass().getSimpleName() + "): " + err.getMessage());
                    }
                    SessionManager.clearSession();
                    AppView.SIGNIN.switchTo();
                }),
                Duration.ofSeconds(5));
    }

    /**
     * Defers {@code navigate} until at least {@link #MIN_VISIBLE_MS} has
     * elapsed since {@code start}, scheduled on the FX thread via
     * {@link PauseTransition}. Both the success and error/timeout paths
     * route through this so the splash brand always shows for a minimum
     * amount of time even when the restore returns instantly.
     */
    private void scheduleAfterMinVisible(long start, Runnable navigate) {
        long elapsed = System.currentTimeMillis() - start;
        long remaining = MIN_VISIBLE_MS - elapsed;
        if (remaining <= 0) {
            navigate.run();
            return;
        }
        PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(remaining));
        pt.setOnFinished(e -> navigate.run());
        pt.play();
    }
}
