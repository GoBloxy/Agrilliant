package smartfarm.ui.views;

import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.mvc.View;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Splash screen + session-restore router. Set as HOME_VIEW.
 *
 * <p>On show: hides the Gluon AppBar, then kicks off the async session
 * restore. On hide: restores the AppBar.
 *
 * <p>Routes to {@link smartfarm.ui.nav.AppView#SHELL} if a session was
 * restored, otherwise to {@link smartfarm.ui.nav.AppView#SIGNIN}.
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
            AppManager.getInstance().getAppBar().setVisible(false);
            // Session restore logic added in B1.8
        });
        setOnHiding(e -> AppManager.getInstance().getAppBar().setVisible(true));
    }
}
