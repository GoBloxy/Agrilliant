package smartfarm.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.User;
import smartfarm.model.Worker;
import smartfarm.service.AuthService;
import smartfarm.service.FingerprintService;
import smartfarm.service.SessionManager;

public class SignInController {

    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnSignIn;
    @FXML private Button btnFingerprint;
    @FXML private Label lblFpStatus;

    private final AuthService authService = new AuthService();
    private final FingerprintService fpService = new FingerprintService();
    private final WorkerDAO workerDAO = new WorkerDAO();

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
    private void onFingerprintLogin() {
        lblFpStatus.setVisible(true);
        lblFpStatus.setText("Connecting to sensor...");
        lblError.setVisible(false);
        btnFingerprint.setDisable(true);

        new Thread(() -> {
            try {
                if (!fpService.isConnected()) {
                    if (!fpService.autoConnect()) {
                        updateFpStatus("Cannot connect to ESP32. Close Arduino Serial Monitor first.");
                        return;
                    }
                }
                updateFpStatus("Place your finger on the sensor...");

                int fpId = fpService.scanAndMatch();
                if (fpId < 0) {
                    updateFpStatus("Fingerprint not recognized. Try again.");
                    return;
                }

                // Look up worker by fingerprint ID
                Worker worker = findWorkerByFingerprint(fpId);
                if (worker == null) {
                    updateFpStatus("No worker registered with this fingerprint (ID: " + fpId + ")");
                    return;
                }

                // Build User object with WORKER role and navigate
                User user = new User(worker.getWorkerId(), worker.getFullName(),
                        worker.getEmail() != null ? worker.getEmail() : "",
                        worker.getFullName(), User.Role.WORKER);

                javafx.application.Platform.runLater(() -> {
                    lblFpStatus.setVisible(false);
                    navigateToDashboard(user);
                });

            } catch (Exception e) {
                updateFpStatus("Error: " + e.getMessage());
            } finally {
                javafx.application.Platform.runLater(() -> btnFingerprint.setDisable(false));
            }
        }).start();
    }

    private void updateFpStatus(String msg) {
        javafx.application.Platform.runLater(() -> {
            lblFpStatus.setText(msg);
            lblFpStatus.setVisible(true);
        });
    }

    private Worker findWorkerByFingerprint(int fpId) {
        try {
            for (Worker w : workerDAO.getAll()) {
                if (w.getFingerprintId() != null && w.getFingerprintId() == fpId) {
                    return w;
                }
            }
        } catch (Exception e) {
            System.err.println("Error looking up worker by fingerprint: " + e.getMessage());
        }
        return null;
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
