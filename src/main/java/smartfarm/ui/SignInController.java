package smartfarm.ui;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.User;
import smartfarm.model.Worker;
import smartfarm.service.AuthService;
import smartfarm.service.FingerprintService;
import smartfarm.service.SessionManager;
import smartfarm.ui.async.AsyncCalls;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.nav.NavContext;
import smartfarm.util.Logger;

public class SignInController {

    private static final String TAG = "SignInController";

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

        // P2.4: async. AuthService.signIn blocks on a DB lookup; running it
        // on the FX thread would freeze the UI while the JDBC round-trip is
        // in flight. runWithBusy disables btnSignIn for the duration so the
        // user can't double-submit; the 10s timeout protects against a hung
        // DB connection (TimeoutException is delivered to onError).
        lblError.setVisible(false);
        AsyncCalls.runWithBusy(
                btnSignIn,
                () -> authService.signIn(email, password),
                user -> {
                    SessionManager.saveSession(email);
                    navigateToDashboard(user);
                },
                err -> {
                    if (err instanceof TimeoutException) {
                        showError("Sign-in timed out. Check your connection and try again.");
                    } else {
                        showError(err.getMessage());
                    }
                },
                Duration.ofSeconds(10));
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
            Logger.e(TAG, "Error looking up worker by fingerprint", e);
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
        AppView.SIGNUP.switchTo();
    }

    private void navigateToDashboard(User user) {
        NavContext.get().setCurrentUser(user);
        AppView.SHELL.switchTo();
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
