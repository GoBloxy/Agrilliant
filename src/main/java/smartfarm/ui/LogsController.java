package smartfarm.ui;

import com.gluonhq.charm.glisten.control.CharmListCell;
import com.gluonhq.charm.glisten.control.CharmListView;
import com.gluonhq.charm.glisten.control.ListTile;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import smartfarm.model.SystemLog;
import smartfarm.service.SystemLogManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class LogsController {

    @FXML private Label lblTotal, lblInfo, lblWarnings, lblErrors;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbType;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private CharmListView<SystemLog, String> logList;
    @FXML private Button btnExport, btnClear;

    private final SystemLogManager logManager = SystemLogManager.getInstance();

    @FXML
    public void initialize() {
        setupCellFactory();
        setupFilters();
        refresh();
    }

    /**
     * Builds each list row as a Gluon {@link ListTile}: a severity-coloured
     * icon badge on the left (info / warning / error palette), three text
     * lines (message, timestamp + user, source). No trailing actions —
     * logs are read-only.
     */
    private void setupCellFactory() {
        logList.setCellFactory(view -> new LogListCell());
    }

    private static final class LogListCell extends CharmListCell<SystemLog> {
        private final ListTile tile = new ListTile();
        private final FontIcon badgeIcon = new FontIcon();
        private final StackPane badge = new StackPane(badgeIcon);

        LogListCell() {
            badgeIcon.setIconSize(20);
            badge.setStyle(
                    "-fx-min-width:42;-fx-min-height:42;-fx-pref-width:42;-fx-pref-height:42;"
                    + "-fx-background-radius:10;");
            tile.setPrimaryGraphic(badge);
        }

        @Override
        public void updateItem(SystemLog l, boolean empty) {
            super.updateItem(l, empty);
            if (empty || l == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String bg, fg, icon;
            switch (l.getType()) {
                case INFO    -> { bg = "#dbeafe"; fg = "#1d4ed8"; icon = "fth-info"; }
                case WARNING -> { bg = "#fef3c7"; fg = "#d97706"; icon = "fth-alert-triangle"; }
                case ERROR   -> { bg = "#fee2e2"; fg = "#dc2626"; icon = "fth-x-circle"; }
                default      -> { bg = "#e8f5e9"; fg = "#2e7d32"; icon = "fth-file-text"; }
            }
            badge.setStyle(
                    "-fx-min-width:42;-fx-min-height:42;-fx-pref-width:42;-fx-pref-height:42;"
                    + "-fx-background-radius:10;-fx-background-color:" + bg + ";");
            badgeIcon.setIconLiteral(icon);
            badgeIcon.setIconColor(Color.web(fg));

            tile.setTextLine(0, l.getMessage());
            tile.setTextLine(1, l.getFormattedTimestamp() + "  ·  " + safe(l.getUser()));
            tile.setTextLine(2, "Source: " + safe(l.getSource()) + "  ·  " + l.getType().name());

            setText(null);
            setGraphic(tile);
        }

        private static String safe(String s) {
            return s != null && !s.isEmpty() ? s : "--";
        }
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
                .collect(Collectors.toList());

        logList.setItems(FXCollections.observableArrayList(filtered));

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
            for (SystemLog log : logList.itemsProperty().get()) {
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