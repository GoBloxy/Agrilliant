package smartfarm.ui;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import smartfarm.model.User;
import smartfarm.service.AuthService;
import smartfarm.ui.async.AsyncCalls;
import smartfarm.ui.nav.AppView;

public class SignUpController {

    @FXML private TextField txtFullName, txtUsername, txtEmail, txtPhone;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private ComboBox<String> cmbRole;
    @FXML private Label lblError;
    @FXML private Button btnSignUp;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        lblError.setVisible(false);
        cmbRole.getItems().addAll("Admin", "Manager");
        cmbRole.setValue("Manager");
    }

    @FXML
    private void onSignUp() {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();
        String password = txtPassword.getText();
        String confirm = txtConfirm.getText();

        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all required fields");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            return;
        }

        User.Role role = "Admin".equals(cmbRole.getValue()) ? User.Role.ADMIN : User.Role.MANAGER;

        // P2.4: async. AuthService.signUp performs a DB INSERT (and a
        // uniqueness check) so it blocks the FX thread when called sync.
        // runWithBusy disables btnSignUp for the duration; the 10s timeout
        // surfaces a TimeoutException rather than leaving the button stuck.
        lblError.setVisible(false);
        AsyncCalls.runWithBusy(
                btnSignUp,
                () -> { authService.signUp(fullName, username, email, password, phone, role); return null; },
                ignored -> onGoToSignIn(),
                err -> {
                    if (err instanceof TimeoutException) {
                        showError("Sign-up timed out. Check your connection and try again.");
                    } else {
                        showError(err.getMessage());
                    }
                },
                Duration.ofSeconds(10));
    }

    @FXML
    private void onGoToSignIn() {
        AppView.SIGNIN.switchTo();
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
