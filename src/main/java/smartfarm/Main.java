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
import smartfarm.util.Logger;

/**
 * Gluon {@link MobileApplication} entry for both desktop and Android.
 *
 * <p>Registers 4 lazy View factories in {@link #init()}.
 * {@link #postInit(Scene)} applies stylesheets and, on desktop only,
 * configures window defaults and starts the {@code FarmServer} TCP
 * ingress via reflection.
 *
 * <p>The reflective load avoids a compile-time dependency on
 * {@code smartfarm.server.FarmServer}, which Hagag's H9 excludes from
 * the Android Maven profile's compile classpath. Combined with the
 * {@code !Constants.IS_ANDROID} runtime guard, this means:
 * <ul>
 *   <li>On desktop: the class is on the classpath, the guard passes,
 *       the TCP listener starts in a daemon thread.</li>
 *   <li>On Android: even if the class somehow ends up on the classpath
 *       (e.g. through javac implicit-compile leakage), the guard
 *       short-circuits and the listener never starts — Android
 *       background restrictions would kill the socket anyway.</li>
 * </ul>
 */
public class Main extends MobileApplication {

    private static final String TAG = "Main";

    @Override
    public void init() {
        // SPLASH registers under HOME_VIEW ("home") so Glisten mounts it at startup.
        // See AppView's javadoc for the contract.
        addViewFactory(AppView.SPLASH.registeredName(), SplashView::new);
        addViewFactory(AppView.SIGNIN.registeredName(), SignInView::new);
        addViewFactory(AppView.SIGNUP.registeredName(), SignUpView::new);
        addViewFactory(AppView.SHELL.registeredName(),  ShellView::new);
    }

    @Override
    public void postInit(Scene scene) {
        // Stylesheets — both targets
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        scene.getStylesheets().add(
                getClass().getResource("/css/farm-theme.css").toExternalForm());

        if (Constants.IS_ANDROID) {
            // Android-only: touch-target overrides + larger base font (B5).
            // Loaded after farm-theme.css so its size rules win over the
            // dashboard-optimized defaults without changing the desktop UX.
            scene.getStylesheets().add(
                    getClass().getResource("/css/mobile.css").toExternalForm());
        } else {
            // Desktop-only: window setup + FarmServer
            applyDesktopWindowDefaults(scene);
            startFarmServerReflectively();
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

    /**
     * Starts the ESP32 ingress server on desktop builds only.
     *
     * <p>Reflective load avoids a compile-time dependency on
     * {@code smartfarm.server.FarmServer}, which Hagag's H9 excludes
     * from the Android Maven profile. The outer
     * {@code !Constants.IS_ANDROID} check in {@link #postInit(Scene)}
     * is the runtime gate; this method is the no-static-import bridge.
     */
    private void startFarmServerReflectively() {
        try {
            Class<?> cls = Class.forName("smartfarm.server.FarmServer");
            Object server = cls.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method start = cls.getMethod("start");

            Thread t = new Thread(() -> {
                try {
                    start.invoke(server);
                } catch (Exception e) {
                    Logger.e(TAG, "FarmServer crashed", e);
                }
            }, "FarmServer-TCP");
            t.setDaemon(true);
            t.start();
        } catch (ClassNotFoundException e) {
            // Expected on Android build — class excluded from compile.
            Logger.i(TAG, "FarmServer class absent — skipping TCP ingress");
        } catch (Exception e) {
            Logger.e(TAG, "Could not start FarmServer", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
