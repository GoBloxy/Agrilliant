package smartfarm.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import smartfarm.model.User;
import smartfarm.service.AuthService;

public class SignUpView extends HBox {

    private final TextField txtFullName = new TextField();
    private final TextField txtUsername = new TextField();
    private final TextField txtEmail = new TextField();
    private final TextField txtPhone = new TextField();
    private final PasswordField txtPassword = new PasswordField();
    private final PasswordField txtConfirm = new PasswordField();
    private final ComboBox<String> cmbRole = new ComboBox<>();
    private final Label lblError = new Label();
    private final AuthService authService = new AuthService();

    public SignUpView() {
        getStyleClass().add("auth-root");
        setPrefSize(1100, 700);

        Region imagePanel = new Region();
        imagePanel.getStyleClass().add("auth-image-panel");
        imagePanel.setPrefWidth(540);
        imagePanel.setMinWidth(540);
        imagePanel.setMaxWidth(540);

        ScrollPane scroll = new ScrollPane(buildFormPanel());
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("auth-scroll");
        HBox.setHgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(imagePanel, scroll);
    }

    private VBox buildFormPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(30, 60, 30, 60));
        panel.getStyleClass().add("auth-form-panel");

        Button tabSignIn = new Button("Sign In");
        tabSignIn.getStyleClass().add("auth-tab");
        tabSignIn.setOnAction(e -> navigateToSignIn());

        Button tabSignUp = new Button("Sign Up");
        tabSignUp.getStyleClass().addAll("auth-tab", "auth-tab-active");

        HBox tabs = new HBox(tabSignIn, tabSignUp);
        tabs.setAlignment(Pos.CENTER);
        tabs.getStyleClass().add("auth-tabs");

        Label title = new Label("Create Account");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Join Agrilliant to manage your farm");
        subtitle.getStyleClass().add("auth-subtitle");
        VBox header = new VBox(4, title, subtitle);
        header.setAlignment(Pos.CENTER);

        txtFullName.setPromptText("Enter your full name");
        txtFullName.getStyleClass().add("auth-field");
        txtUsername.setPromptText("Choose a username");
        txtUsername.getStyleClass().add("auth-field");
        txtEmail.setPromptText("Enter your email");
        txtEmail.getStyleClass().add("auth-field");
        txtPhone.setPromptText("Enter your phone number");
        txtPhone.getStyleClass().add("auth-field");
        txtPassword.setPromptText("Create a password");
        txtPassword.getStyleClass().add("auth-field");
        txtConfirm.setPromptText("Confirm your password");
        txtConfirm.getStyleClass().add("auth-field");

        cmbRole.getItems().addAll("Admin", "Manager");
        cmbRole.setValue("Manager");
        cmbRole.setPromptText("Select role");
        cmbRole.getStyleClass().add("auth-combo");

        lblError.getStyleClass().add("auth-error");
        lblError.setVisible(false);
        lblError.setManaged(false);

        Button btnSignUp = new Button("\u2192 Sign Up");
        btnSignUp.getStyleClass().add("auth-btn");
        btnSignUp.setOnAction(e -> onSignUp());

        VBox fields = new VBox(10,
                label("Full Name"), txtFullName,
                label("Username"), txtUsername,
                label("Email Address"), txtEmail,
                label("Phone (optional)"), txtPhone,
                label("Password"), txtPassword,
                label("Confirm Password"), txtConfirm,
                label("Role"), cmbRole,
                lblError, spacer(4), btnSignUp
        );
        fields.setAlignment(Pos.CENTER_LEFT);
        fields.setMaxWidth(380);

        Label hasAccount = new Label("Already have an account?");
        hasAccount.getStyleClass().add("auth-subtitle");
        Hyperlink signInLink = new Hyperlink("Sign in");
        signInLink.getStyleClass().add("auth-link");
        signInLink.setOnAction(e -> navigateToSignIn());
        HBox bottomRow = new HBox(4, hasAccount, signInLink);
        bottomRow.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(tabs, spacer(20), header, spacer(16), fields, spacer(16), bottomRow, spacer(16));
        return panel;
    }

    private Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("auth-label");
        return l;
    }

    private Region spacer(double height) {
        Region r = new Region();
        r.setPrefHeight(height);
        return r;
    }

    private void onSignUp() {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();
        String password = txtPassword.getText();
        String confirm = txtConfirm.getText();

        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all required fields"); return;
        }
        if (!password.equals(confirm)) { showError("Passwords do not match"); return; }
        if (password.length() < 6) { showError("Password must be at least 6 characters"); return; }

        User.Role role = "Admin".equals(cmbRole.getValue()) ? User.Role.ADMIN : User.Role.MANAGER;
        try {
            authService.signUp(fullName, username, email, password, phone, role);
            navigateToSignIn();
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    private void navigateToSignIn() {
        getScene().setRoot(new SignInView());
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}
