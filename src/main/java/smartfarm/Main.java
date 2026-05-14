package smartfarm;

import com.gluonhq.charm.glisten.application.MobileApplication;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.views.ShellView;
import smartfarm.ui.views.SignInView;
import smartfarm.ui.views.SignUpView;
import smartfarm.ui.views.SplashView;

/**
 * Gluon {@link MobileApplication} entry for both desktop and Android.
 *
 * <p>Registers 4 lazy View factories in {@link #init()}. {@code postInit(Scene)}
 * applies stylesheets and, on desktop only, configures window defaults and
 * starts the {@code FarmServer} TCP ingress (added in B1.10 / B1.11).
 *
 * <p>The desktop entry path is {@code smartfarm.Launcher} → {@code Main.main()};
 * the Android entry path is {@code smartfarm.Main} directly via GluonFX.
 */
public class Main extends MobileApplication {

    @Override
    public void init() {
        addViewFactory(AppView.SPLASH.name(), SplashView::new);
        addViewFactory(AppView.SIGNIN.name(), SignInView::new);
        addViewFactory(AppView.SIGNUP.name(), SignUpView::new);
        addViewFactory(AppView.SHELL.name(),  ShellView::new);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
