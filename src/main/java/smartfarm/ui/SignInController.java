package smartfarm.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import smartfarm.model.User;
import smartfarm.service.AuthService;
import smartfarm.service.SessionManager;

public class SignInController {

    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnSignIn;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        lblError.setVisible(false);
        txtEmail.setOnAction(e -> txtPassword.requestFocus());
        txtPassword.setOnAction(e -> onSignIn());
    }

    @FXML
    private void onSignIn() {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            return;
        }

        try {
            User user = authService.signIn(email, password);
            SessionManager.saveSession(email);
            navigateToDashboard(user);
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onForgotPassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Forgot Password");
        alert.setHeaderText("Password Reset");
        alert.setContentText("Please contact your system administrator to reset your password.");
        alert.showAndWait();
    }

    @FXML
    private void onGoToSignUp() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/signup.fxml"));
            Stage stage = (Stage) btnSignIn.getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            showError("Navigation error: " + e.getMessage());
        }
    }

    private void navigateToDashboard(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Parent root = loader.load();
            DashboardController controller = loader.getController();
            controller.setCurrentUser(user);
            Stage stage = (Stage) btnSignIn.getScene().getWindow();
            Scene scene = stage.getScene();
            scene.setRoot(root);
            stage.setTitle("Agrilliant — Smart Farm Management System");
            stage.setMaximized(true);
        } catch (Exception e) {
            showError("Failed to load dashboard: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
