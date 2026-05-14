package smartfarm.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
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
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.model.Alert;
import smartfarm.service.AlertService;
import smartfarm.ui.async.AsyncCalls;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * Controller for the Alerts page.
 * Displays all alerts in a colour-coded TableView with a details pane.
 */
public class AlertController {

    // ── Filters ──
    @FXML private ComboBox<String> cmbSeverity;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TextField txtSearch;
    @FXML private Button btnMarkAllRead;

    // ── Summary Cards ──
    @FXML private Label lblCritical, lblWarnings, lblResolved, lblUnresolved;

    @FXML private VBox rootPane;
    @FXML private VBox listSection;

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
    
    @FXML private Button btnDetailResolve;
    @FXML private Button btnDetailCreateTask;

    private final AlertService alertService = new AlertService();
    private ObservableList<Alert> masterList;
    private FilteredList<Alert> filteredList;
    private Alert currentSelectedAlert = null;
    private ChangeListener<Number> detailWidthListener;

    private static final double DETAIL_STACK_BREAKPOINT = 700.0;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy  hh:mm a");

    // ═══════════════ INITIALIZATION ═══════════════

    @FXML
    public void initialize() {
        // Hide details pane initially
        detailPane.setVisible(false);
        detailPane.setManaged(false);

        setupFilters();
        setupColumns();
        setupResponsiveDetailLayout();
        loadAlerts();

        // Listen for table selection changes
        alertTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                showAlertDetails(newSel);
            }
        });
        if (rootPane.getScene() != null) {
            rootPane.getScene().widthProperty().addListener(detailWidthListener);
            applyDetailLayout();
        }
    }

    private void setupResponsiveDetailLayout() {
        detailWidthListener = (obs, oldWidth, newWidth) -> applyDetailLayout();
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.widthProperty().removeListener(detailWidthListener);
            }
            if (newScene != null) {
                newScene.widthProperty().addListener(detailWidthListener);
                applyDetailLayout();
            }
        });
    }

    private void applyDetailLayout() {
        boolean detailOpen = currentSelectedAlert != null && detailPane.isVisible();
        boolean narrow = rootPane.getScene() != null
                && rootPane.getScene().getWidth() < DETAIL_STACK_BREAKPOINT;
        boolean showList = !detailOpen || !narrow;
        listSection.setVisible(showList);
        listSection.setManaged(showList);
    }

    private void setupFilters() {
        cmbSeverity.setItems(FXCollections.observableArrayList("All Severity", "CRITICAL", "WARNING", "INFO"));
        cmbSeverity.getSelectionModel().selectFirst();
        cmbSeverity.valueProperty().addListener((obs, oldVal, newVal) -> updateFilter());

        cmbStatus.setItems(FXCollections.observableArrayList("All Status", "Active", "Resolved"));
        cmbStatus.getSelectionModel().selectFirst();
        cmbStatus.valueProperty().addListener((obs, oldVal, newVal) -> updateFilter());

        txtSearch.textProperty().addListener((obs, oldText, newText) -> updateFilter());
    }

    private void updateFilter() {
        if (filteredList == null) return;
        
        String sevFilter = cmbSeverity.getValue();
        String statusFilter = cmbStatus.getValue();
        String searchFilter = txtSearch.getText().toLowerCase();

        filteredList.setPredicate(alert -> {
            boolean sevMatch = sevFilter.equals("All Severity") || alert.getSeverity().name().equals(sevFilter);
            
            boolean statusMatch = statusFilter.equals("All Status");
            if (!statusMatch) {
                if (statusFilter.equals("Resolved")) statusMatch = alert.isResolved();
                if (statusFilter.equals("Active")) statusMatch = !alert.isResolved();
            }

            boolean searchMatch = searchFilter.isEmpty() || 
                                  alert.getMessage().toLowerCase().contains(searchFilter) ||
                                  formatType(alert.getAlertType()).toLowerCase().contains(searchFilter) ||
                                  String.valueOf(alert.getPlotId()).contains(searchFilter);

            return sevMatch && statusMatch && searchMatch;
        });
        
        lblPagination.setText("Showing 1 to " + filteredList.size() + " of " + masterList.size() + " alerts");
    }

    private void setupColumns() {
        alertTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colSeverity.setResizable(false);
        colType.setResizable(false);
        colPlot.setResizable(false);
        colMessage.setResizable(false);
        colTimestamp.setResizable(false);
        colStatus.setResizable(false);
        colAction.setResizable(false);

        // ── Severity — colour-coded badge ──
        colSeverity.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getSeverity().name()));
        colSeverity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                if (empty || severity == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(severity);
                    badge.getStyleClass().addAll("badge", getBadgeClass(severity));
                    setGraphic(badge);
                }
            }
        });

        // ── Alert Type ──
        colType.setCellValueFactory(cell ->
                new SimpleStringProperty(formatType(cell.getValue().getAlertType())));

        // ── Plot ID ──
        colPlot.setCellValueFactory(new PropertyValueFactory<>("plotId"));

        // ── Message ──
        colMessage.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getMessage()));

        // ── Timestamp — formatted ──
        colTimestamp.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTimestamp().format(TIME_FMT)));

        // ── Status — Resolved / Active badge ──
        colStatus.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().isResolved() ? "Resolved" : "Active"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().addAll("badge",
                            status.equals("Resolved") ? "badge-normal" : "badge-active");
                    setGraphic(badge);
                }
            }
        });

        // ── Action — Resolve button ──
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
                if (empty) {
                    setGraphic(null);
                } else {
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
        // P2.2: async. Initialize empty masterList/filteredList synchronously so
        // the filter listeners (which can fire as soon as the user types) don't
        // hit a null filteredList during the in-flight fetch. The setAll(...)
        // call when the async completes propagates through FilteredList to the
        // table automatically.
        masterList = FXCollections.observableArrayList();
        filteredList = new FilteredList<>(masterList, p -> true);
        alertTable.setItems(filteredList);
        updateSummaryCards();
        updateFilter();

        AsyncCalls.runAndApply(
                alertService::getAllAlerts,
                alerts -> {
                    masterList.setAll(alerts);
                    updateSummaryCards();
                    updateFilter();
                },
                err -> {
                    alertTable.setPlaceholder(new Label("No database connection — alerts unavailable"));
                    System.err.println("Failed to load alerts: " + err.getMessage());
                }
        );
    }

    private void updateSummaryCards() {
        int total      = masterList.size();
        int unresolved = (int) masterList.stream().filter(a -> !a.isResolved()).count();
        int critical   = (int) masterList.stream().filter(Alert::isCritical).count();
        int warnings   = (int) masterList.stream().filter(a -> a.getSeverity().name().equals("WARNING")).count();
        int resolved   = total - unresolved;

        lblCritical.setText(String.valueOf(critical));
        lblWarnings.setText(String.valueOf(warnings));
        lblUnresolved.setText(String.valueOf(unresolved));
        lblResolved.setText(String.valueOf(resolved));
    }

    // ═══════════════ DETAIL PANE LOGIC ═══════════════

    private void showAlertDetails(Alert alert) {
        currentSelectedAlert = alert;
        detailPane.setVisible(true);
        detailPane.setManaged(true);
        applyDetailLayout();

        // Header
        lblDetailSeverity.setText(alert.getSeverity().name());
        lblDetailSeverity.getStyleClass().removeAll("badge-high", "badge-low", "badge-info", "badge-normal");
        lblDetailSeverity.getStyleClass().add(getBadgeClass(alert.getSeverity().name()));

        lblDetailStatus.setText(alert.isResolved() ? "Resolved" : "Active");
        lblDetailTitle.setText(formatType(alert.getAlertType()) + " Alert");
        lblDetailPlot.setText("Plot " + alert.getPlotId());
        lblDetailMessage.setText(alert.getMessage());

        // Grid
        lblGridSensorType.setText(getSensorType(alert.getAlertType()));
        lblGridCurrentVal.setText("N/A"); // Ideally from alert payload
        lblGridThreshold.setText("N/A");
        lblGridTriggered.setText(alert.getTimestamp().format(TIME_FMT));
        lblGridSensorId.setText("SENSOR-P" + alert.getPlotId());

        // Setup Chart with dummy historical data
        populateDummyChart();

        // Buttons
        btnDetailResolve.setDisable(alert.isResolved());
        if(alert.isResolved()) {
            btnDetailResolve.setText("Resolved");
        } else {
            btnDetailResolve.setText("Mark as Resolved");
        }
    }

    @FXML
    private void onCloseDetails() {
        detailPane.setVisible(false);
        detailPane.setManaged(false);
        alertTable.getSelectionModel().clearSelection();
        currentSelectedAlert = null;
        applyDetailLayout();
    }

    @FXML
    private void onDetailResolve() {
        if (currentSelectedAlert != null && !currentSelectedAlert.isResolved()) {
            onResolveAlert(currentSelectedAlert);
            showAlertDetails(currentSelectedAlert); // refresh details pane
        }
    }

    private void populateDummyChart() {
        detailChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Sensor Reading");
        
        Random r = new Random();
        int base = 35;
        for (int i = 5; i > 0; i--) {
            series.getData().add(new XYChart.Data<>("-" + i + "m", base + r.nextInt(10)));
        }
        series.getData().add(new XYChart.Data<>("Now", base + r.nextInt(10)));
        detailChart.getData().add(series);
    }

    // ═══════════════ RESOLVE ACTION ═══════════════

    private void onResolveAlert(Alert alert) {
        // P2.2: async. In-memory `alert.resolve()` and the UI refresh run on the
        // FX thread inside the apply lambda — ordered after the DB write
        // completes so a failed UPDATE doesn't leave the model marked resolved.
        AsyncCalls.runAndApply(
                () -> { alertService.resolveAlert(alert.getAlertId()); return null; },
                ignored -> {
                    alert.resolve();
                    alertTable.refresh();
                    updateSummaryCards();
                    updateFilter();
                },
                err -> System.err.println("Failed to resolve alert: " + err.getMessage())
        );
    }

    // ═══════════════ UTILITY METHODS ═══════════════

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
        if (alertType.contains("MOISTURE")) return "Moisture Sensor";
        if (alertType.contains("HUMIDITY")) return "Humidity Sensor";
        return "Environmental Sensor";
    }
}
