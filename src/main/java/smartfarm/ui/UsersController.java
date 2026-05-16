package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.AdminDAO;
import smartfarm.dao.ManagerDAO;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Admin;
import smartfarm.model.Manager;
import smartfarm.model.Worker;
import smartfarm.service.SystemLogManager;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class UsersController {

    @FXML private Label lblTotalUsers, lblAdminCount, lblManagerCount, lblWorkerCount;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbRole;
    @FXML private TableView<UserRow> userTable;
    @FXML private TableColumn<UserRow, String> colName, colUsername, colEmail, colPhone, colRole, colStatus, colActions;
    @FXML private Button btnAddUser;
    @FXML private PieChart rolePieChart;

    private final AdminDAO adminDAO = new AdminDAO();
    private final ManagerDAO managerDAO = new ManagerDAO();
    private final WorkerDAO workerDAO = new WorkerDAO();
    private final ObservableList<UserRow> allUsers = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        loadUsers();
        updateSummaryCards();
        populateChart();
    }

    private void setupTableColumns() {
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colName.setResizable(false);
        colUsername.setResizable(false);
        colEmail.setResizable(false);
        colPhone.setResizable(false);
        colRole.setResizable(false);
        colStatus.setResizable(false);
        colActions.setResizable(false);

        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().name));
        colUsername.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().username));
        colEmail.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().email != null ? d.getValue().email : "--"));
        colPhone.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().phone != null && !d.getValue().phone.isEmpty() ? d.getValue().phone : "--"));
        colRole.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().role));

        colRole.setCellFactory(col -> new TableCell<UserRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label badge = new Label(item);
                badge.setStyle(getRoleBadgeStyle(item));
                setGraphic(badge);
                setText(null);
            }
        });

        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().active ? "Active" : "Inactive"));
        colStatus.setCellFactory(col -> new TableCell<UserRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Circle dot = new Circle(4);
                dot.setFill("Active".equals(item) ? Color.web("#22c55e") : Color.web("#ef4444"));
                Label lbl = new Label(item);
                lbl.setGraphic(dot);
                lbl.setGraphicTextGap(6);
                setGraphic(lbl);
                setText(null);
            }
        });

        colActions.setCellFactory(col -> new TableCell<UserRow, String>() {
            private final Button editBtn = new Button("", new FontIcon("fth-edit-2"));
            private final Button delBtn = new Button("", new FontIcon("fth-trash-2"));
            private final HBox box = new HBox(6, editBtn, delBtn);
            {
                editBtn.getStyleClass().add("icon-btn");
                delBtn.getStyleClass().add("icon-btn");
                editBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        onEditUser(getTableView().getItems().get(idx));
                });
                delBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        onDeleteUser(getTableView().getItems().get(idx));
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void setupFilters() {
        cmbRole.getItems().addAll("All", "Admin", "Manager", "Worker");
        cmbRole.setValue("All");
        cmbRole.setOnAction(e -> applyFilters());
        txtSearch.textProperty().addListener((obs, old, val) -> applyFilters());
    }

    private void loadUsers() {
        allUsers.clear();
        try {
            for (Admin a : adminDAO.getAll()) {
                allUsers.add(new UserRow(a.getAdminId(), a.getFullName(), a.getUsername(),
                        a.getEmail(), a.getPhone(), "Admin", a.isActive()));
            }
        } catch (SQLException e) { /* skip */ }

        try {
            for (Manager m : managerDAO.getAll()) {
                allUsers.add(new UserRow(m.getManagerId(), m.getFullName(), m.getUsername(),
                        m.getEmail(), m.getPhone(), "Manager", m.isActive()));
            }
        } catch (SQLException e) { /* skip */ }

        try {
            for (Worker w : workerDAO.getAll()) {
                allUsers.add(new UserRow(w.getWorkerId(), w.getFullName(),
                        w.getEmail() != null ? w.getEmail().split("@")[0] : w.getFullName().toLowerCase().replace(" ", "."),
                        w.getEmail(), w.getPhone(), "Worker", w.isOnDuty()));
            }
        } catch (SQLException e) { /* skip */ }

        applyFilters();
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String role = cmbRole.getValue();
        List<UserRow> filtered = allUsers.stream()
                .filter(u -> "All".equals(role) || u.role.equals(role))
                .filter(u -> {
                    if (search.isEmpty()) return true;
                    return u.name.toLowerCase().contains(search)
                            || u.username.toLowerCase().contains(search)
                            || (u.email != null && u.email.toLowerCase().contains(search))
                            || u.role.toLowerCase().contains(search);
                })
                .collect(Collectors.toList());
        userTable.setItems(FXCollections.observableArrayList(filtered));
        if (filtered.isEmpty()) {
            userTable.setPlaceholder(new Label(search.isEmpty() ? "No users found" : "No users matching \"" + search + "\""));
        }
    }

    private void updateSummaryCards() {
        long admins = allUsers.stream().filter(u -> "Admin".equals(u.role)).count();
        long managers = allUsers.stream().filter(u -> "Manager".equals(u.role)).count();
        long workers = allUsers.stream().filter(u -> "Worker".equals(u.role)).count();

        lblTotalUsers.setText(String.valueOf(allUsers.size()));
        lblAdminCount.setText(String.valueOf(admins));
        lblManagerCount.setText(String.valueOf(managers));
        lblWorkerCount.setText(String.valueOf(workers));
    }

    private void populateChart() {
        long admins = allUsers.stream().filter(u -> "Admin".equals(u.role)).count();
        long managers = allUsers.stream().filter(u -> "Manager".equals(u.role)).count();
        long workers = allUsers.stream().filter(u -> "Worker".equals(u.role)).count();

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        if (admins > 0) data.add(new PieChart.Data("Admins (" + admins + ")", admins));
        if (managers > 0) data.add(new PieChart.Data("Managers (" + managers + ")", managers));
        if (workers > 0) data.add(new PieChart.Data("Workers (" + workers + ")", workers));
        if (data.isEmpty()) data.add(new PieChart.Data("No Users", 1));

        rolePieChart.setData(data);

        javafx.application.Platform.runLater(() -> {
            String[] colors = {"#3b82f6", "#22c55e", "#f59e0b"};
            int i = 0;
            for (PieChart.Data d : rolePieChart.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle("-fx-pie-color: " + colors[i % colors.length] + ";");
                }
                i++;
            }
        });
    }

    // ═══════════════ ADD USER ═══════════════

    @FXML
    private void onAddUser() {
        Dialog<UserRow> dialog = createUserDialog(null);
        dialog.showAndWait().ifPresent(row -> {
            try {
                switch (row.role) {
                    case "Admin":
                        Admin admin = new Admin(row.name, row.username, row.email, hashPassword("default123"), row.phone);
                        adminDAO.save(admin);
                        break;
                    case "Manager":
                        Manager mgr = new Manager(row.name, row.username, row.email, hashPassword("default123"), row.phone);
                        managerDAO.save(mgr);
                        break;
                    case "Worker":
                        Worker w = new Worker(row.name, row.phone, row.email, hashPassword("default123"),
                                "Worker", "", 1);
                        workerDAO.save(w);
                        break;
                }
                SystemLogManager.getInstance().info("UsersController",
                        "User '" + row.name + "' (" + row.role + ") added", "admin");
                loadUsers();
                updateSummaryCards();
                populateChart();
            } catch (SQLException e) {
                SystemLogManager.getInstance().error("UsersController",
                        "Failed to add user: " + e.getMessage(), "system");
                showAlert("Error", "Failed to save user: " + e.getMessage());
            }
        });
    }

    private void onEditUser(UserRow row) {
        Dialog<UserRow> dialog = createUserDialog(row);
        dialog.showAndWait().ifPresent(updated -> {
            try {
                switch (row.role) {
                    case "Admin":
                        Admin a = adminDAO.getById(row.id);
                        if (a != null) {
                            a.setFullName(updated.name);
                            a.setUsername(updated.username);
                            a.setEmail(updated.email);
                            a.setPhone(updated.phone);
                            a.setActive(updated.active);
                            adminDAO.update(a);
                        }
                        break;
                    case "Manager":
                        Manager m = managerDAO.getById(row.id);
                        if (m != null) {
                            m.setFullName(updated.name);
                            m.setUsername(updated.username);
                            m.setEmail(updated.email);
                            m.setPhone(updated.phone);
                            m.setActive(updated.active);
                            managerDAO.update(m);
                        }
                        break;
                    case "Worker":
                        Worker w = workerDAO.getById(row.id);
                        if (w != null) {
                            w.setFullName(updated.name);
                            w.setEmail(updated.email);
                            w.setPhone(updated.phone);
                            workerDAO.update(w);
                        }
                        break;
                }
                SystemLogManager.getInstance().info("UsersController",
                        "User '" + updated.name + "' (" + row.role + ") updated", "admin");
                loadUsers();
                updateSummaryCards();
                populateChart();
            } catch (SQLException e) {
                showAlert("Error", "Failed to update user: " + e.getMessage());
            }
        });
    }

    private void onDeleteUser(UserRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + row.role + " \"" + row.name + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    switch (row.role) {
                        case "Admin": adminDAO.delete(row.id); break;
                        case "Manager": managerDAO.delete(row.id); break;
                        case "Worker": workerDAO.delete(row.id); break;
                    }
                    SystemLogManager.getInstance().info("UsersController",
                            "User '" + row.name + "' (" + row.role + ") deleted", "admin");
                    loadUsers();
                    updateSummaryCards();
                    populateChart();
                } catch (SQLException e) {
                    showAlert("Error", "Failed to delete: " + e.getMessage());
                }
            }
        });
    }

    // ═══════════════ DIALOG ═══════════════

    private Dialog<UserRow> createUserDialog(UserRow existing) {
        Dialog<UserRow> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add User" : "Edit User");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.name : "");
        nameField.setPromptText("Full Name");

        TextField usernameField = new TextField(existing != null ? existing.username : "");
        usernameField.setPromptText("Username");

        TextField emailField = new TextField(existing != null ? existing.email : "");
        emailField.setPromptText("Email");

        TextField phoneField = new TextField(existing != null ? existing.phone : "");
        phoneField.setPromptText("Phone");

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("Admin", "Manager", "Worker");
        roleCombo.setValue(existing != null ? existing.role : "Worker");
        roleCombo.setMaxWidth(Double.MAX_VALUE);
        if (existing != null) roleCombo.setDisable(true);

        CheckBox activeCheck = new CheckBox("Active");
        activeCheck.setSelected(existing == null || existing.active);

        VBox form = new VBox(10,
                new Label("Full Name:"), nameField,
                new Label("Username:"), usernameField,
                new Label("Email:"), emailField,
                new Label("Phone:"), phoneField,
                new Label("Role:"), roleCombo,
                activeCheck);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        Button saveBtnNode = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveBtnNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (nameField.getText().trim().isEmpty()) {
                showAlert("Validation", "Name is required"); event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                return new UserRow(
                        existing != null ? existing.id : -1,
                        nameField.getText().trim(),
                        usernameField.getText().trim(),
                        emailField.getText().trim(),
                        phoneField.getText().trim(),
                        roleCombo.getValue(),
                        activeCheck.isSelected()
                );
            }
            return null;
        });

        return dialog;
    }

    // ═══════════════ HELPERS ═══════════════

    private String getRoleBadgeStyle(String role) {
        switch (role) {
            case "Admin":   return "-fx-background-color:#dbeafe;-fx-text-fill:#1d4ed8;-fx-padding:2 8;-fx-background-radius:4;-fx-font-size:11;-fx-font-weight:bold;";
            case "Manager": return "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;-fx-padding:2 8;-fx-background-radius:4;-fx-font-size:11;-fx-font-weight:bold;";
            case "Worker":  return "-fx-background-color:#fef3c7;-fx-text-fill:#92400e;-fx-padding:2 8;-fx-background-radius:4;-fx-font-size:11;-fx-font-weight:bold;";
            default:        return "-fx-background-color:#f3f4f6;-fx-text-fill:#374151;-fx-padding:2 8;-fx-background-radius:4;-fx-font-size:11;";
        }
    }

    private String hashPassword(String plain) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return plain;
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }

    // ═══════════════ UNIFIED ROW MODEL ═══════════════

    public static class UserRow {
        public final int id;
        public final String name;
        public final String username;
        public final String email;
        public final String phone;
        public final String role;
        public final boolean active;

        public UserRow(int id, String name, String username, String email, String phone, String role, boolean active) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.role = role;
            this.active = active;
        }
    }
}
