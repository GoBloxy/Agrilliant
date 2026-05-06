package smartfarm.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
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
    @FXML private Label lblTemperature, lblHumidity, lblSoilMoisture, lblLight;
    @FXML private Label lblTempPlot, lblHumPlot, lblSoilPlot, lblLightPlot;
    @FXML private Label lblTempStatus, lblHumStatus, lblSoilStatus, lblLightStatus;

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
    @FXML private Button btnDashboard, btnMonitoring, btnAlerts, btnCrops, btnWorkers,
                          btnTasks, btnHarvests, btnReports, btnSettings, btnUsers, btnLogs,
                          btnCropsCrops, btnCropsPlots;

    private ContextMenu userMenu;
    private Button activeNavButton;

    @FXML
    public void initialize() {
        updateDateTime();
        setupUserMenu();
        subscribeLiveSensor();
        updateSidebarStatus();
        activeNavButton = btnDashboard;
    }

    private void updateDateTime() {
        LocalDateTime now = LocalDateTime.now();
        lblDate.setText(now.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        lblTime.setText(now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
    }

    private void setupUserMenu() {
        userMenu = new ContextMenu();
        MenuItem profile = new MenuItem("Profile");
        MenuItem settings = new MenuItem("Settings");
        MenuItem logout = new MenuItem("Logout");
        userMenu.getItems().addAll(profile, settings, new SeparatorMenuItem(), logout);
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

        live.temperatureProperty().addListener((obs, oldVal, newVal) -> {
            float t = newVal.floatValue();
            lblTemperature.setText(String.format("%.1f °C", t));
            // Update status badge based on threshold
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
        });

        live.humidityProperty().addListener((obs, oldVal, newVal) -> {
            float h = newVal.floatValue();
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
        });

        live.deviceIdProperty().addListener((obs, oldVal, newVal) -> {
            String plot = newVal.replace("_sensor", "").replace("plot", "Plot ");
            lblTempPlot.setText(plot);
            lblHumPlot.setText(plot);
        });
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
    @FXML private void onNavMonitoring() { showPlaceholder("Monitoring",   "fth-activity",     btnMonitoring); }
    @FXML private void onNavAlerts()     { loadFxmlPage("/fxml/alerts.fxml", btnAlerts); }
    @FXML private void onNavCropsList()  { showPlaceholder("Crops",        "fth-feather",      btnCropsCrops); }
    @FXML private void onNavPlotsList()  { showPlaceholder("Plots",        "fth-map",          btnCropsPlots); }
    @FXML private void onNavWorkers()    { showPlaceholder("Workers",      "fth-users",        btnWorkers); }
    @FXML private void onNavTasks()      { showPlaceholder("Tasks",        "fth-check-square", btnTasks); }
    @FXML private void onNavHarvests()   { showPlaceholder("Harvests",     "fth-package",      btnHarvests); }
    @FXML private void onNavReports()    { showPlaceholder("Reports",      "fth-bar-chart-2",  btnReports); }
    @FXML private void onNavSettings()   { showPlaceholder("Settings",     "fth-settings",     btnSettings); }
    @FXML private void onNavUsers()      { showPlaceholder("Users",        "fth-user",         btnUsers); }
    @FXML private void onNavLogs()       { showPlaceholder("Logs",         "fth-file-text",    btnLogs); }

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
        } catch (IOException e) {
            System.err.println("Failed to load " + fxmlPath + ": " + e.getMessage());
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
