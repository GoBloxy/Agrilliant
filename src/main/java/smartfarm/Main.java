package smartfarm;

import atlantafx.base.theme.PrimerLight;
import com.gluonhq.charm.glisten.application.MobileApplication;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.views.ShellView;
import smartfarm.ui.views.SignInView;
import smartfarm.ui.views.SignUpView;
import smartfarm.ui.views.SplashView;
import smartfarm.util.Constants;

/**
 * Gluon {@link MobileApplication} entry for both desktop and Android.
 *
 * <p>Registers 4 lazy View factories in {@link #init()}.
 * {@link #postInit(Scene)} applies stylesheets and, on desktop only,
 * configures window defaults. B1.11 adds the reflective {@code FarmServer}
 * launch.
 */
public class Main extends MobileApplication {

    @Override
    public void init() {
        addViewFactory(AppView.SPLASH.name(), SplashView::new);
        addViewFactory(AppView.SIGNIN.name(), SignInView::new);
        addViewFactory(AppView.SIGNUP.name(), SignUpView::new);
        addViewFactory(AppView.SHELL.name(),  ShellView::new);
    }

    @Override
    public void postInit(Scene scene) {
        // Stylesheets — both targets
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        scene.getStylesheets().add(
                getClass().getResource("/css/farm-theme.css").toExternalForm());
        // mobile.css added in B5

        // Desktop-only: window setup
        if (!Constants.IS_ANDROID) {
            applyDesktopWindowDefaults(scene);
        }
    }

    private void applyDesktopWindowDefaults(Scene scene) {
        Stage stage = (Stage) scene.getWindow();
        stage.setTitle("Agrilliant — Smart Farm Management System");
        stage.getIcons().add(
                new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        stage.setMinWidth(1300);
        stage.setMinHeight(820);
        stage.setMaximized(true);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
