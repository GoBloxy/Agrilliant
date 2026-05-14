package smartfarm.ui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import smartfarm.util.Logger;

import java.io.IOException;

/**
 * Gluon {@link View} wrapping the existing {@code signin.fxml}.
 *
 * <p>The existing {@code SignInController} (referenced via
 * {@code fx:controller} in the FXML) keeps working unchanged. B2
 * rewrote the controller's navigation calls to use
 * {@link smartfarm.ui.nav.AppView#switchTo()}.
 */
public class SignInView extends View {

    public SignInView() {
        try {
            Parent content = FXMLLoader.load(
                    getClass().getResource("/fxml/signin.fxml"));
            setCenter(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load signin.fxml", e);
        }
        setOnShowing(e -> Logger.d("SignInView", "showing"));
        setOnHiding(e -> Logger.d("SignInView", "hiding"));
    }
}
