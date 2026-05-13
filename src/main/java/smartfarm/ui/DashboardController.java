package smartfarm.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.service.LiveSensorData;
import smartfarm.util.DBConnection;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DashboardController {

    // ── Sensor Cards ──
    @FXML private Label lblTemperature, lblHumidity, lblSoilMoisture;
    @FXML private Label lblTempPlot, lblHumPlot, lblSoilPlot;
    @FXML private Label lblTempStatus, lblHumStatus, lblSoilStatus;

    // ── Chart (placeholder - data filled by SensorService) ──
    @FXML private LineChart<String, Number> sensorChart;

    // ── Tables ──
    @FXML private TableView<?> cropTable, workerTable, harvestTable;

    // ── Lists ──
    @FXML private ListView<String> alertListView, taskListView;

    // ── Top Bar ──
    @FXML private Label lblDate, lblTime, lblTempTop, lblUserName, lblUserRole;
    @FXML private HBox userPill;

    // ── Sidebar Status ──
    @FXML private Label lblSystemStatus, lblDbStatus, lblSensorStatus;
    @FXML private Circle dotSystem, dotDb, dotSensors;

    // ── Navigation ──
    @FXML private StackPane pageContainer;
    @FXML private VBox dashboardPage;
    @FXML private VBox cropsSubmenu;
    @FXML private Button btnDashboard, btnMonitoring, btnDisease, btnAlerts, btnCrops, btnWorkers,
                          btnAttendance, btnTasks, btnHarvests, btnReports, btnSettings,
                          btnUsers, btnLogs, btnCropsCrops, btnCropsPlots;

    private ContextMenu userMenu;
    private Button activeNavButton;
    private smartfarm.model.User currentUser;

    public void setCurrentUser(smartfarm.model.User user) {
        this.currentUser = user;
        if (user != null) {
            lblUserName.setText(user.getFullName());
            lblUserRole.setText(user.getRole().name());
            WorkerController.setCurrentManagerId(user.getId());
            applyRolePermissions(user.getRole());
        }
    }

    private void applyRolePermissions(smartfarm.model.User.Role role) {
        if (role == smartfarm.model.User.Role.WORKER) {
            // Workers can only see: Dashboard, Attendance, Tasks
            btnMonitoring.setVisible(false); btnMonitoring.setManaged(false);
            btnAlerts.setVisible(false);     btnAlerts.setManaged(false);
            btnCrops.setVisible(false);      btnCrops.setManaged(false);
            btnWorkers.setVisible(false);    btnWorkers.setManaged(false);
            btnHarvests.setVisible(false);   btnHarvests.setManaged(false);
            btnReports.setVisible(false);    btnReports.setManaged(false);
            btnSettings.setVisible(false);   btnSettings.setManaged(false);
            btnUsers.setVisible(false);      btnUsers.setManaged(false);
            btnLogs.setVisible(false);       btnLogs.setManaged(false);
        }
    }

    @FXML
    public void initialize() {
        updateDateTime();
        setupUserMenu();
        subscribeLiveSensor();
        updateSidebarStatus();
        activeNavButton = btnDashboard;
    }

    private void updateDateTime() {
        javafx.animation.Timeline clock = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> {
            LocalDateTime now = LocalDateTime.now();
            lblDate.setText(now.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
            lblTime.setText(now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
        }), new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1)));
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        clock.play();
    }

    private void setupUserMenu() {
        userMenu = new ContextMenu();
        MenuItem profile = new MenuItem("About");
        profile.setOnAction(e -> showPage(new AboutPage(), null));
        MenuItem settings = new MenuItem("Settings");
        settings.setOnAction(e -> showPage(new SettingsPage(), btnSettings));
        MenuItem logout = new MenuItem("Logout");
        logout.setOnAction(e -> onLogout());
        userMenu.getItems().addAll(profile, settings, new SeparatorMenuItem(), logout);
    }

    private void onLogout() {
        smartfarm.service.SessionManager.clearSession();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/signin.fxml"));
            Stage stage = (Stage) lblUserName.getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            System.err.println("Logout navigation error: " + e.getMessage());
        }
    }

    private void updateSidebarStatus() {
        // System status — always online if we got this far
        lblSystemStatus.setText("Online");
        dotSystem.getStyleClass().setAll("status-dot-online");

        // Database status — check actual connection
        try {
            Connection conn = DBConnection.getInstance();
            if (conn != null && !conn.isClosed()) {
                lblDbStatus.setText("Connected");
                dotDb.getStyleClass().setAll("status-dot-online");
            } else {
                lblDbStatus.setText("Disconnected");
                dotDb.getStyleClass().setAll("status-dot-offline");
            }
        } catch (SQLException e) {
            lblDbStatus.setText("Error");
            dotDb.getStyleClass().setAll("status-dot-offline");
        }

        // IoT Sensors — listen for live changes
        LiveSensorData live = LiveSensorData.getInstance();
        updateSensorDot(live.activeSensorsProperty().get());
        live.activeSensorsProperty().addListener((obs, oldVal, newVal) ->
            updateSensorDot(newVal.intValue())
        );
    }

    private void updateSensorDot(int count) {
        lblSensorStatus.setText(count + " Active");
        dotSensors.getStyleClass().setAll(count > 0 ? "status-dot-online" : "status-dot-offline");
    }

    private void subscribeLiveSensor() {
        LiveSensorData live = LiveSensorData.getInstance();

        live.temperatureProperty().addListener((obs, oldVal, newVal) -> updateTemperature(newVal.floatValue()));
        live.humidityProperty().addListener((obs, oldVal, newVal) -> updateHumidity(newVal.floatValue()));
        live.soilMoistureProperty().addListener((obs, oldVal, newVal) -> updateSoilMoisture(newVal.floatValue()));
        live.deviceIdProperty().addListener((obs, oldVal, newVal) -> updatePlotLabels(newVal));

        // Display current values immediately (data may have arrived before dashboard loaded)
        float t = live.temperatureProperty().get();
        float h = live.humidityProperty().get();
        float s = live.soilMoistureProperty().get();
        String dev = live.deviceIdProperty().get();
        if (!Float.isNaN(t)) updateTemperature(t);
        if (!Float.isNaN(h)) updateHumidity(h);
        if (!Float.isNaN(s)) updateSoilMoisture(s);
        if (dev != null && !dev.equals("--")) updatePlotLabels(dev);
    }

    private void updateTemperature(float t) {
        lblTemperature.setText(String.format("%.1f °C", t));
        lblTempStatus.getStyleClass().removeAll("badge-normal", "badge-high", "badge-low");
        if (t > 35) {
            lblTempStatus.setText("High");
            lblTempStatus.getStyleClass().add("badge-high");
        } else if (t < 10) {
            lblTempStatus.setText("Low");
            lblTempStatus.getStyleClass().add("badge-low");
        } else {
            lblTempStatus.setText("Normal");
            lblTempStatus.getStyleClass().add("badge-normal");
        }
    }

    private void updateHumidity(float h) {
        lblHumidity.setText(String.format("%.0f %%", h));
        lblHumStatus.getStyleClass().removeAll("badge-normal", "badge-high", "badge-low");
        if (h > 80) {
            lblHumStatus.setText("High");
            lblHumStatus.getStyleClass().add("badge-high");
        } else if (h < 30) {
            lblHumStatus.setText("Low");
            lblHumStatus.getStyleClass().add("badge-low");
        } else {
            lblHumStatus.setText("Normal");
            lblHumStatus.getStyleClass().add("badge-normal");
        }
    }

    private void updateSoilMoisture(float s) {
        lblSoilMoisture.setText(String.format("%.0f %%", s));
        lblSoilStatus.getStyleClass().removeAll("badge-normal", "badge-high", "badge-low");
        if (s < 30) {
            lblSoilStatus.setText("Dry");
            lblSoilStatus.getStyleClass().add("badge-low");
        } else if (s > 85) {
            lblSoilStatus.setText("Wet");
            lblSoilStatus.getStyleClass().add("badge-high");
        } else {
            lblSoilStatus.setText("Normal");
            lblSoilStatus.getStyleClass().add("badge-normal");
        }
    }

    private void updatePlotLabels(String devId) {
        String plot = devId.replace("_sensor", "").replace("plot", "Plot ");
        lblTempPlot.setText(plot);
        lblHumPlot.setText(plot);
        lblSoilPlot.setText(plot);
    }

    @FXML
    private void showUserMenu(MouseEvent event) {
        userMenu.show(userPill, Side.BOTTOM, 0, 5);
    }

    @FXML
    private void toggleCropsSubmenu() {
        boolean visible = !cropsSubmenu.isVisible();
        cropsSubmenu.setVisible(visible);
        cropsSubmenu.setManaged(visible);
    }

    // ═══════════════ NAVIGATION HANDLERS ═══════════════
    @FXML private void onNavDashboard()  { showPage(dashboardPage,  btnDashboard); }
    @FXML private void onNavMonitoring() { loadFxmlPage("/fxml/monitoring.fxml", btnMonitoring); }
    @FXML private void onNavDisease()    { showPage(new DiseaseDetectionPage(), btnDisease); }
    @FXML private void onNavAlerts()     { loadFxmlPage("/fxml/alerts.fxml", btnAlerts); }
    @FXML private void onNavCropsList()  { loadFxmlPage("/fxml/crops.fxml", btnCropsCrops); }
    @FXML private void onNavPlotsList()  { loadFxmlPage("/fxml/plots.fxml", btnCropsPlots); }
    @FXML private void onNavWorkers()    { loadFxmlPage("/fxml/workers.fxml", btnWorkers); }
    @FXML private void onNavAttendance() { showPage(new AttendancePage(), btnAttendance); }
    @FXML private void onNavTasks()      { loadFxmlPage("/fxml/tasks.fxml", btnTasks); }
    @FXML private void onNavHarvests()   { loadFxmlPage("/fxml/harvest.fxml", btnHarvests); }
    @FXML private void onNavReports()    { loadFxmlPage("/fxml/reports.fxml", btnReports); }
    @FXML private void onNavSettings()   { showPage(new SettingsPage(), btnSettings); }
    @FXML private void onNavUsers()      { loadFxmlPage("/fxml/workers.fxml", btnUsers); }
    @FXML private void onNavLogs()       { loadFxmlPage("/fxml/logs.fxml", btnLogs); }

    private void showPage(Node page, Button navBtn) {
        pageContainer.getChildren().setAll(page);
        setActiveNav(navBtn);
    }

    /**
     * Loads an FXML file and displays it in the page container.
     * Falls back to a placeholder if the FXML fails to load.
     */
    private void loadFxmlPage(String fxmlPath, Button navBtn) {
        try {
            Node page = FXMLLoader.load(getClass().getResource(fxmlPath));
            showPage(page, navBtn);
        } catch (Exception e) {
            System.err.println("Failed to load " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
            showPlaceholder("Error", "fth-alert-circle", navBtn);
        }
    }

    private void showPlaceholder(String title, String iconName, Button navBtn) {
        VBox placeholder = new VBox(15);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(60));
        placeholder.getStyleClass().add("placeholder-page");

        FontIcon icon = new FontIcon(iconName);
        icon.setIconSize(56);
        icon.getStyleClass().add("placeholder-icon");

        Label heading = new Label(title);
        heading.getStyleClass().add("placeholder-title");

        Label sub = new Label("This page is coming soon.");
        sub.getStyleClass().add("placeholder-subtitle");

        placeholder.getChildren().addAll(icon, heading, sub);
        showPage(placeholder, navBtn);
    }

    private void setActiveNav(Button btn) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-active");
        }
        if (btn != null && !btn.getStyleClass().contains("nav-active")) {
            btn.getStyleClass().add("nav-active");
        }
        activeNavButton = btn;
    }
}
