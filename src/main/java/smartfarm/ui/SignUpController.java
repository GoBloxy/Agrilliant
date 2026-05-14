package smartfarm.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import smartfarm.model.User;
import smartfarm.service.AuthService;

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
        lblError.setManaged(false);
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

        try {
            authService.signUp(fullName, username, email, password, phone, role);
            onGoToSignIn();
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onGoToSignIn() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/signin.fxml"));
            Stage stage = (Stage) btnSignUp.getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            showError("Navigation error: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}
