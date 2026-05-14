package smartfarm.ui.views;

import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import smartfarm.model.User;
import smartfarm.ui.DashboardController;
import smartfarm.ui.nav.NavContext;
import smartfarm.util.Logger;

import java.io.IOException;

/**
 * Post-login shell: wraps the existing {@code dashboard.fxml}.
 *
 * <p>The Gluon AppBar is hidden while the shell is on screen — the
 * dashboard FXML has its own sidebar + top bar that already provide
 * the navigation affordances. (Phase 2 / B3 may switch to a Gluon
 * {@code NavigationDrawer} instead, at which point the AppBar can
 * come back; for B2 we keep the existing chrome.)
 *
 * <p>The current user is read from {@link NavContext} on every
 * {@code onShowing} and pushed into {@link DashboardController#setCurrentUser(User)}
 * so the dashboard refreshes if the user switches via a sign-out /
 * sign-in cycle.
 */
public class ShellView extends View {

    private static final String TAG = "ShellView";

    private DashboardController controller;

    public ShellView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Parent content = loader.load();
            this.controller = loader.getController();
            setCenter(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dashboard.fxml", e);
        }

        setOnShowing(e -> {
            Logger.d(TAG, "showing");
            // Dashboard has its own top bar — hide Gluon's AppBar.
            AppManager.getInstance().getAppBar().setVisible(false);

            if (controller != null) {
                User u = NavContext.get().getCurrentUser();
                if (u != null) {
                    controller.setCurrentUser(u);
                } else {
                    Logger.w(TAG, "Shell shown with no current user — DashboardController will render with null user");
                }
            }
        });
        setOnHiding(e -> {
            Logger.d(TAG, "hiding");
            AppManager.getInstance().getAppBar().setVisible(true);
        });
    }
}
