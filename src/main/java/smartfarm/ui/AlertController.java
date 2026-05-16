package smartfarm.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.SensorDAO;
import smartfarm.dao.TaskDAO;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Alert;
import smartfarm.model.SensorReading;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import smartfarm.service.AlertService;
import smartfarm.service.SystemLogManager;
import smartfarm.util.ThresholdConfig;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlertController {

    // ── Filters ──
    @FXML private ComboBox<String> cmbSeverity;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TextField txtSearch;
    @FXML private Button btnMarkAllRead;

    // ── Summary Cards ──
    @FXML private Label lblCritical, lblWarnings, lblResolved, lblUnresolved;

    // ── Alert Table ──
    @FXML private TableView<Alert> alertTable;
    @FXML private TableColumn<Alert, String> colSeverity;
    @FXML private TableColumn<Alert, String> colType;
    @FXML private TableColumn<Alert, Integer> colPlot;
    @FXML private TableColumn<Alert, String> colMessage;
    @FXML private TableColumn<Alert, String> colTimestamp;
    @FXML private TableColumn<Alert, String> colStatus;
    @FXML private TableColumn<Alert, Void>   colAction;

    @FXML private Label lblPagination;
    @FXML private HBox paginationBox;

    // ── Detail Pane ──
    @FXML private VBox detailPane;
    @FXML private Button btnCloseDetails;

    @FXML private Label lblDetailSeverity;
    @FXML private Label lblDetailStatus;
    @FXML private FontIcon detailIcon;

    @FXML private Label lblDetailTitle;
    @FXML private Label lblDetailPlot;
    @FXML private Label lblDetailMessage;

    @FXML private Label lblGridSensorType;
    @FXML private Label lblGridCurrentVal;
    @FXML private Label lblGridThreshold;
    @FXML private Label lblGridTriggered;
    @FXML private Label lblGridSensorId;

    @FXML private LineChart<String, Number> detailChart;
    @FXML private CategoryAxis detailChartX;
    @FXML private NumberAxis detailChartY;

    @FXML private Label lblSuggestedAction;
    @FXML private HBox relatedTaskBox;
    @FXML private Label lblRelatedTaskId;
    @FXML private Label lblRelatedTaskTitle;
    @FXML private Label lblRelatedTaskStatus;

    @FXML private Button btnDetailResolve;
    @FXML private Button btnDetailCreateTask;

    private final AlertService alertService = new AlertService();
    private ObservableList<Alert> masterList;
    private FilteredList<Alert> filteredList;
    private Alert currentSelectedAlert = null;

    private static final int PAGE_SIZE = 20;
    private int currentPage = 0;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy  hh:mm a");

    private static int workerModeId = -1;
    private static int currentManagerId = 1;
    public static void setWorkerMode(int workerId) { workerModeId = workerId; }
    public static void clearWorkerMode() { workerModeId = -1; }
    public static void setCurrentManagerId(int id) { currentManagerId = id; }

    // ═══════════════ INITIALIZATION ═══════════════

    @FXML
    public void initialize() {
        detailPane.setVisible(false);
        detailPane.setManaged(false);

        // Workers cannot create tasks
        if (workerModeId > 0 && btnDetailCreateTask != null) {
            btnDetailCreateTask.setVisible(false);
            btnDetailCreateTask.setManaged(false);
        }

        setupFilters();
        setupColumns();
        loadAlerts();

        alertTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                showAlertDetails(newSel);
            }
        });
    }

    private void setupFilters() {
        cmbSeverity.setItems(FXCollections.observableArrayList("All Severity", "CRITICAL", "WARNING", "INFO"));
        cmbSeverity.getSelectionModel().selectFirst();
        cmbSeverity.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilterAndPaginate(); });

        cmbStatus.setItems(FXCollections.observableArrayList("All Status", "Active", "Resolved"));
        cmbStatus.getSelectionModel().selectFirst();
        cmbStatus.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilterAndPaginate(); });

        txtSearch.textProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilterAndPaginate(); });
    }

    private void applyFilterAndPaginate() {
        if (filteredList == null) return;

        String sevFilter = cmbSeverity.getValue();
        String statusFilter = cmbStatus.getValue();
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";

        filteredList.setPredicate(alert -> {
            boolean sevMatch = "All Severity".equals(sevFilter) || alert.getSeverity().name().equals(sevFilter);

            boolean statusMatch = "All Status".equals(statusFilter);
            if (!statusMatch) {
                if ("Resolved".equals(statusFilter)) statusMatch = alert.isResolved();
                else if ("Active".equals(statusFilter)) statusMatch = !alert.isResolved();
            }

            boolean searchMatch = search.isEmpty()
                    || alert.getMessage().toLowerCase().contains(search)
                    || formatType(alert.getAlertType()).toLowerCase().contains(search)
                    || alert.getSeverity().name().toLowerCase().contains(search)
                    || String.valueOf(alert.getPlotId()).contains(search);

            return sevMatch && statusMatch && searchMatch;
        });

        applyPagination();
    }

    // ═══════════════ PAGINATION ═══════════════

    private void applyPagination() {
        int total = filteredList.size();
        int maxPage = Math.max(0, (total - 1) / PAGE_SIZE);
        if (currentPage > maxPage) currentPage = maxPage;

        int from = currentPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);

        String search = txtSearch.getText() != null ? txtSearch.getText().trim() : "";
        String sev = cmbSeverity.getValue();
        String stat = cmbStatus.getValue();
        boolean hasFilters = !search.isEmpty()
                || (sev != null && !"All Severity".equals(sev))
                || (stat != null && !"All Status".equals(stat));

        if (total == 0 && !search.isEmpty()) {
            alertTable.setPlaceholder(new Label("No alerts matching \"" + search + "\""));
        } else if (total == 0 && hasFilters) {
            alertTable.setPlaceholder(new Label("No alerts match the selected filters"));
        } else if (total == 0) {
            alertTable.setPlaceholder(new Label("No alerts found"));
        }

        ObservableList<Alert> pageItems = FXCollections.observableArrayList(
                filteredList.subList(from, to)
        );
        alertTable.setItems(pageItems);

        lblPagination.setText(total > 0
                ? "Showing " + (from + 1) + " to " + to + " of " + total + " alerts"
                : total == 0 && !search.isEmpty() ? "No alerts matching \"" + search + "\""
                : "No alerts found");

        buildPaginationButtons(maxPage);
    }

    private void buildPaginationButtons(int maxPage) {
        paginationBox.getChildren().clear();

        if (maxPage <= 0) return;

        Button prev = new Button("<");
        prev.getStyleClass().add("page-btn");
        prev.setDisable(currentPage == 0);
        prev.setOnAction(e -> { currentPage--; applyPagination(); });
        paginationBox.getChildren().add(prev);

        for (int i = 0; i <= maxPage; i++) {
            final int page = i;
            Button btn = new Button(String.valueOf(i + 1));
            btn.getStyleClass().add("page-btn");
            if (i == currentPage) btn.getStyleClass().add("page-btn-active");
            btn.setOnAction(e -> { currentPage = page; applyPagination(); });
            paginationBox.getChildren().add(btn);
            if (paginationBox.getChildren().size() > 8) break;
        }

        Button next = new Button(">");
        next.getStyleClass().add("page-btn");
        next.setDisable(currentPage >= maxPage);
        next.setOnAction(e -> { currentPage++; applyPagination(); });
        paginationBox.getChildren().add(next);
    }

    // ═══════════════ TABLE COLUMNS ═══════════════

    private void setupColumns() {
        alertTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colSeverity.setResizable(false);
        colType.setResizable(false);
        colPlot.setResizable(false);
        // colMessage left resizable so it stretches to fill remaining width
        colTimestamp.setResizable(false);
        colStatus.setResizable(false);
        colAction.setResizable(false);

        colSeverity.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getSeverity().name()));
        colSeverity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                if (empty || severity == null) { setGraphic(null); } else {
                    Label badge = new Label(severity);
                    badge.getStyleClass().addAll("badge", getBadgeClass(severity));
                    setGraphic(badge);
                }
            }
        });

        colType.setCellValueFactory(cell ->
                new SimpleStringProperty(formatType(cell.getValue().getAlertType())));

        colPlot.setCellValueFactory(new PropertyValueFactory<>("plotId"));

        colMessage.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getMessage()));

        colTimestamp.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTimestamp().format(TIME_FMT)));

        colStatus.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().isResolved() ? "Resolved" : "Active"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().addAll("badge",
                            status.equals("Resolved") ? "badge-normal" : "badge-active");
                    setGraphic(badge);
                }
            }
        });

        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button resolveBtn = new Button("Resolve");
            {
                resolveBtn.getStyleClass().add("resolve-btn");
                FontIcon icon = new FontIcon("fth-check");
                icon.setIconSize(12);
                resolveBtn.setGraphic(icon);
                resolveBtn.setGraphicTextGap(4);
                resolveBtn.setOnAction(event -> {
                    Alert alert = getTableView().getItems().get(getIndex());
                    onResolveAlert(alert);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); } else {
                    Alert alert = getTableView().getItems().get(getIndex());
                    if (alert.isResolved()) {
                        Label done = new Label("✓ Done");
                        done.getStyleClass().add("resolved-label");
                        setGraphic(done);
                    } else {
                        setGraphic(resolveBtn);
                    }
                }
            }
        });
    }

    // ═══════════════ DATA LOADING ═══════════════

    private void loadAlerts() {
        try {
            List<Alert> alerts = alertService.getAllAlerts();
            masterList = FXCollections.observableArrayList(alerts);
            filteredList = new FilteredList<>(masterList, p -> true);
            updateSummaryCards();
            applyFilterAndPaginate();
        } catch (RuntimeException e) {
            masterList = FXCollections.observableArrayList();
            filteredList = new FilteredList<>(masterList, p -> true);
            alertTable.setItems(FXCollections.observableArrayList());
            alertTable.setPlaceholder(new Label("No database connection — alerts unavailable"));
            updateSummaryCards();
        }
    }

    private void updateSummaryCards() {
        int resolved   = (int) masterList.stream().filter(Alert::isResolved).count();
        int critical   = (int) masterList.stream().filter(a -> !a.isResolved() && a.isCritical()).count();
        int warnings   = (int) masterList.stream().filter(a -> !a.isResolved() && a.getSeverity().name().equals("WARNING")).count();
        int unresolved = (int) masterList.stream().filter(a -> !a.isResolved()).count();

        lblCritical.setText(String.valueOf(critical));
        lblWarnings.setText(String.valueOf(warnings));
        lblUnresolved.setText(String.valueOf(unresolved));
        lblResolved.setText(String.valueOf(resolved));
    }

    // ═══════════════ DETAIL PANE ═══════════════

    private void showAlertDetails(Alert alert) {
        currentSelectedAlert = alert;
        detailPane.setVisible(true);
        detailPane.setManaged(true);

        // Header badges
        lblDetailSeverity.setText(alert.getSeverity().name());
        lblDetailSeverity.getStyleClass().removeAll("badge-high", "badge-low", "badge-info", "badge-normal");
        lblDetailSeverity.getStyleClass().add(getBadgeClass(alert.getSeverity().name()));

        lblDetailStatus.setText(alert.isResolved() ? "Resolved" : "Active");
        lblDetailTitle.setText(formatType(alert.getAlertType()) + " Alert");
        lblDetailPlot.setText("Plot " + alert.getPlotId());
        lblDetailMessage.setText(alert.getMessage());

        // Details grid — extract actual value from message
        String alertType = alert.getAlertType() != null ? alert.getAlertType() : "";
        lblGridSensorType.setText(getSensorType(alertType));
        lblGridCurrentVal.setText(extractValueFromMessage(alert.getMessage()));
        lblGridThreshold.setText(getThresholdForType(alertType));
        lblGridTriggered.setText(alert.getTimestamp().format(TIME_FMT));
        lblGridSensorId.setText("SENSOR-P" + alert.getPlotId());

        // Detail icon
        if (alertType.contains("TEMP")) detailIcon.setIconLiteral("fth-thermometer");
        else if (alertType.contains("HUMIDITY")) detailIcon.setIconLiteral("fth-droplet");
        else if (alertType.contains("SOIL")) detailIcon.setIconLiteral("fth-droplet");
        else if (alertType.contains("LIGHT")) detailIcon.setIconLiteral("fth-sun");
        else detailIcon.setIconLiteral("fth-alert-triangle");

        // Chart — real sensor data
        populateRealChart(alert.getPlotId(), alertType);

        // Suggested action — derived from alert type + current value
        lblSuggestedAction.setText(getSuggestedAction(alertType, alert.getMessage()));

        // Related task
        loadRelatedTask(alert.getAlertId());

        // Buttons
        btnDetailResolve.setDisable(alert.isResolved());
        btnDetailResolve.setText(alert.isResolved() ? "Resolved" : "Mark as Resolved");
    }

    @FXML
    private void onCloseDetails() {
        detailPane.setVisible(false);
        detailPane.setManaged(false);
        alertTable.getSelectionModel().clearSelection();
        currentSelectedAlert = null;
    }

    @FXML
    private void onDetailResolve() {
        if (currentSelectedAlert != null && !currentSelectedAlert.isResolved()) {
            onResolveAlert(currentSelectedAlert);
            showAlertDetails(currentSelectedAlert);
        }
    }

    // ═══════════════ REAL CHART DATA ═══════════════

    private void populateRealChart(int plotId, String alertType) {
        detailChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Sensor Reading");

        try {
            SensorDAO sensorDAO = new SensorDAO();
            List<SensorReading> readings = sensorDAO.getRecentForPlot(plotId, 20);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");

            for (int i = readings.size() - 1; i >= 0; i--) {
                SensorReading r = readings.get(i);
                String label = r.getTimestamp() != null ? r.getTimestamp().format(fmt) : "-" + i + "m";
                float value;
                if (alertType.contains("TEMP")) value = r.getTemperature();
                else if (alertType.contains("HUMIDITY")) value = r.getHumidity();
                else if (alertType.contains("SOIL")) value = r.getSoilMoisture();
                else value = r.getTemperature();

                if (!Float.isNaN(value)) {
                    series.getData().add(new XYChart.Data<>(label, value));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load chart data: " + e.getMessage());
        }

        if (series.getData().isEmpty()) {
            series.getData().add(new XYChart.Data<>("No data", 0));
        }
        detailChart.getData().add(series);

        // Style the chart line
        javafx.application.Platform.runLater(() -> {
            if (!detailChart.getData().isEmpty()) {
                javafx.scene.Node line = detailChart.lookup(".chart-series-line");
                if (line != null) line.setStyle("-fx-stroke: #ef4444; -fx-stroke-width: 2px;");
            }
        });
    }

    // ═══════════════ RELATED TASK ═══════════════

    private void loadRelatedTask(int alertId) {
        try {
            TaskDAO taskDAO = new TaskDAO();
            List<Task> all = taskDAO.getAll();
            Task related = null;
            for (Task t : all) {
                if (t.getAlertId() != null && t.getAlertId() == alertId) {
                    related = t;
                    break;
                }
            }
            if (related != null) {
                lblRelatedTaskId.setText("Task #" + related.getTaskId());
                lblRelatedTaskTitle.setText(related.getDescription());
                lblRelatedTaskStatus.setText(related.getStatus().name().replace("_", " "));
                relatedTaskBox.setVisible(true);
                relatedTaskBox.setManaged(true);
            } else {
                lblRelatedTaskId.setText("No related task");
                lblRelatedTaskTitle.setText("—");
                lblRelatedTaskStatus.setText("");
                relatedTaskBox.setVisible(true);
                relatedTaskBox.setManaged(true);
            }
        } catch (SQLException e) {
            lblRelatedTaskId.setText("No related task");
            lblRelatedTaskTitle.setText("—");
            lblRelatedTaskStatus.setText("");
        }
    }

    // ═══════════════ ACTIONS ═══════════════

    private void onResolveAlert(Alert alert) {
        try {
            alertService.resolveAlert(alert.getAlertId());
            alert.resolve();
            SystemLogManager.getInstance().info("AlertService",
                    "Alert #" + alert.getAlertId() + " resolved: " + alert.getAlertType() + " in Plot " + alert.getPlotId(), "manager");
            alertTable.refresh();
            updateSummaryCards();
            applyFilterAndPaginate();
            if (currentSelectedAlert != null && currentSelectedAlert.getAlertId() == alert.getAlertId()) {
                showAlertDetails(alert);
            }
        } catch (RuntimeException e) {
            System.err.println("Failed to resolve alert: " + e.getMessage());
        }
    }

    @FXML
    private void onMarkAllRead() {
        for (Alert a : masterList) {
            if (!a.isResolved()) {
                try {
                    alertService.resolveAlert(a.getAlertId());
                    a.resolve();
                } catch (RuntimeException e) {
                    System.err.println("Failed to resolve alert " + a.getAlertId() + ": " + e.getMessage());
                }
            }
        }
        alertTable.refresh();
        updateSummaryCards();
        applyFilterAndPaginate();
        if (currentSelectedAlert != null) showAlertDetails(currentSelectedAlert);
    }

    @FXML
    private void onCreateTask() {
        if (currentSelectedAlert == null) return;
        Alert alert = currentSelectedAlert;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Task from Alert");
        dialog.setHeaderText("Create a task for: " + formatType(alert.getAlertType()));

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(10));

        TextField descField = new TextField("Resolve: " + alert.getMessage());
        descField.setPrefWidth(350);
        DatePicker duePicker = new DatePicker(LocalDate.now().plusDays(1));

        grid.add(new Label("Description:"), 0, 0);
        grid.add(descField, 1, 0);
        grid.add(new Label("Due Date:"), 0, 1);
        grid.add(duePicker, 1, 1);
        grid.add(new Label("Plot:"), 0, 2);
        grid.add(new Label(String.valueOf(alert.getPlotId())), 1, 2);

        // Worker assignment dropdown
        ComboBox<String> workerCombo = new ComboBox<>();
        workerCombo.setPromptText("Select Worker");
        workerCombo.setMaxWidth(Double.MAX_VALUE);
        java.util.Map<String, Integer> workerNameToId = new java.util.LinkedHashMap<>();
        try {
            WorkerDAO workerDAO = new WorkerDAO();
            List<Worker> workers = workerDAO.getAll();
            for (Worker w : workers) {
                String label = w.getFullName() + " (" + w.getJobTitle() + ")";
                workerCombo.getItems().add(label);
                workerNameToId.put(label, w.getWorkerId());
            }
        } catch (SQLException e) {
            System.err.println("Failed to load workers: " + e.getMessage());
        }
        grid.add(new Label("Assign to:"), 0, 3);
        grid.add(workerCombo, 1, 3);

        dp.setContent(grid);

        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (descField.getText().trim().isEmpty()) {
                descField.setStyle("-fx-border-color:red;");
                event.consume();
            } else {
                descField.setStyle("");
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    TaskDAO taskDAO = new TaskDAO();
                    Task task = new Task(
                            descField.getText().trim(),
                            duePicker.getValue(),
                            alert.getPlotId(),
                            alert.getAlertId() > 0 ? alert.getAlertId() : null,
                            currentManagerId,
                            alert.getAlertType()
                    );
                    // Assign selected worker
                    String selectedWorker = workerCombo.getValue();
                    if (selectedWorker != null && workerNameToId.containsKey(selectedWorker)) {
                        task.setWorkerIds(List.of(workerNameToId.get(selectedWorker)));
                    }
                    taskDAO.save(task);
                    loadRelatedTask(alert.getAlertId());
                } catch (SQLException e) {
                    System.err.println("Failed to create task: " + e.getMessage());
                }
            }
        });
    }

    // ═══════════════ UTILITY METHODS ═══════════════

    private String extractValueFromMessage(String message) {
        if (message == null) return "N/A";
        Matcher m = Pattern.compile("([\\d.]+)\\s*[°%]").matcher(message);
        if (m.find()) {
            String val = m.group(1);
            if (message.contains("°C") || message.toLowerCase().contains("temp")) {
                String unit = smartfarm.service.SettingsManager.getInstance().isUseFahrenheit() ? "°F" : "°C";
                if (smartfarm.service.SettingsManager.getInstance().isUseFahrenheit()) {
                    float c = Float.parseFloat(val); val = String.format("%.1f", c * 9f / 5f + 32f);
                }
                return val + " " + unit;
            }
            if (message.contains("%")) return val + " %";
            return val;
        }
        return "N/A";
    }

    private String getThresholdForType(String alertType) {
        if (alertType == null) return "N/A";
        if (alertType.contains("HIGH_TEMP")) return "> " + smartfarm.service.SettingsManager.getInstance().formatTemp(ThresholdConfig.TEMP_CRITICAL_HIGH);
        if (alertType.contains("LOW_TEMP")) return "< " + smartfarm.service.SettingsManager.getInstance().formatTemp(ThresholdConfig.TEMP_CRITICAL_LOW);
        if (alertType.contains("HIGH_HUMIDITY")) return "> " + ThresholdConfig.HUM_WARNING_HIGH + " %";
        if (alertType.contains("LOW_HUMIDITY")) return "< " + ThresholdConfig.HUM_WARNING_LOW + " %";
        if (alertType.contains("DRY_SOIL")) return "< " + ThresholdConfig.SOIL_WARNING_DRY + " %";
        if (alertType.contains("WET_SOIL")) return "> " + ThresholdConfig.SOIL_WARNING_WET + " %";
        if (alertType.contains("HIGH_LIGHT")) return "> " + ThresholdConfig.LIGHT_WARNING_HIGH + " %";
        if (alertType.contains("LOW_LIGHT")) return "< " + ThresholdConfig.LIGHT_WARNING_LOW + " %";
        return "N/A";
    }

    private String getSuggestedAction(String alertType, String message) {
        if (alertType == null) return "Monitor the situation and take action if needed.";
        String val = extractValueFromMessage(message);
        if (alertType.contains("HIGH_TEMP"))
            return "Reading at " + val + ". Activate cooling/ventilation systems. Increase irrigation to reduce soil temperature.";
        if (alertType.contains("LOW_TEMP"))
            return "Reading at " + val + ". Enable greenhouse heating. Cover crops with protective frost blankets.";
        if (alertType.contains("HIGH_HUMIDITY"))
            return "Reading at " + val + ". Improve ventilation and air circulation. Reduce irrigation frequency.";
        if (alertType.contains("LOW_HUMIDITY"))
            return "Reading at " + val + ". Increase misting or irrigation. Check for leaks in irrigation system.";
        if (alertType.contains("DRY_SOIL"))
            return "Reading at " + val + ". Start irrigation immediately. Check for blocked drip lines or pump failures.";
        if (alertType.contains("WET_SOIL"))
            return "Reading at " + val + ". Reduce watering schedule. Ensure proper drainage. Check for overwatering.";
        if (alertType.contains("LOW_LIGHT"))
            return "Reading at " + val + ". Activate supplemental grow lights. Check for shade from structures or vegetation.";
        if (alertType.contains("HIGH_LIGHT"))
            return "Reading at " + val + ". Deploy shade netting to protect crops from excessive light and heat stress.";
        return "Monitor the situation and take corrective action as needed.";
    }

    private String getBadgeClass(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "badge-high";
            case "WARNING"  -> "badge-low";
            case "INFO"     -> "badge-info";
            default         -> "badge-normal";
        };
    }

    private String formatType(String alertType) {
        if (alertType == null) return "";
        String[] parts = alertType.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String getSensorType(String alertType) {
        if (alertType == null) return "Unknown Sensor";
        if (alertType.contains("TEMP")) return "Temperature Sensor";
        if (alertType.contains("SOIL") || alertType.contains("MOISTURE")) return "Soil Moisture Sensor";
        if (alertType.contains("HUMIDITY")) return "Humidity Sensor";
        if (alertType.contains("LIGHT")) return "Light Intensity Sensor";
        return "Environmental Sensor";
    }
}
