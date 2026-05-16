package smartfarm.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.*;
import smartfarm.model.Alert;
import smartfarm.model.Crop;
import smartfarm.model.HarvestRecord;
import smartfarm.model.Plot;
import smartfarm.model.SensorReading;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SystemLogManager;
import smartfarm.util.DBConnection;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardController {

    // ── Sensor Cards ──
    @FXML private Label lblTemperature, lblHumidity, lblSoilMoisture, lblLightLevel;
    @FXML private Label lblTempPlot, lblHumPlot, lblSoilPlot, lblLightPlot;
    @FXML private Label lblTempStatus, lblHumStatus, lblSoilStatus, lblLightStatus;

    // ── Field Map ──
    @FXML private Pane fieldMap;

    // ── Chart (real-time JavaFX LineChart) ──
    @FXML private StackPane chartContainer;
    private LineChart<String, Number> sensorChart;
    private XYChart.Series<String, Number> tempSeries;
    private XYChart.Series<String, Number> humSeries;
    private XYChart.Series<String, Number> soilSeries;
    private int dataPointIndex = 0;
    private static final int MAX_DATA_POINTS = 40;
    private float lastTempCelsius = Float.NaN;

    // ── Tables ──
    @FXML private TableView<Crop> cropTable;
    @FXML private TableView<Worker> workerTable;
    @FXML private TableView<HarvestRecord> harvestTable;
    @FXML private TableColumn<Crop, String> colCropName, colCropPlot, colCropStage, colPlantingDate, colCropStatus;
    @FXML private TableColumn<Worker, String> colWorkerName, colWorkerRole, colWorkerStatus, colWorkerTask, colWorkload;
    @FXML private TableColumn<HarvestRecord, String> colHarvestCrop, colHarvestPlot, colHarvestDate, colHarvestQty, colHarvestGrade;

    // ── Lists ──
    @FXML private ListView<String> alertListView, taskListView;
    @FXML private Label lblTasksTitle;

    // ── Top Bar ──
    @FXML private Label lblDate, lblTime, lblTempTop, lblWeatherDesc, lblUserName, lblUserRole;
    @FXML private HBox userPill;

    // ── Sidebar Status ──
    @FXML private Label lblSystemStatus, lblDbStatus, lblSensorStatus;
    @FXML private Circle dotSystem, dotDb, dotSensors;

    // ── Sensor Refresh ──
    @FXML private Button btnRefreshSensors;

    // ── Navigation ──
    @FXML private StackPane pageContainer;
    @FXML private VBox dashboardPage;
    @FXML private VBox cropsSubmenu;
    @FXML private Button btnDashboard, btnMonitoring, btnDisease, btnAlerts, btnCrops, btnWorkers,
                          btnAttendance, btnTasks, btnHarvests, btnReports, btnSettings,
                          btnUsers, btnLogs, btnCropsCrops, btnCropsPlots;

    // ── Hyperlinks ──
    @FXML private Hyperlink linkViewSensors, linkShowAllAlerts,
                             linkViewAllTasks, linkViewCrops,
                             linkViewWorkers, linkViewHarvests, linkManagePlots;
    @FXML private VBox workerOverviewCard, quickActionsCard;
    @FXML private HBox bottomSection;

    private ContextMenu userMenu;
    private Button activeNavButton;
    private smartfarm.model.User currentUser;

    // DAOs
    private final AlertDAO alertDAO = new AlertDAO();
    private final TaskDAO taskDAO = new TaskDAO();
    private final CropDAO cropDAO = new CropDAO();
    private final PlotDAO plotDAO = new PlotDAO();
    private final WorkerDAO workerDAO = new WorkerDAO();
    private final HarvestDAO harvestDAO = new HarvestDAO();
    private final SensorDAO sensorDAO = new SensorDAO();

    // Cached data
    private List<Plot> allPlots;
    private List<Crop> allCrops;
    private List<Task> allTasks;

    public void setCurrentUser(smartfarm.model.User user) {
        this.currentUser = user;
        if (user != null) {
            lblUserName.setText(user.getFullName());
            lblUserRole.setText(user.getRole().name());
            WorkerController.setCurrentManagerId(user.getId());
            PlotController.setCurrentManagerId(user.getId());
            applyRolePermissions(user.getRole());
            AlertController.setCurrentManagerId(user.getId());
            if (user.getRole() == smartfarm.model.User.Role.WORKER) {
                lblTasksTitle.setText("My Tasks");
                TaskController.setWorkerMode(user.getId());
                AlertController.setWorkerMode(user.getId());
                loadTasks(); // reload filtered for this worker
            } else {
                TaskController.clearWorkerMode();
                AlertController.clearWorkerMode();
            }
        }
    }

    private void applyRolePermissions(smartfarm.model.User.Role role) {
        if (role == smartfarm.model.User.Role.WORKER) {
            // Sidebar: workers can see Dashboard, Monitoring, Disease, Alerts, Attendance, Tasks
            btnCrops.setVisible(false);      btnCrops.setManaged(false);
            btnWorkers.setVisible(false);    btnWorkers.setManaged(false);
            btnHarvests.setVisible(false);   btnHarvests.setManaged(false);
            btnReports.setVisible(false);    btnReports.setManaged(false);
            btnSettings.setVisible(false);   btnSettings.setManaged(false);
            btnUsers.setVisible(false);      btnUsers.setManaged(false);
            btnLogs.setVisible(false);       btnLogs.setManaged(false);

            // Dashboard: hide management-only sections
            workerOverviewCard.setVisible(false); workerOverviewCard.setManaged(false);
            quickActionsCard.setVisible(false);   quickActionsCard.setManaged(false);
            bottomSection.setVisible(false);      bottomSection.setManaged(false);

            // Hyperlinks: hide management links
            linkViewCrops.setVisible(false);    linkViewCrops.setManaged(false);
            linkViewWorkers.setVisible(false);  linkViewWorkers.setManaged(false);
            linkViewHarvests.setVisible(false); linkViewHarvests.setManaged(false);
            linkManagePlots.setVisible(false);  linkManagePlots.setManaged(false);

            // User menu: remove Settings for workers
            userMenu.getItems().removeIf(item -> "Settings".equals(item.getText()));
        }
        if (role == smartfarm.model.User.Role.MANAGER) {
            btnUsers.setVisible(false);      btnUsers.setManaged(false);
            btnLogs.setVisible(false);       btnLogs.setManaged(false);
        }
        if (role == smartfarm.model.User.Role.ADMIN) {
            // Sidebar: Admin is an IT Supervisor, hide farm operations tabs
            btnMonitoring.setVisible(false); btnMonitoring.setManaged(false);
            btnDisease.setVisible(false);    btnDisease.setManaged(false);
            btnAlerts.setVisible(false);     btnAlerts.setManaged(false);
            btnCrops.setVisible(false);      btnCrops.setManaged(false);
            btnWorkers.setVisible(false);    btnWorkers.setManaged(false);
            btnTasks.setVisible(false);      btnTasks.setManaged(false);
            btnHarvests.setVisible(false);   btnHarvests.setManaged(false);
            
            // Dashboard: hide management-only sections to keep it clean
            workerOverviewCard.setVisible(false); workerOverviewCard.setManaged(false);
            quickActionsCard.setVisible(false);   quickActionsCard.setManaged(false);
            bottomSection.setVisible(false);      bottomSection.setManaged(false);
        }
    }

    @FXML
    public void initialize() {
        updateDateTime();
        setupUserMenu();
        setupChart();
        setupHyperlinks();
        subscribeLiveSensor();
        updateSidebarStatus();
        loadDashboardData();
        refreshSensorLabels();   // Populate cards immediately from LiveSensorData or DB
        activeNavButton = btnDashboard;

        // Log DB connection status
        try {
            Connection c = DBConnection.getInstance();
            if (c != null && !c.isClosed()) {
                SystemLogManager.getInstance().info("DBConnection", "Database connected successfully", "system");
            }
        } catch (SQLException ignored) {
            SystemLogManager.getInstance().error("DBConnection", "Database connection failed", "system");
        }
    }

    // ═══════════════ DATA LOADING ═══════════════

    private void loadDashboardData() {
        loadAlerts();
        loadTasks();
        loadCropTable();
        loadWorkerTable();
        loadHarvestTable();
        buildFieldMap();
    }

    private void loadAlerts() {
        try {
            List<Alert> alerts = alertDAO.getUnresolved();
            ObservableList<String> items = FXCollections.observableArrayList();
            int limit = Math.min(alerts.size(), 8);
            for (int i = 0; i < limit; i++) {
                Alert a = alerts.get(i);
                String icon = switch (a.getSeverity()) {
                    case CRITICAL -> "🔴";
                    case WARNING -> "🟡";
                    case INFO -> "🔵";
                };
                items.add(icon + " [" + a.getAlertType() + "] " + a.getMessage());
            }
            if (items.isEmpty()) items.add("No active alerts");
            alertListView.setItems(items);
        } catch (SQLException e) {
            alertListView.setItems(FXCollections.observableArrayList("Failed to load alerts"));
        }
    }

    private void loadTasks() {
        try {
            allTasks = taskDAO.getAll();
            boolean isWorker = currentUser != null && currentUser.getRole() == smartfarm.model.User.Role.WORKER;
            int workerId = isWorker ? currentUser.getId() : -1;
            ObservableList<String> items = FXCollections.observableArrayList();
            int count = 0;
            for (Task t : allTasks) {
                if (t.getStatus() == Task.Status.DONE) continue;
                if (isWorker && !t.getWorkerIds().contains(workerId)) continue;
                if (count >= 8) break;
                String statusIcon = t.getStatus() == Task.Status.IN_PROGRESS ? "🔄" : "⏳";
                String due = t.getDueDate() != null ? " (due " + t.getDueDate() + ")" : "";
                items.add(statusIcon + " " + t.getDescription() + due);
                count++;
            }
            if (items.isEmpty()) items.add(isWorker ? "No assigned tasks" : "No pending tasks");
            taskListView.setItems(items);
        } catch (SQLException e) {
            taskListView.setItems(FXCollections.observableArrayList("Failed to load tasks"));
        }
    }

    private void loadCropTable() {
        try {
            allCrops = cropDAO.getAll();
            allPlots = plotDAO.getAll();
            Map<Integer, String> plotMap = new HashMap<>();
            for (Plot p : allPlots) plotMap.put(p.getPlotId(), p.getName());

            colCropName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCropName()));
            colCropPlot.setCellValueFactory(d -> new SimpleStringProperty(plotMap.getOrDefault(d.getValue().getPlotId(), "Plot " + d.getValue().getPlotId())));
            colCropStage.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGrowthStage().name()));
            colPlantingDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPlantingDate() != null ? d.getValue().getPlantingDate().toString() : "—"));
            colCropStatus.setCellValueFactory(d -> {
                Crop c = d.getValue();
                String status;
                if (c.getGrowthStage() == Crop.GrowthStage.HARVESTED) status = "Harvested";
                else if (c.isOverdue()) status = "At Risk";
                else status = "Growing";
                return new SimpleStringProperty(status);
            });

            ObservableList<Crop> cropData = FXCollections.observableArrayList(allCrops.subList(0, Math.min(allCrops.size(), 10)));
            cropTable.setItems(cropData);
        } catch (SQLException e) {
            System.err.println("Failed to load crop table: " + e.getMessage());
        }
    }

    private void loadWorkerTable() {
        try {
            List<Worker> workers = workerDAO.getAll();
            if (allTasks == null) allTasks = taskDAO.getAll();

            colWorkerName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullName()));
            colWorkerRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getJobTitle()));
            colWorkerStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isOnDuty() ? "On Duty" : "Off"));
            colWorkerTask.setCellValueFactory(d -> {
                Worker w = d.getValue();
                String task = allTasks.stream()
                    .filter(t -> t.getStatus() != Task.Status.DONE && t.getWorkerIds().contains(w.getWorkerId()))
                    .map(Task::getDescription)
                    .findFirst().orElse("—");
                return new SimpleStringProperty(task.length() > 20 ? task.substring(0, 20) + "…" : task);
            });
            colWorkload.setCellValueFactory(d -> {
                int active = d.getValue().getActiveTaskCount(allTasks);
                return new SimpleStringProperty(active + " task" + (active != 1 ? "s" : ""));
            });

            ObservableList<Worker> workerData = FXCollections.observableArrayList(workers.subList(0, Math.min(workers.size(), 10)));
            workerTable.setItems(workerData);
        } catch (SQLException e) {
            System.err.println("Failed to load worker table: " + e.getMessage());
        }
    }

    private void loadHarvestTable() {
        try {
            List<HarvestRecord> records = harvestDAO.getAll();
            if (allCrops == null) allCrops = cropDAO.getAll();
            if (allPlots == null) allPlots = plotDAO.getAll();

            Map<Integer, Crop> cropMap = new HashMap<>();
            for (Crop c : allCrops) cropMap.put(c.getCropId(), c);
            Map<Integer, String> plotMap = new HashMap<>();
            for (Plot p : allPlots) plotMap.put(p.getPlotId(), p.getName());

            colHarvestCrop.setCellValueFactory(d -> {
                Crop c = cropMap.get(d.getValue().getCropId());
                return new SimpleStringProperty(c != null ? c.getCropName() : "Crop #" + d.getValue().getCropId());
            });
            colHarvestPlot.setCellValueFactory(d -> {
                Crop c = cropMap.get(d.getValue().getCropId());
                return new SimpleStringProperty(c != null ? plotMap.getOrDefault(c.getPlotId(), "—") : "—");
            });
            colHarvestDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getHarvestDate() != null ? d.getValue().getHarvestDate().toString() : "—"));
            colHarvestQty.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.1f", d.getValue().getQuantityKg())));
            colHarvestGrade.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGrade().name()));

            ObservableList<HarvestRecord> harvestData = FXCollections.observableArrayList(records.subList(0, Math.min(records.size(), 10)));
            harvestTable.setItems(harvestData);
        } catch (SQLException e) {
            System.err.println("Failed to load harvest table: " + e.getMessage());
        }
    }

    // ═══════════════ FIELD MAP (DYNAMIC FROM DB) ═══════════════

    private void buildFieldMap() {
        fieldMap.getChildren().clear();
        try {
            if (allPlots == null) allPlots = plotDAO.getAll();
            if (allPlots.isEmpty()) {
                Label empty = new Label("No plots defined");
                empty.setStyle("-fx-text-fill:#9ca3af;-fx-font-size:12;");
                empty.setLayoutX(80);
                empty.setLayoutY(150);
                fieldMap.getChildren().add(empty);
                return;
            }

            double mapW = fieldMap.getPrefWidth() > 0 ? fieldMap.getPrefWidth() : 260;
            double mapH = fieldMap.getPrefHeight() > 0 ? fieldMap.getPrefHeight() : 330;
            int count = allPlots.size();
            int cols = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
            int rows = (int) Math.ceil((double) count / cols);

            double pad = 12;
            double cellW = (mapW - pad * (cols + 1)) / cols;
            double cellH = (mapH - pad * (rows + 1)) / rows;

            // Check latest sensor readings for each plot to determine status
            SensorDAO sensorDAO = new SensorDAO();
            Map<Integer, String> plotStatus = new HashMap<>();
            for (Plot p : allPlots) {
                try {
                    List<SensorReading> recent = sensorDAO.getRecentForDevice(p.getPlotId(), 1);
                    if (recent.isEmpty()) {
                        plotStatus.put(p.getPlotId(), "offline");
                    } else {
                        SensorReading r = recent.get(0);
                        float t = r.getTemperature();
                        float h = r.getHumidity();
                        float s = r.getSoilMoisture();
                        boolean crit = t > 40 || t < 5 || h > 90 || h < 20 || (!Float.isNaN(s) && (s < 15 || s > 95));
                        boolean warn = t > 35 || t < 10 || h > 80 || h < 30 || (!Float.isNaN(s) && (s < 30 || s > 85));
                        plotStatus.put(p.getPlotId(), crit ? "critical" : warn ? "warning" : "normal");
                    }
                } catch (Exception e) {
                    plotStatus.put(p.getPlotId(), "offline");
                }
            }

            for (int i = 0; i < count; i++) {
                Plot p = allPlots.get(i);
                int col = i % cols;
                int row = i / cols;
                double x = pad + col * (cellW + pad);
                double y = pad + row * (cellH + pad);

                Rectangle rect = new Rectangle(cellW, cellH);
                rect.setArcWidth(8);
                rect.setArcHeight(8);
                String status = plotStatus.getOrDefault(p.getPlotId(), "offline");
                rect.getStyleClass().add("plot-" + status);
                rect.setLayoutX(x);
                rect.setLayoutY(y);

                Label lbl = new Label(p.getName());
                lbl.setStyle("-fx-font-size:11;-fx-font-weight:bold;-fx-text-fill:#1f2937;");
                lbl.setLayoutX(x + cellW / 2 - 20);
                lbl.setLayoutY(y + cellH / 2 - 8);

                Label sizeLbl = new Label(String.format("%.1f ac", p.getSizeAcres()));
                sizeLbl.setStyle("-fx-font-size:9;-fx-text-fill:#6b7280;");
                sizeLbl.setLayoutX(x + cellW / 2 - 15);
                sizeLbl.setLayoutY(y + cellH / 2 + 8);

                fieldMap.getChildren().addAll(rect, lbl, sizeLbl);
            }
        } catch (SQLException e) {
            System.err.println("Failed to build field map: " + e.getMessage());
        }
    }

    // ═══════════════ HYPERLINK NAVIGATION ═══════════════

    private void setupHyperlinks() {
        linkViewSensors.setOnAction(e -> loadFxmlPage("/fxml/monitoring.fxml", btnMonitoring));
        linkShowAllAlerts.setOnAction(e -> loadFxmlPage("/fxml/alerts.fxml", btnAlerts));
        linkViewAllTasks.setOnAction(e -> loadFxmlPage("/fxml/tasks.fxml", btnTasks));
        linkViewCrops.setOnAction(e -> loadFxmlPage("/fxml/crops.fxml", btnCropsCrops));
        linkViewWorkers.setOnAction(e -> loadFxmlPage("/fxml/workers.fxml", btnWorkers));
        linkViewHarvests.setOnAction(e -> loadFxmlPage("/fxml/harvest.fxml", btnHarvests));
        linkManagePlots.setOnAction(e -> loadFxmlPage("/fxml/plots.fxml", btnCropsPlots));
    }

    // ═══════════════ QUICK ACTIONS ═══════════════

    @FXML
    private void onAddCrop() {
        Dialog<Crop> dialog = new Dialog<>();
        dialog.setTitle("Add Crop");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Crop Name");

        ComboBox<String> plotCombo = new ComboBox<>();
        plotCombo.setPromptText("Select Plot");
        plotCombo.setMaxWidth(Double.MAX_VALUE);
        Map<String, Integer> plotNameToId = new HashMap<>();
        try {
            List<Plot> plots = (allPlots != null) ? allPlots : plotDAO.getAll();
            for (Plot p : plots) {
                String label = p.getName() + " (" + p.getLocation() + ")";
                plotCombo.getItems().add(label);
                plotNameToId.put(label, p.getPlotId());
            }
        } catch (SQLException ignored) {}

        DatePicker plantedPicker = new DatePicker(LocalDate.now());
        DatePicker harvestPicker = new DatePicker(LocalDate.now().plusMonths(3));

        TextField yieldField = new TextField();
        yieldField.setPromptText("Expected Yield (kg)");

        ComboBox<Crop.GrowthStage> stageCombo = new ComboBox<>();
        stageCombo.getItems().addAll(Crop.GrowthStage.values());
        stageCombo.setValue(Crop.GrowthStage.PLANTED);
        stageCombo.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(10,
                new Label("Crop Name:"), nameField,
                new Label("Plot:"), plotCombo,
                new Label("Planted Date:"), plantedPicker,
                new Label("Harvest Date:"), harvestPicker,
                new Label("Expected Yield (kg):"), yieldField,
                new Label("Growth Stage:"), stageCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        Button saveBtnNode = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveBtnNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (nameField.getText().trim().isEmpty()) {
                showQuickAlert("Crop name is required"); event.consume(); return;
            }
            String selPlot = plotCombo.getValue();
            if (selPlot == null || !plotNameToId.containsKey(selPlot)) {
                showQuickAlert("Please select a plot"); event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                String selPlot = plotCombo.getValue();
                double yield = 0;
                try { yield = Double.parseDouble(yieldField.getText().trim()); } catch (NumberFormatException ignored) {}
                Crop crop = new Crop(name, plantedPicker.getValue(), harvestPicker.getValue(), plotNameToId.get(selPlot), yield);
                crop.setGrowthStage(stageCombo.getValue());
                return crop;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(crop -> {
            try {
                cropDAO.save(crop);
                loadCropTable();
            } catch (SQLException e) {
                showQuickAlert("Failed to save crop: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onAddTask() {
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle("Add Task");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField descField = new TextField();
        descField.setPromptText("Task Description");

        DatePicker duePicker = new DatePicker(LocalDate.now().plusDays(1));

        ComboBox<String> plotCombo = new ComboBox<>();
        plotCombo.setPromptText("Select Plot");
        plotCombo.setMaxWidth(Double.MAX_VALUE);
        Map<String, Integer> plotNameToId = new HashMap<>();
        try {
            List<Plot> plots = (allPlots != null) ? allPlots : plotDAO.getAll();
            for (Plot p : plots) {
                String label = p.getName() + " (" + p.getLocation() + ")";
                plotCombo.getItems().add(label);
                plotNameToId.put(label, p.getPlotId());
            }
        } catch (SQLException ignored) {}

        ComboBox<String> workerCombo = new ComboBox<>();
        workerCombo.setPromptText("Assign Worker");
        workerCombo.setMaxWidth(Double.MAX_VALUE);
        Map<String, Integer> workerNameToId = new HashMap<>();
        try {
            List<Worker> workers = workerDAO.getAll();
            for (Worker w : workers) {
                String label = w.getFullName() + " (" + (w.getJobTitle() != null ? w.getJobTitle() : "Worker") + ")";
                workerCombo.getItems().add(label);
                workerNameToId.put(label, w.getWorkerId());
            }
        } catch (SQLException ignored) {}

        VBox form = new VBox(10,
                new Label("Description:"), descField,
                new Label("Due Date:"), duePicker,
                new Label("Plot:"), plotCombo,
                new Label("Assign To:"), workerCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        Button saveBtnNode2 = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveBtnNode2.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (descField.getText().trim().isEmpty()) {
                showQuickAlert("Description is required"); event.consume(); return;
            }
            String selPlot = plotCombo.getValue();
            if (selPlot == null || !plotNameToId.containsKey(selPlot)) {
                showQuickAlert("Please select a plot"); event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String desc = descField.getText().trim();
                String selPlot = plotCombo.getValue();
                int mgrId = (currentUser != null) ? currentUser.getId() : 1;
                Task task = new Task(desc, duePicker.getValue(), plotNameToId.get(selPlot), null, mgrId, null);
                String selectedWorker = workerCombo.getValue();
                if (selectedWorker != null && workerNameToId.containsKey(selectedWorker)) {
                    task.addWorker(workerNameToId.get(selectedWorker));
                }
                return task;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(task -> {
            try {
                taskDAO.save(task);
                loadTasks();
            } catch (SQLException e) {
                showQuickAlert("Failed to save task: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onLogHarvest() {
        Dialog<HarvestRecord> dialog = new Dialog<>();
        dialog.setTitle("Log Harvest");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        ComboBox<String> cropCombo = new ComboBox<>();
        cropCombo.setPromptText("Select Crop");
        cropCombo.setMaxWidth(Double.MAX_VALUE);
        Map<String, Integer> cropNameToId = new HashMap<>();
        try {
            List<Crop> crops = (allCrops != null) ? allCrops : cropDAO.getAll();
            for (Crop c : crops) {
                String label = c.getCropName() + " (Plot " + c.getPlotId() + ")";
                cropCombo.getItems().add(label);
                cropNameToId.put(label, c.getCropId());
            }
        } catch (SQLException ignored) {}

        TextField qtyField = new TextField();
        qtyField.setPromptText("Quantity (kg)");

        DatePicker datePicker = new DatePicker(LocalDate.now());

        ComboBox<HarvestRecord.Grade> gradeCombo = new ComboBox<>();
        gradeCombo.getItems().addAll(HarvestRecord.Grade.values());
        gradeCombo.setValue(HarvestRecord.Grade.A);
        gradeCombo.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(10,
                new Label("Crop:"), cropCombo,
                new Label("Quantity (kg):"), qtyField,
                new Label("Date:"), datePicker,
                new Label("Quality:"), gradeCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        Button saveBtnNode3 = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveBtnNode3.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String selected = cropCombo.getValue();
            if (selected == null || !cropNameToId.containsKey(selected)) {
                showQuickAlert("Please select a crop"); event.consume(); return;
            }
            try {
                double qty = Double.parseDouble(qtyField.getText().trim());
                if (qty <= 0) { showQuickAlert("Quantity must be > 0"); event.consume(); }
            } catch (NumberFormatException e) {
                showQuickAlert("Invalid quantity"); event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String selected = cropCombo.getValue();
                double qty = Double.parseDouble(qtyField.getText().trim());
                return new HarvestRecord(datePicker.getValue(), qty,
                        gradeCombo.getValue(), cropNameToId.get(selected));
            }
            return null;
        });

        dialog.showAndWait().ifPresent(record -> {
            try {
                harvestDAO.save(record);
                loadHarvestTable();
            } catch (SQLException e) {
                showQuickAlert("Failed to save harvest: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onExportReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Harvest Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("harvest_report.csv");
        File file = chooser.showSaveDialog(chartContainer.getScene().getWindow());
        if (file == null) return;

        try {
            List<HarvestRecord> harvests = harvestDAO.getAll();
            List<Crop> crops = (allCrops != null) ? allCrops : cropDAO.getAll();
            List<Plot> plots = (allPlots != null) ? allPlots : plotDAO.getAll();

            Map<Integer, Crop> cropMap = new HashMap<>();
            for (Crop c : crops) cropMap.put(c.getCropId(), c);
            Map<Integer, String> plotMap = new HashMap<>();
            for (Plot p : plots) plotMap.put(p.getPlotId(), p.getName());

            try (FileWriter writer = new FileWriter(file)) {
                writer.write("Date,Crop,Plot,Qty (kg),Grade\n");
                for (HarvestRecord hr : harvests) {
                    Crop c = cropMap.get(hr.getCropId());
                    String cropName = (c != null) ? c.getCropName() : "Crop #" + hr.getCropId();
                    String plotName = (c != null) ? plotMap.getOrDefault(c.getPlotId(), "—") : "—";
                    writer.write(String.format("%s,%s,%s,%.1f,%s\n",
                            hr.getHarvestDate(), cropName, plotName,
                            hr.getQuantityKg(), hr.getGrade().name()));
                }
            }
            showExportSuccess("Report exported successfully!");
        } catch (SQLException | IOException e) {
            showExportError("Failed to export: " + e.getMessage());
        }
    }

    private void showQuickAlert(String msg) {
        javafx.scene.control.Alert dlg = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.WARNING, msg, ButtonType.OK);
        dlg.setTitle("Quick Action");
        dlg.setHeaderText(null);
        dlg.showAndWait();
    }

    // ═══════════════ CSV EXPORT ═══════════════

    @FXML
    private void onExportSensorCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Sensor Logs");
        fc.setInitialFileName("sensor_logs.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(chartContainer.getScene().getWindow());
        if (file == null) return;
        try {
            List<SensorReading> readings = sensorDAO.getRecent(500);
            try (FileWriter fw = new FileWriter(file)) {
                fw.write("ReadingID,DeviceID,Temperature,Humidity,SoilMoisture,LightLevel,Timestamp\n");
                for (SensorReading r : readings) {
                    fw.write(String.format("%d,%d,%.2f,%.2f,%.2f,%.2f,%s\n",
                        r.getReadingId(), r.getDeviceId(), r.getTemperature(),
                        r.getHumidity(), r.getSoilMoisture(), r.getLightLevel(), r.getTimestamp()));
                }
            }
            showExportSuccess("Sensor logs exported successfully!");
        } catch (SQLException | IOException ex) {
            showExportError("Failed to export sensor logs: " + ex.getMessage());
        }
    }

    @FXML
    private void onExportHarvestCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Harvest Summary");
        fc.setInitialFileName("harvest_summary.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(chartContainer.getScene().getWindow());
        if (file == null) return;
        try {
            List<HarvestRecord> records = harvestDAO.getAll();
            try (FileWriter fw = new FileWriter(file)) {
                fw.write("RecordID,CropID,HarvestDate,QuantityKg,Grade\n");
                for (HarvestRecord r : records) {
                    fw.write(String.format("%d,%d,%s,%.2f,%s\n",
                        r.getRecordId(), r.getCropId(), r.getHarvestDate(),
                        r.getQuantityKg(), r.getGrade().name()));
                }
            }
            showExportSuccess("Harvest summary exported successfully!");
        } catch (SQLException | IOException ex) {
            showExportError("Failed to export harvest data: " + ex.getMessage());
        }
    }

    @FXML
    private void onExportAlertCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Alert History");
        fc.setInitialFileName("alert_history.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(chartContainer.getScene().getWindow());
        if (file == null) return;
        try {
            List<Alert> alerts = alertDAO.getAll();
            try (FileWriter fw = new FileWriter(file)) {
                fw.write("AlertID,Type,Severity,Message,Resolved,Timestamp,PlotID\n");
                for (Alert a : alerts) {
                    fw.write(String.format("%d,%s,%s,\"%s\",%b,%s,%d\n",
                        a.getAlertId(), a.getAlertType(), a.getSeverity().name(),
                        a.getMessage().replace("\"", "\"\""), a.isResolved(),
                        a.getTimestamp(), a.getPlotId()));
                }
            }
            showExportSuccess("Alert history exported successfully!");
        } catch (SQLException | IOException ex) {
            showExportError("Failed to export alert history: " + ex.getMessage());
        }
    }

    private void showExportSuccess(String msg) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert dlg = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            dlg.setTitle("Export Complete");
            dlg.setHeaderText(null);
            dlg.setContentText(msg);
            dlg.showAndWait();
        });
    }

    private void showExportError(String msg) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert dlg = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            dlg.setTitle("Export Failed");
            dlg.setHeaderText(null);
            dlg.setContentText(msg);
            dlg.showAndWait();
        });
    }

    // ═══════════════ CHART SETUP ═══════════════

    @SuppressWarnings("unchecked")
    private void setupChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Time");
        xAxis.setAnimated(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Value");
        yAxis.setAnimated(false);
        yAxis.setAutoRanging(true);

        sensorChart = new LineChart<>(xAxis, yAxis);
        sensorChart.setAnimated(false);
        sensorChart.setCreateSymbols(false);
        sensorChart.setLegendVisible(true);
        sensorChart.setPrefHeight(200);
        sensorChart.setMaxHeight(200);

        tempSeries = new XYChart.Series<>();
        tempSeries.setName(smartfarm.service.SettingsManager.getInstance().isUseFahrenheit() ? "Temperature (°F)" : "Temperature (°C)");
        humSeries = new XYChart.Series<>();
        humSeries.setName("Humidity (%)");
        soilSeries = new XYChart.Series<>();
        soilSeries.setName("Soil Moisture (%)");

        sensorChart.getData().addAll(tempSeries, humSeries, soilSeries);
        chartContainer.getChildren().add(sensorChart);

        loadHistoricalChartData();
    }

    private void loadHistoricalChartData() {
        try {
            List<SensorReading> readings = sensorDAO.getRecent(MAX_DATA_POINTS);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            for (int i = readings.size() - 1; i >= 0; i--) {
                SensorReading r = readings.get(i);
                String label = r.getTimestamp() != null ? r.getTimestamp().format(fmt) : String.valueOf(i);
                tempSeries.getData().add(new XYChart.Data<>(label, r.getTemperature()));
                humSeries.getData().add(new XYChart.Data<>(label, r.getHumidity()));
                if (!Float.isNaN(r.getSoilMoisture())) {
                    soilSeries.getData().add(new XYChart.Data<>(label, r.getSoilMoisture()));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load historical chart data: " + e.getMessage());
        }
    }

    // ═══════════════ DATE/TIME ═══════════════

    private void updateDateTime() {
        javafx.animation.Timeline clock = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> {
            LocalDateTime now = LocalDateTime.now();
            lblDate.setText(now.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
            lblTime.setText(now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
        }), new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1)));
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        clock.play();
    }

    // ═══════════════ USER MENU ═══════════════

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
        Stage stage = (Stage) lblUserName.getScene().getWindow();
        stage.getScene().setRoot(new SignInView());
    }

    // ═══════════════ SIDEBAR STATUS ═══════════════

    private void updateSidebarStatus() {
        lblSystemStatus.setText("Online");
        dotSystem.getStyleClass().setAll("status-dot-online");

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

        // Query DB for devices with ONLINE status
        LiveSensorData live = LiveSensorData.getInstance();
        int dbOnline = 0;
        try {
            dbOnline = new smartfarm.dao.DeviceDAO().countByStatus("ONLINE");
        } catch (SQLException ignored) {}
        int initialCount = Math.max(dbOnline, live.activeSensorsProperty().get());
        updateSensorDot(initialCount);
        live.activeSensorsProperty().addListener((obs, oldVal, newVal) -> {
            int liveCount = newVal.intValue();
            int fromDb = 0;
            try { fromDb = new smartfarm.dao.DeviceDAO().countByStatus("ONLINE"); } catch (SQLException ignored) {}
            updateSensorDot(Math.max(liveCount, fromDb));
        });
    }

    private void updateSensorDot(int count) {
        lblSensorStatus.setText(count > 0 ? "Active" : "Inactive");
        dotSensors.getStyleClass().setAll(count > 0 ? "status-dot-online" : "status-dot-offline");
    }

    // ═══════════════ LIVE SENSOR SUBSCRIPTION ═══════════════

    // ═══════════════ SENSOR REFRESH ═══════════════

    @FXML
    private void onRefreshSensors() {
        refreshSensorLabels();
    }

    /**
     * Populates the Live Environmental Monitor cards.
     * Always queries the DB first — the DB is shared across all machines so remote
     * clients get the latest persisted reading even if their LiveSensorData is stale.
     * Falls back to in-memory LiveSensorData if the DB returns nothing.
     */
    private void refreshSensorLabels() {
        try {
            List<SensorReading> recent = sensorDAO.getRecent(1);
            if (!recent.isEmpty()) {
                SensorReading r = recent.get(0);
                updateTemperature(r.getTemperature());
                updateHumidity(r.getHumidity());
                if (!Float.isNaN(r.getSoilMoisture())) updateSoilMoisture(r.getSoilMoisture());
                if (!Float.isNaN(r.getLightLevel())) updateLightLevel(r.getLightLevel());
                return;
            }
        } catch (SQLException e) {
            System.err.println("refreshSensorLabels: DB query failed: " + e.getMessage());
        }

        // Fallback to in-memory live data
        LiveSensorData live = LiveSensorData.getInstance();
        float t = live.temperatureProperty().get();
        float h = live.humidityProperty().get();
        float s = live.soilMoistureProperty().get();
        String dev = live.deviceIdProperty().get();
        if (!Float.isNaN(t)) updateTemperature(t);
        if (!Float.isNaN(h)) updateHumidity(h);
        if (!Float.isNaN(s)) updateSoilMoisture(s);
        float l0 = live.lightLevelProperty().get();
        if (!Float.isNaN(l0)) updateLightLevel(l0);
        if (dev != null && !dev.equals("--")) updatePlotLabels(dev);
    }

    private void subscribeLiveSensor() {
        LiveSensorData live = LiveSensorData.getInstance();

        live.temperatureProperty().addListener((obs, oldVal, newVal) -> {
            updateTemperature(newVal.floatValue());
            updateSensorDot(live.activeSensorsProperty().get());
        });
        live.humidityProperty().addListener((obs, oldVal, newVal) -> updateHumidity(newVal.floatValue()));
        live.soilMoistureProperty().addListener((obs, oldVal, newVal) -> updateSoilMoisture(newVal.floatValue()));
        live.lightLevelProperty().addListener((obs, oldVal, newVal) -> updateLightLevel(newVal.floatValue()));
        live.deviceIdProperty().addListener((obs, oldVal, newVal) -> updatePlotLabels(newVal));

        // Re-format temperature labels immediately when the user changes the unit
        smartfarm.service.SettingsManager.getInstance().useFahrenheitProperty().addListener((obs, oldVal, newVal) -> {
            if (!Float.isNaN(lastTempCelsius)) {
                smartfarm.service.SettingsManager sm = smartfarm.service.SettingsManager.getInstance();
                lblTemperature.setText(sm.formatTemp(lastTempCelsius));
                lblTempTop.setText(sm.formatTempShort(lastTempCelsius));
                tempSeries.setName(newVal ? "Temperature (°F)" : "Temperature (°C)");
            }
        });

        // Polling fallback for remote clients: ChangeListeners only fire on value change,
        // so if the same reading arrives twice (stable environment) the cards go stale.
        // This Timeline re-applies whatever is in LiveSensorData every 5 seconds.
        javafx.animation.Timeline sensorPoll = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                float t2 = live.temperatureProperty().get();
                float h2 = live.humidityProperty().get();
                float s2 = live.soilMoistureProperty().get();
                String dev2 = live.deviceIdProperty().get();
                if (!Float.isNaN(t2)) updateTemperature(t2);
                if (!Float.isNaN(h2)) updateHumidity(h2);
                if (!Float.isNaN(s2)) updateSoilMoisture(s2);
                float l2 = live.lightLevelProperty().get();
                if (!Float.isNaN(l2)) updateLightLevel(l2);
                if (dev2 != null && !dev2.equals("--")) updatePlotLabels(dev2);
            })
        );
        sensorPoll.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sensorPoll.play();

        float t = live.temperatureProperty().get();
        float h = live.humidityProperty().get();
        float s = live.soilMoistureProperty().get();
        float l = live.lightLevelProperty().get();
        String dev = live.deviceIdProperty().get();
        if (!Float.isNaN(t)) updateTemperature(t);
        if (!Float.isNaN(h)) updateHumidity(h);
        if (!Float.isNaN(s)) updateSoilMoisture(s);
        if (!Float.isNaN(l)) updateLightLevel(l);
        if (dev != null && !dev.equals("--")) updatePlotLabels(dev);
    }

    private void updateTemperature(float t) {
        lastTempCelsius = t;
        smartfarm.service.SettingsManager sm = smartfarm.service.SettingsManager.getInstance();
        lblTemperature.setText(sm.formatTemp(t));
        lblTempTop.setText(sm.formatTempShort(t));
        lblWeatherDesc.setText(t > 35 ? "Hot conditions" : t > 28 ? "Warm" : t > 18 ? "Comfortable" : t > 10 ? "Cool" : "Cold conditions");
        lblTempStatus.getStyleClass().removeAll("badge-normal", "badge-high", "badge-low");
        if (t > 35) { lblTempStatus.setText("High"); lblTempStatus.getStyleClass().add("badge-high"); }
        else if (t < 10) { lblTempStatus.setText("Low"); lblTempStatus.getStyleClass().add("badge-low"); }
        else { lblTempStatus.setText("Normal"); lblTempStatus.getStyleClass().add("badge-normal"); }
        addChartDataPoint(tempSeries, t);
    }

    private void updateHumidity(float h) {
        lblHumidity.setText(String.format("%.0f %%", h));
        lblHumStatus.getStyleClass().removeAll("badge-normal", "badge-high", "badge-low");
        if (h > 80) { lblHumStatus.setText("High"); lblHumStatus.getStyleClass().add("badge-high"); }
        else if (h < 30) { lblHumStatus.setText("Low"); lblHumStatus.getStyleClass().add("badge-low"); }
        else { lblHumStatus.setText("Normal"); lblHumStatus.getStyleClass().add("badge-normal"); }
        addChartDataPoint(humSeries, h);
    }

    private void updateSoilChart(float s) {
        if (!Float.isNaN(s)) addChartDataPoint(soilSeries, s);
    }

    private void addChartDataPoint(XYChart.Series<String, Number> series, float value) {
        if (series == null) return;
        dataPointIndex++;
        String timeLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        series.getData().add(new XYChart.Data<>(timeLabel, value));
        if (series.getData().size() > MAX_DATA_POINTS) {
            series.getData().remove(0);
        }
    }

    private void updateSoilMoisture(float s) {
        lblSoilMoisture.setText(String.format("%.0f %%", s));
        lblSoilStatus.getStyleClass().removeAll("badge-normal", "badge-high", "badge-low");
        if (s < 30) { lblSoilStatus.setText("Dry"); lblSoilStatus.getStyleClass().add("badge-low"); }
        else if (s > 85) { lblSoilStatus.setText("Wet"); lblSoilStatus.getStyleClass().add("badge-high"); }
        else { lblSoilStatus.setText("Normal"); lblSoilStatus.getStyleClass().add("badge-normal"); }
        updateSoilChart(s);
    }

    private void updateLightLevel(float l) {
        lblLightLevel.setText(String.format("%.0f %%", l));
        lblLightStatus.getStyleClass().removeAll("badge-normal", "badge-high", "badge-low");
        if (l < 20) { lblLightStatus.setText("Low"); lblLightStatus.getStyleClass().add("badge-low"); }
        else if (l > 80) { lblLightStatus.setText("Bright"); lblLightStatus.getStyleClass().add("badge-high"); }
        else { lblLightStatus.setText("Normal"); lblLightStatus.getStyleClass().add("badge-normal"); }
    }

    private void updatePlotLabels(String devId) {
        String plot = devId.replace("_sensor", "").replace("plot", "Plot ");
        lblTempPlot.setText(plot);
        lblLightPlot.setText(plot);
        lblHumPlot.setText(plot);
        lblSoilPlot.setText(plot);
    }

    // ═══════════════ UI EVENTS ═══════════════

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
    @FXML private void onNavDashboard()  { showPage(dashboardPage, btnDashboard); refreshSensorLabels(); }
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
    @FXML private void onNavUsers()      { loadFxmlPage("/fxml/users.fxml", btnUsers); }
    @FXML private void onNavLogs()       { loadFxmlPage("/fxml/logs.fxml", btnLogs); }

    private void showPage(Node page, Button navBtn) {
        pageContainer.getChildren().setAll(page);
        setActiveNav(navBtn);
    }

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
