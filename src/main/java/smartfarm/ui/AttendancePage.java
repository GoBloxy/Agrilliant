package smartfarm.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Attendance;
import smartfarm.model.Worker;
import smartfarm.service.AttendanceService;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttendancePage extends VBox {

    private final AttendanceService attendanceService = new AttendanceService();
    private final WorkerDAO workerDAO = new WorkerDAO();
    private final TableView<Attendance> table = new TableView<>();
    private final ObservableList<Attendance> data = FXCollections.observableArrayList();
    private final Map<Integer, String> workerNames = new HashMap<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AttendancePage() {
        setSpacing(20);
        setPadding(new Insets(30));
        setStyle("-fx-background-color: #f8f9fa;");

        loadWorkerNames();

        getChildren().addAll(buildHeader(), buildTable(), buildFooter());
        refreshData();
    }

    private HBox buildHeader() {
        Label title = new Label("Attendance Records");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");

        Label subtitle = new Label("R307 Fingerprint Scanner");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #888;");

        VBox left = new VBox(2, title, subtitle);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6;");
        refreshBtn.setOnAction(e -> refreshData());

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(left, Priority.ALWAYS);
        header.getChildren().addAll(left, refreshBtn);
        return header;
    }

    @SuppressWarnings("unchecked")
    private TableView<Attendance> buildTable() {
        TableColumn<Attendance, String> colWorker = new TableColumn<>("Worker");
        colWorker.setCellValueFactory(cd ->
                new SimpleStringProperty(workerNames.getOrDefault(cd.getValue().getWorkerId(), "ID: " + cd.getValue().getWorkerId())));
        colWorker.setPrefWidth(180);

        TableColumn<Attendance, String> colCheckIn = new TableColumn<>("Check In");
        colCheckIn.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getCheckIn().format(FMT)));
        colCheckIn.setPrefWidth(170);

        TableColumn<Attendance, String> colCheckOut = new TableColumn<>("Check Out");
        colCheckOut.setCellValueFactory(cd -> {
            if (cd.getValue().getCheckOut() != null) {
                return new SimpleStringProperty(cd.getValue().getCheckOut().format(FMT));
            }
            return new SimpleStringProperty("-- Still on site --");
        });
        colCheckOut.setPrefWidth(170);

        TableColumn<Attendance, String> colDuration = new TableColumn<>("Duration");
        colDuration.setCellValueFactory(cd -> {
            Attendance a = cd.getValue();
            if (a.getCheckOut() == null) return new SimpleStringProperty("Active");
            long mins = java.time.Duration.between(a.getCheckIn(), a.getCheckOut()).toMinutes();
            long hrs = mins / 60;
            long rem = mins % 60;
            return new SimpleStringProperty(hrs + "h " + rem + "m");
        });
        colDuration.setPrefWidth(100);

        TableColumn<Attendance, String> colDevice = new TableColumn<>("Device");
        colDevice.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDeviceCode() != null ? cd.getValue().getDeviceCode() : "-"));
        colDevice.setPrefWidth(130);

        TableColumn<Attendance, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cd -> {
            if (cd.getValue().isCheckedOut()) {
                return new SimpleStringProperty("Checked Out");
            }
            return new SimpleStringProperty("On Site");
        });
        colStatus.setPrefWidth(100);

        table.getColumns().addAll(colWorker, colCheckIn, colCheckOut, colDuration, colDevice, colStatus);
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No attendance records found"));
        table.setStyle("-fx-background-color: white; -fx-background-radius: 8;");
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }

    private HBox buildFooter() {
        Label info = new Label("Workers scan their fingerprint on the R307 sensor to check in/out automatically.");
        info.setStyle("-fx-font-size: 12px; -fx-text-fill: #999;");

        HBox footer = new HBox(info);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(5, 0, 0, 0));
        return footer;
    }

    private void refreshData() {
        try {
            List<Attendance> records = attendanceService.getAllRecords();
            data.setAll(records);
        } catch (Exception e) {
            data.clear();
            System.err.println("Failed to load attendance: " + e.getMessage());
        }
    }

    private void loadWorkerNames() {
        try {
            List<Worker> workers = workerDAO.getAll();
            for (Worker w : workers) {
                workerNames.put(w.getWorkerId(), w.getFullName());
            }
        } catch (SQLException e) {
            System.err.println("Failed to load worker names: " + e.getMessage());
        }
    }
}
