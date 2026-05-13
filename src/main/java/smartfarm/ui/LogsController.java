package smartfarm.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import smartfarm.model.SystemLog;
import smartfarm.service.SystemLogManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LogsController {

    @FXML private Label lblTotal, lblInfo, lblWarnings, lblErrors;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbType;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private TableView<SystemLog> logTable;
    @FXML private TableColumn<SystemLog, String> colTimestamp, colType, colSource, colMessage, colUser;
    @FXML private Button btnExport, btnClear;

    private final SystemLogManager logManager = SystemLogManager.getInstance();
    private int lastKnownSize = -1;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        refresh();

        // Auto-refresh every 3 seconds so new log entries appear automatically
        Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            if (logManager.size() != lastKnownSize) {
                refresh();
            }
        }));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    private void setupTableColumns() {
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colTimestamp.setResizable(false);
        colType.setResizable(false);
        colSource.setResizable(false);
        colMessage.setResizable(false);
        colUser.setResizable(false);

        colTimestamp.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getFormattedTimestamp()));
        colType.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getType().name()));
        colSource.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getSource()));
        colMessage.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getMessage()));
        colUser.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getUser()));

        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    String color = switch (item) {
                        case "INFO" -> "#1d4ed8";
                        case "WARNING" -> "#d97706";
                        default -> "#dc2626";
                    };
                    setStyle("-fx-text-fill:" + color + ";-fx-font-weight:bold;");
                }
            }
        });
    }

    private void setupFilters() {
        cmbType.getItems().addAll("All", "INFO", "WARNING", "ERROR");
        cmbType.setValue("All");
        cmbType.setOnAction(e -> refresh());

        txtSearch.textProperty().addListener((obs, old, val) -> refresh());
        dpFrom.valueProperty().addListener((obs, old, val) -> refresh());
        dpTo.valueProperty().addListener((obs, old, val) -> refresh());
    }

    private void refresh() {
        List<SystemLog> logs = logManager.getLogs();
        lastKnownSize = logs.size();

        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String type = cmbType.getValue();
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        List<SystemLog> filtered = logs.stream()
                .filter(l -> search.isEmpty()
                        || l.getMessage().toLowerCase().contains(search)
                        || l.getSource().toLowerCase().contains(search)
                        || l.getUser().toLowerCase().contains(search))
                .filter(l -> "All".equals(type) || l.getType().name().equals(type))
                .filter(l -> from == null || !l.getTimestamp().toLocalDate().isBefore(from))
                .filter(l -> to == null || !l.getTimestamp().toLocalDate().isAfter(to))
                .collect(Collectors.toCollection(ArrayList::new));

        // Show newest logs first
        Collections.reverse(filtered);
        logTable.setItems(FXCollections.observableArrayList(filtered));
        if (filtered.isEmpty() && !search.isEmpty()) {
            logTable.setPlaceholder(new Label("No logs matching \"" + search + "\""));
        } else if (filtered.isEmpty()) {
            logTable.setPlaceholder(new Label("No logs found"));
        }

        long infoCount = logs.stream().filter(l -> l.getType() == SystemLog.LogType.INFO).count();
        long warnCount = logs.stream().filter(l -> l.getType() == SystemLog.LogType.WARNING).count();
        long errCount = logs.stream().filter(l -> l.getType() == SystemLog.LogType.ERROR).count();

        lblTotal.setText(String.valueOf(logs.size()));
        lblInfo.setText(String.valueOf(infoCount));
        lblWarnings.setText(String.valueOf(warnCount));
        lblErrors.setText(String.valueOf(errCount));
    }

    @FXML
    private void onExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Logs");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("system_logs.csv");

        File file = chooser.showSaveDialog(btnExport.getScene().getWindow());
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Timestamp,Type,Source,Message,User\n");
            for (SystemLog log : logTable.getItems()) {
                writer.write(String.format("%s,%s,%s,\"%s\",%s\n",
                        log.getFormattedTimestamp(), log.getType(),
                        log.getSource(), log.getMessage(), log.getUser()));
            }
            showAlert("Export", "Exported to " + file.getName(), Alert.AlertType.INFORMATION);
        } catch (IOException e) {
            showAlert("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void onClear() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Clear all logs?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                logManager.clear();
                refresh();
            }
        });
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}