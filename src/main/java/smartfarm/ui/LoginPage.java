package smartfarm.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.User;
import smartfarm.model.Worker;
import smartfarm.service.AuthService;
import smartfarm.service.FingerprintService;
import smartfarm.service.SessionManager;
import smartfarm.service.SystemLogManager;

/**
 * Pure JavaFX sign-in / sign-up page — no FXML.
 * Left panel: branded farm image with overlay.
 * Right panel: tabbed sign-in / sign-up form.
 */
public class LoginPage {

    private final AuthService authService = new AuthService();
    private final FingerprintService fpService = new FingerprintService();
    private final WorkerDAO workerDAO = new WorkerDAO();

    // Root container
    private HBox root;

    // Right-side form area (swapped between sign-in and sign-up)
    private StackPane formContainer;
    private VBox signInForm;
    private VBox signUpForm;

    // Sign-in tab buttons
    private Button tabSignIn, tabSignUp;
    private Region tabIndicatorIn, tabIndicatorUp;

    // Sign-in fields
    private TextField txtEmailIn;
    private PasswordField txtPasswordIn;
    private TextField txtPasswordVisible;
    private CheckBox chkRemember;
    private Label lblErrorIn;

    // Sign-up fields
    private TextField txtFullName, txtUsername, txtEmailUp, txtPhone;
    private PasswordField txtPasswordUp, txtConfirmPassword;
    private ComboBox<String> cmbRole;
    private Label lblErrorUp;

    // ═══════════════ PUBLIC API ═══════════════

    public Parent createView() {
        root = new HBox();
        root.setStyle("-fx-background-color: #f5f7fa;");

        // ── Left Panel (~40%) ──
        StackPane leftPanel = buildLeftPanel();
        leftPanel.setPrefWidth(520);
        leftPanel.setMinWidth(400);
        leftPanel.setMaxWidth(600);

        // ── Right Panel (fills remaining ~60%) ──
        VBox rightPanel = buildRightPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        root.getChildren().addAll(leftPanel, rightPanel);
        return root;
    }

    // ═══════════════ LEFT PANEL ═══════════════

    private StackPane buildLeftPanel() {
        StackPane panel = new StackPane();
        // Match the very dark green at the image's top/bottom edges
        panel.setStyle("-fx-background-color: #162a1c;");

        // Full image visible, fit by width, centered vertically
        ImageView bgImage = new ImageView();
        try {
            Image img = new Image(getClass().getResourceAsStream("/images/logo.png"));
            bgImage.setImage(img);
            bgImage.setPreserveRatio(true);
            bgImage.fitWidthProperty().bind(panel.widthProperty());
            bgImage.setSmooth(true);
        } catch (Exception ignored) {}

        panel.getChildren().add(bgImage);
        return panel;
    }

    // ═══════════════ RIGHT PANEL ═══════════════

    private VBox buildRightPanel() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: white;");
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(50, 50, 40, 50));

        // Tab row
        HBox tabs = buildTabRow();

        // Form container
        formContainer = new StackPane();
        signInForm = buildSignInForm();
        signUpForm = buildSignUpForm();
        signUpForm.setVisible(false);
        signUpForm.setManaged(false);
        formContainer.getChildren().addAll(signInForm, signUpForm);

        VBox.setVgrow(formContainer, Priority.ALWAYS);
        panel.getChildren().addAll(tabs, formContainer);
        return panel;
    }

    private HBox buildTabRow() {
        HBox tabs = new HBox(0);
        tabs.setAlignment(Pos.CENTER_LEFT);
        tabs.setStyle("-fx-border-color: transparent transparent #e5e7eb transparent; -fx-border-width: 0 0 1 0;");

        VBox tabInBox = new VBox(0);
        tabSignIn = new Button("Sign In");
        tabSignIn.setStyle("-fx-background-color: transparent; -fx-font-size: 15; -fx-font-weight: bold; " +
                "-fx-text-fill: #2e7d32; -fx-padding: 12 30; -fx-cursor: hand;");
        tabIndicatorIn = new Region();
        tabIndicatorIn.setPrefHeight(3);
        tabIndicatorIn.setStyle("-fx-background-color: #2e7d32;");
        tabInBox.getChildren().addAll(tabSignIn, tabIndicatorIn);

        VBox tabUpBox = new VBox(0);
        tabSignUp = new Button("Sign Up");
        tabSignUp.setStyle("-fx-background-color: transparent; -fx-font-size: 15; -fx-font-weight: bold; " +
                "-fx-text-fill: #9ca3af; -fx-padding: 12 30; -fx-cursor: hand;");
        tabIndicatorUp = new Region();
        tabIndicatorUp.setPrefHeight(3);
        tabIndicatorUp.setStyle("-fx-background-color: transparent;");
        tabUpBox.getChildren().addAll(tabSignUp, tabIndicatorUp);

        tabSignIn.setOnAction(e -> switchToSignIn());
        tabSignUp.setOnAction(e -> switchToSignUp());

        tabs.getChildren().addAll(tabInBox, tabUpBox);
        return tabs;
    }

    // ── Sign In Form ──

    private VBox buildSignInForm() {
        VBox form = new VBox(18);
        form.setPadding(new Insets(30, 0, 0, 0));
        form.setAlignment(Pos.TOP_LEFT);

        Label welcome = new Label("Welcome back!");
        welcome.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Sign in to continue to your account");
        subtitle.setStyle("-fx-font-size: 13; -fx-text-fill: #6b7280;");

        lblErrorIn = new Label();
        lblErrorIn.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12; -fx-font-weight: bold;");
        lblErrorIn.setVisible(false);
        lblErrorIn.setManaged(false);

        // Email
        Label emailLabel = new Label("Email Address");
        emailLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #374151;");
        txtEmailIn = new TextField();
        txtEmailIn.setPromptText("Enter your email");
        txtEmailIn.setPrefHeight(44);
        HBox emailField = wrapFieldWithIcon(txtEmailIn, "fth-mail");

        // Password
        HBox pwdHeader = new HBox();
        pwdHeader.setAlignment(Pos.CENTER_LEFT);
        Label pwdLabel = new Label("Password");
        pwdLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #374151;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Hyperlink forgot = new Hyperlink("Forgot password?");
        forgot.setStyle("-fx-font-size: 12; -fx-text-fill: #2e7d32; -fx-border-color: transparent;");
        forgot.setOnAction(e -> onForgotPassword());
        pwdHeader.getChildren().addAll(pwdLabel, sp, forgot);

        txtPasswordIn = new PasswordField();
        txtPasswordIn.setPromptText("Enter your password");
        txtPasswordIn.setPrefHeight(44);
        txtPasswordVisible = new TextField();
        txtPasswordVisible.setPromptText("Enter your password");
        txtPasswordVisible.setPrefHeight(44);
        txtPasswordVisible.setVisible(false);
        txtPasswordVisible.setManaged(false);
        txtPasswordVisible.textProperty().bindBidirectional(txtPasswordIn.textProperty());

        StackPane passwordStack = new StackPane(txtPasswordIn, txtPasswordVisible);
        HBox.setHgrow(passwordStack, Priority.ALWAYS);

        Button eyeBtn = new Button();
        org.kordamp.ikonli.javafx.FontIcon eyeIcon = new org.kordamp.ikonli.javafx.FontIcon("fth-eye");
        eyeIcon.setIconSize(16);
        eyeIcon.setIconColor(Color.web("#9ca3af"));
        eyeBtn.setGraphic(eyeIcon);
        eyeBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0 8;");
        eyeBtn.setOnAction(e -> togglePasswordVisibility(txtPasswordIn, txtPasswordVisible, eyeIcon));

        HBox passwordField = buildIconField("fth-lock", passwordStack, eyeBtn);

        // Remember me
        chkRemember = new CheckBox("Remember me");
        chkRemember.setSelected(true);
        chkRemember.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");

        // Sign In button
        Button btnSignIn = new Button("→  Sign In");
        btnSignIn.setPrefHeight(48);
        btnSignIn.setMaxWidth(Double.MAX_VALUE);
        btnSignIn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-size: 15; " +
                "-fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand;");
        btnSignIn.setOnAction(e -> handleSignIn());

        // Fingerprint button
        Button btnFingerprint = new Button("Fingerprint Login");
        btnFingerprint.setPrefHeight(40);
        btnFingerprint.setMaxWidth(Double.MAX_VALUE);
        btnFingerprint.setStyle("-fx-background-color: transparent; -fx-text-fill: #2e7d32; -fx-font-size: 13; " +
                "-fx-font-weight: bold; -fx-border-color: #2e7d32; -fx-border-radius: 10; " +
                "-fx-background-radius: 10; -fx-cursor: hand;");
        org.kordamp.ikonli.javafx.FontIcon fpIcon = new org.kordamp.ikonli.javafx.FontIcon("fth-cpu");
        fpIcon.setIconSize(16);
        fpIcon.setIconColor(Color.web("#2e7d32"));
        btnFingerprint.setGraphic(fpIcon);
        btnFingerprint.setOnAction(e -> handleFingerprintLogin(btnFingerprint));

        // Bottom link
        HBox bottomRow = new HBox(4);
        bottomRow.setAlignment(Pos.CENTER);
        bottomRow.setPadding(new Insets(10, 0, 0, 0));
        Label noAcc = new Label("Don't have an account?");
        noAcc.setStyle("-fx-font-size: 13; -fx-text-fill: #6b7280;");
        Hyperlink signUpLink = new Hyperlink("Sign up");
        signUpLink.setStyle("-fx-font-size: 13; -fx-text-fill: #2e7d32; -fx-font-weight: bold; -fx-border-color: transparent;");
        signUpLink.setOnAction(e -> switchToSignUp());
        bottomRow.getChildren().addAll(noAcc, signUpLink);

        txtEmailIn.setOnAction(e -> txtPasswordIn.requestFocus());
        txtPasswordIn.setOnAction(e -> handleSignIn());

        form.getChildren().addAll(welcome, subtitle, lblErrorIn, emailLabel, emailField,
                pwdHeader, passwordField, chkRemember, btnSignIn, btnFingerprint, bottomRow);
        return form;
    }

    // ── Sign Up Form ──

    private VBox buildSignUpForm() {
        VBox form = new VBox(14);
        form.setPadding(new Insets(24, 0, 0, 0));
        form.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Create Account");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Fill in the details to get started");
        subtitle.setStyle("-fx-font-size: 13; -fx-text-fill: #6b7280;");

        lblErrorUp = new Label();
        lblErrorUp.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12; -fx-font-weight: bold;");
        lblErrorUp.setVisible(false);
        lblErrorUp.setManaged(false);

        txtFullName = new TextField();
        txtFullName.setPromptText("Full Name");
        txtFullName.setPrefHeight(40);
        HBox fullNameField = wrapFieldWithIcon(txtFullName, "fth-user");

        txtUsername = new TextField();
        txtUsername.setPromptText("Username");
        txtUsername.setPrefHeight(40);
        HBox usernameField = wrapFieldWithIcon(txtUsername, "fth-at-sign");

        txtEmailUp = new TextField();
        txtEmailUp.setPromptText("Email Address");
        txtEmailUp.setPrefHeight(40);
        HBox emailField = wrapFieldWithIcon(txtEmailUp, "fth-mail");

        txtPhone = new TextField();
        txtPhone.setPromptText("Phone Number (optional)");
        txtPhone.setPrefHeight(40);
        HBox phoneField = wrapFieldWithIcon(txtPhone, "fth-phone");

        txtPasswordUp = new PasswordField();
        txtPasswordUp.setPromptText("Password (min 6 chars)");
        txtPasswordUp.setPrefHeight(40);
        HBox pwdField = wrapFieldWithIcon(txtPasswordUp, "fth-lock");

        txtConfirmPassword = new PasswordField();
        txtConfirmPassword.setPromptText("Confirm Password");
        txtConfirmPassword.setPrefHeight(40);
        HBox confirmField = wrapFieldWithIcon(txtConfirmPassword, "fth-lock");

        // Role selector
        HBox roleRow = new HBox(10);
        roleRow.setAlignment(Pos.CENTER_LEFT);
        Label roleLabel = new Label("Role:");
        roleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #374151;");
        cmbRole = new ComboBox<>();
        cmbRole.getItems().addAll("Admin", "Manager");
        cmbRole.setValue("Manager");
        cmbRole.setStyle("-fx-font-size: 13;");
        cmbRole.setPrefHeight(38);
        roleRow.getChildren().addAll(roleLabel, cmbRole);

        // Create account button
        Button btnCreate = new Button("Create Account");
        btnCreate.setPrefHeight(48);
        btnCreate.setMaxWidth(Double.MAX_VALUE);
        btnCreate.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-size: 15; " +
                "-fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand;");
        btnCreate.setOnAction(e -> handleSignUp());

        // Bottom link
        HBox bottomRow = new HBox(4);
        bottomRow.setAlignment(Pos.CENTER);
        bottomRow.setPadding(new Insets(6, 0, 0, 0));
        Label hasAcc = new Label("Already have an account?");
        hasAcc.setStyle("-fx-font-size: 13; -fx-text-fill: #6b7280;");
        Hyperlink signInLink = new Hyperlink("Sign in");
        signInLink.setStyle("-fx-font-size: 13; -fx-text-fill: #2e7d32; -fx-font-weight: bold; -fx-border-color: transparent;");
        signInLink.setOnAction(e -> switchToSignIn());
        bottomRow.getChildren().addAll(hasAcc, signInLink);

        ScrollPane sp = new ScrollPane(new VBox(14, title, subtitle, lblErrorUp, fullNameField,
                usernameField, emailField, phoneField, pwdField, confirmField, roleRow, btnCreate, bottomRow));
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent;");
        ((VBox) sp.getContent()).setAlignment(Pos.TOP_LEFT);
        ((VBox) sp.getContent()).setPadding(new Insets(0, 2, 0, 0));

        form.getChildren().add(sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return form;
    }

    // ═══════════════ FIELD HELPERS ═══════════════

    private HBox wrapFieldWithIcon(TextField field, String iconCode) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; " +
                "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14;");
        box.setPrefHeight(44);

        org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(iconCode);
        icon.setIconSize(16);
        icon.setIconColor(Color.web("#9ca3af"));

        field.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-font-size: 13; -fx-padding: 0;");
        HBox.setHgrow(field, Priority.ALWAYS);

        box.getChildren().addAll(icon, field);
        return box;
    }

    private HBox buildIconField(String iconCode, StackPane fieldStack, Button trailing) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; " +
                "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14;");
        box.setPrefHeight(44);

        org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(iconCode);
        icon.setIconSize(16);
        icon.setIconColor(Color.web("#9ca3af"));

        HBox.setHgrow(fieldStack, Priority.ALWAYS);
        box.getChildren().addAll(icon, fieldStack, trailing);
        return box;
    }

    // ═══════════════ TAB SWITCHING ═══════════════

    private void switchToSignIn() {
        signInForm.setVisible(true);
        signInForm.setManaged(true);
        signUpForm.setVisible(false);
        signUpForm.setManaged(false);
        tabSignIn.setStyle("-fx-background-color: transparent; -fx-font-size: 15; -fx-font-weight: bold; " +
                "-fx-text-fill: #2e7d32; -fx-padding: 12 30; -fx-cursor: hand;");
        tabIndicatorIn.setStyle("-fx-background-color: #2e7d32;");
        tabSignUp.setStyle("-fx-background-color: transparent; -fx-font-size: 15; -fx-font-weight: bold; " +
                "-fx-text-fill: #9ca3af; -fx-padding: 12 30; -fx-cursor: hand;");
        tabIndicatorUp.setStyle("-fx-background-color: transparent;");
        lblErrorIn.setVisible(false);
        lblErrorIn.setManaged(false);
    }

    private void switchToSignUp() {
        signInForm.setVisible(false);
        signInForm.setManaged(false);
        signUpForm.setVisible(true);
        signUpForm.setManaged(true);
        tabSignUp.setStyle("-fx-background-color: transparent; -fx-font-size: 15; -fx-font-weight: bold; " +
                "-fx-text-fill: #2e7d32; -fx-padding: 12 30; -fx-cursor: hand;");
        tabIndicatorUp.setStyle("-fx-background-color: #2e7d32;");
        tabSignIn.setStyle("-fx-background-color: transparent; -fx-font-size: 15; -fx-font-weight: bold; " +
                "-fx-text-fill: #9ca3af; -fx-padding: 12 30; -fx-cursor: hand;");
        tabIndicatorIn.setStyle("-fx-background-color: transparent;");
        lblErrorUp.setVisible(false);
        lblErrorUp.setManaged(false);
    }

    // ═══════════════ PASSWORD TOGGLE ═══════════════

    private void togglePasswordVisibility(PasswordField pwd, TextField visible,
                                          org.kordamp.ikonli.javafx.FontIcon icon) {
        if (pwd.isVisible()) {
            pwd.setVisible(false);
            pwd.setManaged(false);
            visible.setVisible(true);
            visible.setManaged(true);
            icon.setIconLiteral("fth-eye-off");
        } else {
            visible.setVisible(false);
            visible.setManaged(false);
            pwd.setVisible(true);
            pwd.setManaged(true);
            icon.setIconLiteral("fth-eye");
        }
    }

    // ═══════════════ SIGN IN ═══════════════

    private void handleSignIn() {
        String email = txtEmailIn.getText().trim();
        String password = txtPasswordIn.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError(lblErrorIn, "Please fill in all fields");
            return;
        }

        try {
            User user = authService.signIn(email, password);
            if (chkRemember.isSelected()) {
                SessionManager.saveSession(email);
            }
            SystemLogManager.getInstance().info("AuthService",
                    user.getFullName() + " logged in successfully", user.getFullName());
            navigateToDashboard(user);
        } catch (RuntimeException e) {
            SystemLogManager.getInstance().warning("AuthService",
                    "Failed login attempt for " + email, "system");
            showError(lblErrorIn, e.getMessage());
        }
    }

    // ═══════════════ FINGERPRINT LOGIN ═══════════════

    private void handleFingerprintLogin(Button btn) {
        btn.setDisable(true);
        btn.setText("Connecting to sensor...");

        new Thread(() -> {
            try {
                if (!fpService.isConnected()) {
                    if (!fpService.autoConnect()) {
                        javafx.application.Platform.runLater(() -> {
                            btn.setText("Fingerprint Login");
                            btn.setDisable(false);
                            showError(lblErrorIn, "Cannot connect to sensor. Close Arduino Serial Monitor first.");
                        });
                        return;
                    }
                }
                javafx.application.Platform.runLater(() -> btn.setText("Place your finger..."));

                int fpId = fpService.scanAndMatch();
                if (fpId < 0) {
                    javafx.application.Platform.runLater(() -> {
                        btn.setText("Fingerprint Login");
                        btn.setDisable(false);
                        showError(lblErrorIn, "Fingerprint not recognized. Try again.");
                    });
                    return;
                }

                Worker worker = findWorkerByFingerprint(fpId);
                if (worker == null) {
                    javafx.application.Platform.runLater(() -> {
                        btn.setText("Fingerprint Login");
                        btn.setDisable(false);
                        showError(lblErrorIn, "No worker registered with this fingerprint (ID: " + fpId + ")");
                    });
                    return;
                }

                User user = new User(worker.getWorkerId(), worker.getFullName(),
                        worker.getEmail() != null ? worker.getEmail() : "",
                        worker.getFullName(), User.Role.WORKER);

                SystemLogManager.getInstance().info("AuthService",
                        worker.getFullName() + " logged in via fingerprint", worker.getFullName());
                javafx.application.Platform.runLater(() -> navigateToDashboard(user));

            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    btn.setText("Fingerprint Login");
                    btn.setDisable(false);
                    showError(lblErrorIn, "Error: " + e.getMessage());
                });
            }
        }).start();
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

    // ═══════════════ SIGN UP ═══════════════

    private void handleSignUp() {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String email = txtEmailUp.getText().trim();
        String phone = txtPhone.getText().trim();
        String password = txtPasswordUp.getText();
        String confirm = txtConfirmPassword.getText();

        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError(lblErrorUp, "Please fill in all required fields");
            return;
        }
        if (!password.equals(confirm)) {
            showError(lblErrorUp, "Passwords do not match");
            return;
        }
        if (password.length() < 6) {
            showError(lblErrorUp, "Password must be at least 6 characters");
            return;
        }

        User.Role role = "Admin".equals(cmbRole.getValue()) ? User.Role.ADMIN : User.Role.MANAGER;

        try {
            authService.signUp(fullName, username, email, password, phone, role);
            switchToSignIn();
            txtEmailIn.setText(email);
        } catch (RuntimeException e) {
            showError(lblErrorUp, e.getMessage());
        }
    }

    // ═══════════════ NAVIGATION ═══════════════

    private void navigateToDashboard(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Parent dashRoot = loader.load();
            DashboardController controller = loader.getController();
            controller.setCurrentUser(user);
            Stage stage = (Stage) root.getScene().getWindow();
            Scene scene = stage.getScene();
            scene.setRoot(dashRoot);
            stage.setTitle("Agrilliant — Smart Farm Management System");
            stage.setMaximized(true);
        } catch (Exception e) {
            showError(lblErrorIn, "Failed to load dashboard: " + e.getMessage());
        }
    }

    // ═══════════════ FORGOT PASSWORD ═══════════════

    private void onForgotPassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Forgot Password");
        alert.setHeaderText("Password Reset");
        alert.setContentText("Please contact your system administrator to reset your password.");
        alert.showAndWait();
    }

    // ═══════════════ UTIL ═══════════════

    private void showError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }
}
