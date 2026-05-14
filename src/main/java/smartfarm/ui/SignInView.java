package smartfarm.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.User;
import smartfarm.model.Worker;
import smartfarm.service.AuthService;
import smartfarm.service.FingerprintService;
import smartfarm.service.SessionManager;
import smartfarm.service.SystemLogManager;

public class SignInView extends HBox {

    private final TextField txtEmail = new TextField();
    private final PasswordField txtPassword = new PasswordField();
    private final Label lblError = new Label();
    private final Button btnFingerprint = new Button("Fingerprint");
    private final Label lblFpStatus = new Label();
    private final CheckBox chkRemember = new CheckBox("Remember me");
    private final AuthService authService = new AuthService();
    private final FingerprintService fpService = new FingerprintService();
    private final WorkerDAO workerDAO = new WorkerDAO();

    public SignInView() {
        getStyleClass().add("auth-root");
        setPrefSize(1100, 700);

        Region imagePanel = new Region();
        imagePanel.getStyleClass().add("auth-image-panel");
        imagePanel.setPrefWidth(540);
        imagePanel.setMinWidth(540);
        imagePanel.setMaxWidth(540);

        VBox formPanel = buildFormPanel();
        formPanel.getStyleClass().add("auth-form-panel");
        HBox.setHgrow(formPanel, Priority.ALWAYS);

        getChildren().addAll(imagePanel, formPanel);
    }

    private VBox buildFormPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(40, 60, 40, 60));

        Button tabSignIn = new Button("Sign In");
        tabSignIn.getStyleClass().addAll("auth-tab", "auth-tab-active");

        Button tabSignUp = new Button("Sign Up");
        tabSignUp.getStyleClass().add("auth-tab");
        tabSignUp.setOnAction(e -> navigateToSignUp());

        HBox tabs = new HBox(tabSignIn, tabSignUp);
        tabs.setAlignment(Pos.CENTER);
        tabs.getStyleClass().add("auth-tabs");

        Label title = new Label("Welcome back!");
        title.getStyleClass().add("auth-title");

        Label subtitle = new Label("Sign in to continue to your account");
        subtitle.getStyleClass().add("auth-subtitle");

        VBox header = new VBox(4, title, subtitle);
        header.setAlignment(Pos.CENTER);

        Label emailLabel = new Label("Email Address");
        emailLabel.getStyleClass().add("auth-label");
        txtEmail.setPromptText("Enter your email");
        txtEmail.getStyleClass().add("auth-field");
        txtEmail.setOnAction(e -> txtPassword.requestFocus());

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("auth-label");

        Hyperlink forgot = new Hyperlink("Forgot password?");
        forgot.getStyleClass().add("auth-forgot");
        forgot.setOnAction(e -> onForgotPassword());

        HBox passRow = new HBox(passLabel, new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, forgot);
        passRow.setAlignment(Pos.CENTER_LEFT);

        txtPassword.setPromptText("Enter your password");
        txtPassword.getStyleClass().add("auth-field");
        txtPassword.setOnAction(e -> onSignIn());

        chkRemember.getStyleClass().add("auth-remember");
        chkRemember.setSelected(true);

        lblError.getStyleClass().add("auth-error");
        lblError.setVisible(false);
        lblError.setManaged(false);

        Button btnSignIn = new Button("\u2192 Sign In");
        btnSignIn.getStyleClass().add("auth-btn");
        btnSignIn.setOnAction(e -> onSignIn());

        VBox fields = new VBox(14, emailLabel, txtEmail, passRow, txtPassword, chkRemember, lblError, btnSignIn);
        fields.setAlignment(Pos.CENTER_LEFT);
        fields.setMaxWidth(380);

        Region divLeft = new Region();
        HBox.setHgrow(divLeft, Priority.ALWAYS);
        divLeft.setStyle("-fx-border-color:#e5e7eb;-fx-border-width:0 0 1 0;");
        Region divRight = new Region();
        HBox.setHgrow(divRight, Priority.ALWAYS);
        divRight.setStyle("-fx-border-color:#e5e7eb;-fx-border-width:0 0 1 0;");
        Label divText = new Label("or continue with");
        divText.getStyleClass().add("auth-divider-text");
        HBox divider = new HBox(10, divLeft, divText, divRight);
        divider.setAlignment(Pos.CENTER);

        btnFingerprint.getStyleClass().add("auth-social-btn");
        btnFingerprint.setMaxWidth(Double.MAX_VALUE);
        btnFingerprint.setOnAction(e -> onFingerprintLogin());
        HBox socialRow = new HBox(12, btnFingerprint);
        socialRow.setAlignment(Pos.CENTER);
        socialRow.setMaxWidth(380);
        HBox.setHgrow(btnFingerprint, Priority.ALWAYS);

        lblFpStatus.getStyleClass().add("auth-subtitle");
        lblFpStatus.setVisible(false);
        lblFpStatus.setManaged(false);

        Label noAccount = new Label("Don't have an account?");
        noAccount.getStyleClass().add("auth-subtitle");
        Hyperlink signUpLink = new Hyperlink("Sign up");
        signUpLink.getStyleClass().add("auth-link");
        signUpLink.setOnAction(e -> navigateToSignUp());
        HBox bottomRow = new HBox(4, noAccount, signUpLink);
        bottomRow.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(
                tabs, spacer(24), header, spacer(20),
                fields, spacer(16), divider, spacer(14),
                socialRow, lblFpStatus, spacer(18), bottomRow
        );

        return panel;
    }

    private Region spacer(double height) {
        Region r = new Region();
        r.setPrefHeight(height);
        return r;
    }

    private void onSignIn() {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText();
        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            return;
        }
        try {
            User user = authService.signIn(email, password);
            if (chkRemember.isSelected()) SessionManager.saveSession(email);
            SystemLogManager.getInstance().info("AuthService",
                    user.getFullName() + " logged in successfully", user.getFullName());
            navigateToDashboard(user);
        } catch (RuntimeException e) {
            SystemLogManager.getInstance().warning("AuthService",
                    "Failed login attempt for " + email, "system");
            showError(e.getMessage());
        }
    }

    private void onFingerprintLogin() {
        lblFpStatus.setVisible(true);
        lblFpStatus.setManaged(true);
        lblFpStatus.setText("Connecting to sensor...");
        lblError.setVisible(false);
        lblError.setManaged(false);
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
                if (fpId < 0) { updateFpStatus("Fingerprint not recognized. Try again."); return; }

                Worker worker = findWorkerByFingerprint(fpId);
                if (worker == null) { updateFpStatus("No worker registered with this fingerprint (ID: " + fpId + ")"); return; }

                User user = new User(worker.getWorkerId(), worker.getFullName(),
                        worker.getEmail() != null ? worker.getEmail() : "",
                        worker.getFullName(), User.Role.WORKER);
                SystemLogManager.getInstance().info("AuthService",
                        worker.getFullName() + " logged in via fingerprint", worker.getFullName());
                Platform.runLater(() -> { lblFpStatus.setVisible(false); lblFpStatus.setManaged(false); navigateToDashboard(user); });
            } catch (Exception e) {
                updateFpStatus("Error: " + e.getMessage());
            } finally {
                Platform.runLater(() -> btnFingerprint.setDisable(false));
            }
        }).start();
    }

    private void updateFpStatus(String msg) {
        Platform.runLater(() -> { lblFpStatus.setText(msg); lblFpStatus.setVisible(true); lblFpStatus.setManaged(true); });
    }

    private Worker findWorkerByFingerprint(int fpId) {
        try {
            for (Worker w : workerDAO.getAll()) {
                if (w.getFingerprintId() != null && w.getFingerprintId() == fpId) return w;
            }
        } catch (Exception e) { System.err.println("Error looking up worker by fingerprint: " + e.getMessage()); }
        return null;
    }

    private void onForgotPassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Forgot Password");
        alert.setHeaderText("Password Reset");
        alert.setContentText("Please contact your system administrator to reset your password.");
        alert.showAndWait();
    }

    private void navigateToSignUp() {
        getScene().setRoot(new SignUpView());
    }

    private void navigateToDashboard(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Parent root = loader.load();
            DashboardController controller = loader.getController();
            controller.setCurrentUser(user);
            Stage stage = (Stage) getScene().getWindow();
            getScene().setRoot(root);
            stage.setTitle("Agrilliant \u2014 Smart Farm Management System");
            stage.setMaximized(true);
        } catch (Exception e) {
            showError("Failed to load dashboard: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}
