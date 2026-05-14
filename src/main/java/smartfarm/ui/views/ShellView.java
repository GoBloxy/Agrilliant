package smartfarm.ui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import smartfarm.model.User;
import smartfarm.ui.nav.NavContext;

/**
 * B1 STUB: Welcome screen that proves the splash → shell pipeline works.
 *
 * <p>Reads the current user from {@link NavContext} on each show and
 * displays a "Welcome, &lt;name&gt;" message.
 *
 * <p>TODO(B2): replace with a Gluon NavigationDrawer + content area
 * driven by {@link smartfarm.ui.nav.ShellContent}.
 */
public class ShellView extends View {

    private final VBox box;

    public ShellView() {
        this.box = new VBox(12);
        this.box.setAlignment(Pos.CENTER);
        setCenter(this.box);

        setOnShowing(e -> refreshContent());
    }

    private void refreshContent() {
        this.box.getChildren().clear();
        User u = NavContext.get().getCurrentUser();
        if (u != null) {
            this.box.getChildren().add(new Label("Welcome, " + u.getFullName()));
        }
        this.box.getChildren().add(new Label("Shell — full UI coming in B2"));
    }
}
