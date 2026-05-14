package smartfarm.ui.nav;

import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.application.MobileApplication;

/**
 * Type-safe identifiers for the 4 top-level Views registered with Gluon's
 * {@link AppManager}.
 *
 * <p>Use {@link #switchTo()} as the idiomatic way to navigate:
 * <pre>AppView.SHELL.switchTo();</pre>
 *
 * <p><b>Registration contract.</b> Glisten's {@code AppManager.continueInit}
 * calls {@code switchView("home")} at startup to mount whichever View was
 * registered under {@link MobileApplication#HOME_VIEW} ({@code "home"}).
 * {@link #SPLASH} therefore uses {@code HOME_VIEW} as its registered name
 * — the result is that {@link smartfarm.ui.views.SplashView} mounts
 * automatically when the app launches, without {@link smartfarm.Main}
 * needing to call {@code switchView} explicitly. The other three Views
 * use their enum names ({@code "SIGNIN"}, {@code "SIGNUP"}, {@code "SHELL"})
 * since they're only ever navigated to via {@link #switchTo()}.
 */
public enum AppView {
    SPLASH(MobileApplication.HOME_VIEW),
    SIGNIN("SIGNIN"),
    SIGNUP("SIGNUP"),
    SHELL("SHELL");

    private final String registeredName;

    AppView(String registeredName) {
        this.registeredName = registeredName;
    }

    /** Name under which this View's factory is registered with the AppManager. */
    public String registeredName() {
        return registeredName;
    }

    /** Switch the current AppManager view to this one. */
    public void switchTo() {
        AppManager.getInstance().switchView(registeredName);
    }
}
