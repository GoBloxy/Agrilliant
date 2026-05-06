package smartfarm.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.model.Alert;
import smartfarm.model.Alert.Severity;
import smartfarm.service.AlertService;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for the Alerts page.
 * Displays all alerts in a colour-coded TableView with filter and resolve actions.
 */
public class AlertController {

    // ── Summary Cards ──
    @FXML private Label lblTotalAlerts, lblUnresolved, lblCritical, lblResolved;

    // ── Filter Buttons ──
    @FXML private Button btnAll, btnUnresolved, btnCriticalOnly;

    // ── Alert Table ──
    @FXML private TableView<Alert> alertTable;
    @FXML private TableColumn<Alert, String> colSeverity;
    @FXML private TableColumn<Alert, String> colType;
    @FXML private TableColumn<Alert, String> colMessage;
    @FXML private TableColumn<Alert, Integer> colPlot;
    @FXML private TableColumn<Alert, String> colTimestamp;
    @FXML private TableColumn<Alert, String> colStatus;
    @FXML private TableColumn<Alert, Void>   colAction;

    // ── Status Bar ──
    @FXML private Label lblStatusMessage;

    private final AlertService alertService = new AlertService();
    private ObservableList<Alert> masterList;
    private FilteredList<Alert> filteredList;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy  hh:mm a");

    // Track active filter button
    private Button activeFilterBtn;

    // ═══════════════ INITIALIZATION ═══════════════

    @FXML
    public void initialize() {
        setupColumns();
        loadAlerts();
        activeFilterBtn = btnAll;
    }

    /**
     * Configures each TableColumn with cell value factories and custom cell factories
     * for colour-coded severity badges and action buttons.
     */
    private void setupColumns() {

        // ── Severity — colour-coded badge ──
        colSeverity.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getSeverity().name()));
        colSeverity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                if (empty || severity == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(severity);
                    badge.getStyleClass().addAll("badge", getBadgeClass(severity));
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // ── Alert Type ──
        colType.setCellValueFactory(cell ->
                new SimpleStringProperty(formatType(cell.getValue().getAlertType())));

        // ── Message ──
        colMessage.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getMessage()));

        // ── Plot ID ──
        colPlot.setCellValueFactory(new PropertyValueFactory<>("plotId"));

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
                    setText(null);
                } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().addAll("badge",
                            status.equals("Resolved") ? "badge-normal" : "badge-active");
                    setGraphic(badge);
                    setText(null);
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
                        // Already resolved — show a muted label instead
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

    /**
     * Fetches all alerts from the service layer,
     * populates the table, and updates the summary cards.
     */
    private void loadAlerts() {
        try {
            List<Alert> alerts = alertService.getAllAlerts();
            masterList = FXCollections.observableArrayList(alerts);
            filteredList = new FilteredList<>(masterList, p -> true);
            alertTable.setItems(filteredList);
            updateSummaryCards();
            lblStatusMessage.setText("Loaded " + alerts.size() + " alerts");
        } catch (RuntimeException e) {
            // DB not connected — show empty table with a friendly message
            masterList = FXCollections.observableArrayList();
            filteredList = new FilteredList<>(masterList, p -> true);
            alertTable.setItems(filteredList);
            alertTable.setPlaceholder(new Label("No database connection — alerts unavailable"));
            updateSummaryCards();
            lblStatusMessage.setText("Database not connected");
        }
    }

    /**
     * Recalculates and displays the summary card numbers.
     */
    private void updateSummaryCards() {
        int total      = masterList.size();
        int unresolved = (int) masterList.stream().filter(a -> !a.isResolved()).count();
        int critical   = (int) masterList.stream().filter(Alert::isCritical).count();
        int resolved   = total - unresolved;

        lblTotalAlerts.setText(String.valueOf(total));
        lblUnresolved.setText(String.valueOf(unresolved));
        lblCritical.setText(String.valueOf(critical));
        lblResolved.setText(String.valueOf(resolved));
    }

    // ═══════════════ FILTER HANDLERS ═══════════════

    @FXML
    private void onFilterAll() {
        filteredList.setPredicate(a -> true);
        setActiveFilter(btnAll);
        lblStatusMessage.setText("Showing all alerts");
    }

    @FXML
    private void onFilterUnresolved() {
        filteredList.setPredicate(a -> !a.isResolved());
        setActiveFilter(btnUnresolved);
        lblStatusMessage.setText("Showing unresolved alerts only");
    }

    @FXML
    private void onFilterCritical() {
        filteredList.setPredicate(Alert::isCritical);
        setActiveFilter(btnCriticalOnly);
        lblStatusMessage.setText("Showing critical alerts only");
    }

    @FXML
    private void onRefresh() {
        loadAlerts();
        // Reset to "All" filter
        if (filteredList != null) {
            filteredList.setPredicate(a -> true);
        }
        setActiveFilter(btnAll);
        lblStatusMessage.setText("Alerts refreshed");
    }

    private void setActiveFilter(Button btn) {
        if (activeFilterBtn != null) {
            activeFilterBtn.getStyleClass().remove("filter-active");
        }
        btn.getStyleClass().add("filter-active");
        activeFilterBtn = btn;
    }

    // ═══════════════ RESOLVE ACTION ═══════════════

    /**
     * Marks the given alert as resolved via AlertService,
     * then refreshes the table and summary cards.
     */
    private void onResolveAlert(Alert alert) {
        try {
            alertService.resolveAlert(alert.getAlertId());
            alert.resolve();                        // update local model
            alertTable.refresh();                    // repaint the table row
            updateSummaryCards();
            lblStatusMessage.setText("Alert #" + alert.getAlertId() + " resolved");
        } catch (RuntimeException e) {
            lblStatusMessage.setText("Failed to resolve alert: " + e.getMessage());
        }
    }

    // ═══════════════ UTILITY METHODS ═══════════════

    /**
     * Returns the CSS class for a severity badge.
     */
    private String getBadgeClass(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "badge-high";
            case "WARNING"  -> "badge-low";
            case "INFO"     -> "badge-info";
            default         -> "badge-normal";
        };
    }

    /**
     * Converts alert type codes like "HIGH_TEMP" into readable text "High Temp".
     */
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
}
