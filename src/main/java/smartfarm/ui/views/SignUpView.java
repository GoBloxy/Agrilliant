package smartfarm.ui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

/**
 * Gluon {@link View} wrapping the existing {@code signup.fxml}.
 *
 * <p>The existing {@code SignUpController} keeps working unchanged. B2
 * will rewrite the controller's navigation calls.
 */
public class SignUpView extends View {

    public SignUpView() {
        try {
            Parent content = FXMLLoader.load(
                    getClass().getResource("/fxml/signup.fxml"));
            setCenter(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load signup.fxml", e);
        }
    }
}
