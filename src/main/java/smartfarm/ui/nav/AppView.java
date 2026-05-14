package smartfarm.ui.nav;

import com.gluonhq.charm.glisten.application.AppManager;

/**
 * Type-safe identifiers for the 4 top-level Views registered with Gluon's
 * {@link AppManager}.
 *
 * <p>Use {@link #switchTo()} as the idiomatic way to navigate:
 * <pre>AppView.SHELL.switchTo();</pre>
 */
public enum AppView {
    SPLASH,
    SIGNIN,
    SIGNUP,
    SHELL;

    /** Switch the current AppManager view to this one. */
    public void switchTo() {
        AppManager.getInstance().switchView(name());
    }
}
