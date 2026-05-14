package smartfarm.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
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
import smartfarm.util.CSVExporter;
import smartfarm.util.DBConnection;

import java.io.File;
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
    @FXML private Label lblTemperature, lblHumidity, lblSoilMoisture;
    @FXML private Label lblTempPlot, lblHumPlot, lblSoilPlot;
    @FXML private Label lblTempStatus, lblHumStatus, lblSoilStatus;

    // ── Chart (real-time JavaFX LineChart) ──
    @FXML private StackPane chartContainer;
    private LineChart<String, Number> sensorChart;
    private XYChart.Series<String, Number> tempSeries;
    private XYChart.Series<String, Number> humSeries;
    private int dataPointIndex = 0;
    private static final int MAX_DATA_POINTS = 40;

    // ── Tables ──
    @FXML private TableView<Crop> cropTable;
    @FXML private TableView<Worker> workerTable;
    @FXML private TableView<HarvestRecord> harvestTable;
    @FXML private TableColumn<Crop, String> colCropName, colCropPlot, colCropStage, colPlantingDate, colCropStatus;
    @FXML private TableColumn<Worker, String> colWorkerName, colWorkerRole, colWorkerStatus, colWorkerTask, colWorkload;
    @FXML private TableColumn<HarvestRecord, String> colHarvestCrop, colHarvestPlot, colHarvestDate, colHarvestQty, colHarvestGrade, colHarvestExpected, colHarvestPerf;

    // ── Lists ──
    @FXML private ListView<String> alertListView, taskListView;
    @FXML private Label lblTasksTitle;

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

    // ── Hyperlinks ──
    @FXML private Hyperlink linkViewSensors, linkViewAlerts, linkShowAllAlerts,
                             linkViewTasks, linkViewAllTasks, linkViewCrops,
                             linkViewWorkers, linkViewHarvests, linkManagePlots;

    private ContextMenu userMenu;
    private Button activeNavButton;
    private smartfarm.model.User currentUser;

    // ─── B9 lifecycle handles ──────────────────────────────────────
    // Promoted to fields so stopLifecycle() can cleanly tear them
    // down. The shell caches this controller for the JVM lifetime
    // today so they never actually leak, but having the hook in place
    // means Phase 2's LifecycleService.PAUSE wiring (Hagag-track) is
    // a one-line addition on ShellView's side rather than a refactor.
    private javafx.animation.Timeline clock;
    private ChangeListener<Number>  liveTempListener;
    private ChangeListener<Number>  liveHumListener;
    private ChangeListener<Number>  liveSoilListener;
    private ChangeListener<String>  liveDeviceListener;
    private ChangeListener<Number>  activeSensorsListener;

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
            applyRolePermissions(user.getRole());
            // For workers, show "My Tasks" instead of "Tasks"
            if (user.getRole() == smartfarm.model.User.Role.WORKER) {
                lblTasksTitle.setText("My Tasks");
            }
        }
    }

    private void applyRolePermissions(smartfarm.model.User.Role role) {
        if (role == smartfarm.model.User.Role.WORKER) {
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
        setupChart();
        setupHyperlinks();
        subscribeLiveSensor();
        updateSidebarStatus();
        loadDashboardData();
        activeNavButton = btnDashboard;
    }

    // ═══════════════ DATA LOADING ═══════════════

    private void loadDashboardData() {
        loadAlerts();
        loadTasks();
        loadCropTable();
        loadWorkerTable();
        loadHarvestTable();
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
            ObservableList<String> items = FXCollections.observableArrayList();
            int count = 0;
            for (Task t : allTasks) {
                if (t.getStatus() == Task.Status.DONE) continue;
                if (count >= 8) break;
                String statusIcon = t.getStatus() == Task.Status.IN_PROGRESS ? "🔄" : "⏳";
                String due = t.getDueDate() != null ? " (due " + t.getDueDate() + ")" : "";
                items.add(statusIcon + " " + t.getDescription() + due);
                count++;
            }
            if (items.isEmpty()) items.add("No pending tasks");
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
            colHarvestExpected.setCellValueFactory(d -> {
                Crop c = cropMap.get(d.getValue().getCropId());
                return new SimpleStringProperty(c != null ? String.format("%.1f", c.getExpectedYield()) : "—");
            });
            colHarvestPerf.setCellValueFactory(d -> {
                Crop c = cropMap.get(d.getValue().getCropId());
                if (c != null && c.getExpectedYield() > 0) {
                    double perf = (d.getValue().getQuantityKg() / c.getExpectedYield()) * 100;
                    return new SimpleStringProperty(String.format("%.0f%%", perf));
                }
                return new SimpleStringProperty("—");
            });

            ObservableList<HarvestRecord> harvestData = FXCollections.observableArrayList(records.subList(0, Math.min(records.size(), 10)));
            harvestTable.setItems(harvestData);
        } catch (SQLException e) {
            System.err.println("Failed to load harvest table: " + e.getMessage());
        }
    }

    // ═══════════════ HYPERLINK NAVIGATION ═══════════════

    private void setupHyperlinks() {
        linkViewSensors.setOnAction(e -> loadFxmlPage("/fxml/monitoring.fxml", btnMonitoring));
        linkViewAlerts.setOnAction(e -> loadFxmlPage("/fxml/alerts.fxml", btnAlerts));
        linkShowAllAlerts.setOnAction(e -> loadFxmlPage("/fxml/alerts.fxml", btnAlerts));
        linkViewTasks.setOnAction(e -> loadFxmlPage("/fxml/tasks.fxml", btnTasks));
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
        stageCombo.setValue(Crop.GrowthStage.SEED);
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

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { showQuickAlert("Crop name is required"); return null; }
                String selPlot = plotCombo.getValue();
                if (selPlot == null || !plotNameToId.containsKey(selPlot)) { showQuickAlert("Please select a plot"); return null; }
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

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String desc = descField.getText().trim();
                if (desc.isEmpty()) { showQuickAlert("Description is required"); return null; }
                String selPlot = plotCombo.getValue();
                if (selPlot == null || !plotNameToId.containsKey(selPlot)) { showQuickAlert("Please select a plot"); return null; }
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

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String selected = cropCombo.getValue();
                if (selected == null || !cropNameToId.containsKey(selected)) {
                    showQuickAlert("Please select a crop");
                    return null;
                }
                try {
                    double qty = Double.parseDouble(qtyField.getText().trim());
                    if (qty <= 0) { showQuickAlert("Quantity must be > 0"); return null; }
                    return new HarvestRecord(datePicker.getValue(), qty,
                            gradeCombo.getValue(), cropNameToId.get(selected));
                } catch (NumberFormatException e) {
                    showQuickAlert("Invalid quantity");
                    return null;
                }
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
        try {
            List<HarvestRecord> harvests = harvestDAO.getAll();
            List<Crop> crops = (allCrops != null) ? allCrops : cropDAO.getAll();
            List<Plot> plots = (allPlots != null) ? allPlots : plotDAO.getAll();

            Map<Integer, Crop> cropMap = new HashMap<>();
            for (Crop c : crops) cropMap.put(c.getCropId(), c);
            Map<Integer, String> plotMap = new HashMap<>();
            for (Plot p : plots) plotMap.put(p.getPlotId(), p.getName());

            double pricePerKg = 2.50;
            StringBuilder csv = new StringBuilder("Date,Crop,Plot,Qty (kg),Grade,Revenue\n");
            for (HarvestRecord hr : harvests) {
                Crop c = cropMap.get(hr.getCropId());
                String cropName = (c != null) ? c.getCropName() : "Crop #" + hr.getCropId();
                String plotName = (c != null) ? plotMap.getOrDefault(c.getPlotId(), "—") : "—";
                csv.append(String.format("%s,%s,%s,%.1f,%s,$%.2f\n",
                        hr.getHarvestDate(), cropName, plotName,
                        hr.getQuantityKg(), hr.getGrade().name(),
                        hr.getQuantityKg() * pricePerKg));
            }
            File saved = CSVExporter.saveCsv(csv.toString(), "harvest_report.csv");
            showExportSuccess("Report exported to " + saved.getName());
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
        try {
            List<SensorReading> readings = sensorDAO.getRecent(500);
            StringBuilder csv = new StringBuilder("ReadingID,DeviceID,Temperature,Humidity,SoilMoisture,Timestamp\n");
            for (SensorReading r : readings) {
                csv.append(String.format("%d,%d,%.2f,%.2f,%.2f,%s\n",
                        r.getReadingId(), r.getDeviceId(), r.getTemperature(),
                        r.getHumidity(), r.getSoilMoisture(), r.getTimestamp()));
            }
            File saved = CSVExporter.saveCsv(csv.toString(), "sensor_logs.csv");
            showExportSuccess("Sensor logs exported to " + saved.getName());
        } catch (SQLException | IOException ex) {
            showExportError("Failed to export sensor logs: " + ex.getMessage());
        }
    }

    @FXML
    private void onExportHarvestCSV() {
        try {
            List<HarvestRecord> records = harvestDAO.getAll();
            StringBuilder csv = new StringBuilder("RecordID,CropID,HarvestDate,QuantityKg,Grade\n");
            for (HarvestRecord r : records) {
                csv.append(String.format("%d,%d,%s,%.2f,%s\n",
                        r.getRecordId(), r.getCropId(), r.getHarvestDate(),
                        r.getQuantityKg(), r.getGrade().name()));
            }
            File saved = CSVExporter.saveCsv(csv.toString(), "harvest_summary.csv");
            showExportSuccess("Harvest summary exported to " + saved.getName());
        } catch (SQLException | IOException ex) {
            showExportError("Failed to export harvest data: " + ex.getMessage());
        }
    }

    @FXML
    private void onExportAlertCSV() {
        try {
            List<Alert> alerts = alertDAO.getAll();
            StringBuilder csv = new StringBuilder("AlertID,Type,Severity,Message,Resolved,Timestamp,PlotID\n");
            for (Alert a : alerts) {
                csv.append(String.format("%d,%s,%s,\"%s\",%b,%s,%d\n",
                        a.getAlertId(), a.getAlertType(), a.getSeverity().name(),
                        a.getMessage().replace("\"", "\"\""), a.isResolved(),
                        a.getTimestamp(), a.getPlotId()));
            }
            File saved = CSVExporter.saveCsv(csv.toString(), "alert_history.csv");
            showExportSuccess("Alert history exported to " + saved.getName());
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
        tempSeries.setName("Temperature (°C)");
        humSeries = new XYChart.Series<>();
        humSeries.setName("Humidity (%)");

        sensorChart.getData().addAll(tempSeries, humSeries);
        chartContainer.getChildren().add(sensorChart);
    }

    // ═══════════════ DATE/TIME ═══════════════

    private void updateDateTime() {
        // B9: keep the Timeline reachable via the `clock` field so
        // stopLifecycle() can pause it. Idempotent — re-entering does
        // not start a second timeline.
        if (clock != null) return;
        clock = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> {
                    LocalDateTime now = LocalDateTime.now();
                    lblDate.setText(now.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
                    lblTime.setText(now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
                }),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1)));
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
        smartfarm.ui.nav.NavContext.get().clear();
        smartfarm.ui.nav.AppView.SIGNIN.switchTo();
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

        LiveSensorData live = LiveSensorData.getInstance();
        updateSensorDot(live.activeSensorsProperty().get());
        if (activeSensorsListener == null) {
            activeSensorsListener = (obs, oldVal, newVal) -> updateSensorDot(newVal.intValue());
            live.activeSensorsProperty().addListener(activeSensorsListener);
        }
    }

    private void updateSensorDot(int count) {
        lblSensorStatus.setText(count + " Active");
        dotSensors.getStyleClass().setAll(count > 0 ? "status-dot-online" : "status-dot-offline");
    }

    // ═══════════════ LIVE SENSOR SUBSCRIPTION ═══════════════

    private void subscribeLiveSensor() {
        LiveSensorData live = LiveSensorData.getInstance();

        // B9: capture listener instances so stopLifecycle() can detach.
        // Guard against a double-subscribe (e.g. if startLifecycle
        // is called twice without an intervening stop).
        if (liveTempListener == null) {
            liveTempListener   = (obs, oldVal, newVal) -> updateTemperature(newVal.floatValue());
            liveHumListener    = (obs, oldVal, newVal) -> updateHumidity(newVal.floatValue());
            liveSoilListener   = (obs, oldVal, newVal) -> updateSoilMoisture(newVal.floatValue());
            liveDeviceListener = (obs, oldVal, newVal) -> updatePlotLabels(newVal);
            live.temperatureProperty().addListener(liveTempListener);
            live.humidityProperty().addListener(liveHumListener);
            live.soilMoistureProperty().addListener(liveSoilListener);
            live.deviceIdProperty().addListener(liveDeviceListener);
        }

        float t = live.temperatureProperty().get();
        float h = live.humidityProperty().get();
        float s = live.soilMoistureProperty().get();
        String dev = live.deviceIdProperty().get();
        if (!Float.isNaN(t)) updateTemperature(t);
        if (!Float.isNaN(h)) updateHumidity(h);
        if (!Float.isNaN(s)) updateSoilMoisture(s);
        if (dev != null && !dev.equals("--")) updatePlotLabels(dev);
    }

    /**
     * B9 lifecycle teardown — stops the clock Timeline and detaches all
     * listeners from the shared {@code LiveSensorData} singleton.
     *
     * <p>Idempotent: safe to call multiple times in a row, or before
     * lifecycle has started. Pairs naturally with a future
     * {@code startLifecycle()} that re-runs {@link #updateDateTime()}
     * + {@link #subscribeLiveSensor()} + {@link #updateSidebarStatus()}.
     *
     * <p>Not called automatically by {@code ShellView} today because the
     * shell caches this controller for the JVM lifetime (only one
     * dashboard ever lives). Phase 2's Gluon Attach {@code LifecycleService}
     * integration is the intended trigger: wire {@code PAUSE} →
     * {@code stopLifecycle()} and {@code RESUME} → a re-attach hook so
     * the dashboard goes idle while the OS has the app backgrounded.
     */
    public void stopLifecycle() {
        if (clock != null) {
            clock.stop();
            clock = null;
        }
        LiveSensorData live = LiveSensorData.getInstance();
        if (liveTempListener != null) {
            live.temperatureProperty().removeListener(liveTempListener);
            liveTempListener = null;
        }
        if (liveHumListener != null) {
            live.humidityProperty().removeListener(liveHumListener);
            liveHumListener = null;
        }
        if (liveSoilListener != null) {
            live.soilMoistureProperty().removeListener(liveSoilListener);
            liveSoilListener = null;
        }
        if (liveDeviceListener != null) {
            live.deviceIdProperty().removeListener(liveDeviceListener);
            liveDeviceListener = null;
        }
        if (activeSensorsListener != null) {
            live.activeSensorsProperty().removeListener(activeSensorsListener);
            activeSensorsListener = null;
        }
    }

    private void updateTemperature(float t) {
        lblTemperature.setText(String.format("%.1f °C", t));
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
    }

    private void updatePlotLabels(String devId) {
        String plot = devId.replace("_sensor", "").replace("plot", "Plot ");
        lblTempPlot.setText(plot);
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
    // Legacy sidebar buttons — still wired by the (now-hidden) sidebar in
    // dashboard.fxml so the desktop look is preserved if mobile.css ever
    // re-enables the sidebar via a width-based toggle. Each handler routes
    // through the public navigate(...) method below so the sidebar and the
    // Gluon NavigationDrawer (set up by ShellView on mobile) share a single
    // dispatch path.
    @FXML private void onNavDashboard()  { navigate(NavTarget.DASHBOARD); }
    @FXML private void onNavMonitoring() { navigate(NavTarget.MONITORING); }
    @FXML private void onNavDisease()    { navigate(NavTarget.DISEASE); }
    @FXML private void onNavAlerts()     { navigate(NavTarget.ALERTS); }
    @FXML private void onNavCropsList()  { navigate(NavTarget.CROPS); }
    @FXML private void onNavPlotsList()  { navigate(NavTarget.PLOTS); }
    @FXML private void onNavWorkers()    { navigate(NavTarget.WORKERS); }
    @FXML private void onNavAttendance() { navigate(NavTarget.ATTENDANCE); }
    @FXML private void onNavTasks()      { navigate(NavTarget.TASKS); }
    @FXML private void onNavHarvests()   { navigate(NavTarget.HARVESTS); }
    @FXML private void onNavReports()    { navigate(NavTarget.REPORTS); }
    @FXML private void onNavSettings()   { navigate(NavTarget.SETTINGS); }
    @FXML private void onNavUsers()      { navigate(NavTarget.USERS); }
    @FXML private void onNavLogs()       { navigate(NavTarget.LOGS); }

    /**
     * Public navigation entry point so external chrome (Gluon
     * NavigationDrawer in {@link smartfarm.ui.views.ShellView}, hyperlinks,
     * etc.) can switch the dashboard's inner page without needing a
     * reference to the matching sidebar Button.
     *
     * <p>The sidebar Button (if any) is still highlighted via
     * {@code setActiveNav} for the desktop look. When called from the
     * NavigationDrawer the Button param is null — {@code setActiveNav}
     * already tolerates null.
     */
    public void navigate(NavTarget target) {
        switch (target) {
            case DASHBOARD  -> showPage(dashboardPage, btnDashboard);
            case MONITORING -> loadFxmlPage("/fxml/monitoring.fxml", btnMonitoring);
            case DISEASE    -> showPage(new DiseaseDetectionPage(), btnDisease);
            case ALERTS     -> loadFxmlPage("/fxml/alerts.fxml", btnAlerts);
            case CROPS      -> loadFxmlPage("/fxml/crops.fxml", btnCropsCrops);
            case PLOTS      -> loadFxmlPage("/fxml/plots.fxml", btnCropsPlots);
            case WORKERS    -> loadFxmlPage("/fxml/workers.fxml", btnWorkers);
            case ATTENDANCE -> showPage(new AttendancePage(), btnAttendance);
            case TASKS      -> loadFxmlPage("/fxml/tasks.fxml", btnTasks);
            case HARVESTS   -> loadFxmlPage("/fxml/harvest.fxml", btnHarvests);
            case REPORTS    -> loadFxmlPage("/fxml/reports.fxml", btnReports);
            case SETTINGS   -> showPage(new SettingsPage(), btnSettings);
            case USERS      -> loadFxmlPage("/fxml/workers.fxml", btnUsers);
            case LOGS       -> loadFxmlPage("/fxml/logs.fxml", btnLogs);
        }
    }

    /** Identifiers for the dashboard's inner pages. */
    public enum NavTarget {
        DASHBOARD, MONITORING, DISEASE, ALERTS, CROPS, PLOTS,
        WORKERS, ATTENDANCE, TASKS, HARVESTS, REPORTS, SETTINGS, USERS, LOGS
    }

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
