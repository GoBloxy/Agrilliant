package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.TaskDAO;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import smartfarm.service.FingerprintService;
import smartfarm.service.WorkerService;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WorkerController {

    @FXML private Label lblTotalWorkers, lblOnDuty, lblOffDuty;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TableView<Worker> workerTable;
    @FXML private TableColumn<Worker, String> colName, colEmail, colPhone, colJobTitle, colSkills, colStatus, colWorkload, colFingerprint, colActions;
    @FXML private Button btnAddWorker;
    @FXML private PieChart statusPieChart;
    @FXML private VBox statusLegendBox;

    private final WorkerService workerService = new WorkerService(new WorkerDAO(), new TaskDAO());
    private final ObservableList<Worker> allWorkers = FXCollections.observableArrayList();
    private List<Task> allTasks;

    // Set by DashboardController so new workers get the correct manager
    private static int currentManagerId = 1;
    public static void setCurrentManagerId(int id) { currentManagerId = id; }

    @FXML
    public void initialize() {
        loadTasks();
        setupTableColumns();
        setupFilters();
        loadWorkers();
        updateSummaryCards();
        populateCharts();
    }

    private void loadTasks() {
        try {
            allTasks = new TaskDAO().getAll();
        } catch (SQLException e) {
            allTasks = List.of();
        }
    }

    private void setupTableColumns() {
        workerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colName.setResizable(false);
        colEmail.setResizable(false);
        colPhone.setResizable(false);
        colJobTitle.setResizable(false);
        colSkills.setResizable(false);
        colStatus.setResizable(false);
        colWorkload.setResizable(false);
        colFingerprint.setResizable(false);
        colActions.setResizable(false);

        colName.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFullName()));
        colEmail.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getEmail() != null && !data.getValue().getEmail().isEmpty()
                                ? data.getValue().getEmail() : "--"));
        colPhone.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getPhone() != null ? data.getValue().getPhone() : "--"));
        colJobTitle.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getJobTitle() != null ? data.getValue().getJobTitle() : "--"));
        colSkills.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getSkills() != null ? data.getValue().getSkills() : "--"));
        colStatus.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().isOnDuty() ? "On Duty" : "Off Duty"));
        colWorkload.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getActiveTaskCount(allTasks) + " tasks"));
        colFingerprint.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getFingerprintId() != null
                                ? "ID: " + data.getValue().getFingerprintId()
                                : "Not enrolled"));

        colActions.setCellFactory(col -> new TableCell<Worker, String>() {
            private final Button editBtn = new Button("", new FontIcon("fth-edit-2"));
            private final Button delBtn = new Button("", new FontIcon("fth-trash-2"));
            private final HBox box = new HBox(6, editBtn, delBtn);
            {
                editBtn.getStyleClass().add("icon-btn");
                delBtn.getStyleClass().add("icon-btn");
                editBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        onEditWorker(getTableView().getItems().get(idx));
                });
                delBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        onDeleteWorker(getTableView().getItems().get(idx));
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadWorkers() {
        try {
            allWorkers.setAll(workerService.getAllWorkers());
        } catch (RuntimeException e) {
            allWorkers.clear();
        }
        applyFilters();
    }

    private void setupFilters() {
        cmbStatus.getItems().addAll("All", "On Duty", "Off Duty");
        cmbStatus.setValue("All");
        cmbStatus.setOnAction(e -> applyFilters());
        txtSearch.textProperty().addListener((obs, old, val) -> applyFilters());
    }

    private final ObservableList<Worker> filteredWorkers = FXCollections.observableArrayList();

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String status = cmbStatus.getValue() != null ? cmbStatus.getValue() : "All";
        List<Worker> filtered = allWorkers.stream()
                .filter(w -> search.isEmpty()
                        || w.getFullName().toLowerCase().contains(search)
                        || (w.getJobTitle() != null && w.getJobTitle().toLowerCase().contains(search)))
                .filter(w -> "All".equals(status)
                        || ("On Duty".equals(status) && w.isOnDuty())
                        || ("Off Duty".equals(status) && !w.isOnDuty()))
                .collect(Collectors.toList());
        filteredWorkers.setAll(filtered);
        workerTable.setItems(filteredWorkers);
        if (filtered.isEmpty() && !search.isEmpty()) {
            workerTable.setPlaceholder(new Label("No workers matching \"" + search + "\""));
        } else if (filtered.isEmpty()) {
            workerTable.setPlaceholder(new Label("No workers found"));
        }
    }

    private void updateSummaryCards() {
        int total = allWorkers.size();
        long onDuty = allWorkers.stream().filter(Worker::isOnDuty).count();
        long offDuty = total - onDuty;
        lblTotalWorkers.setText(String.valueOf(total));
        lblOnDuty.setText(String.valueOf(onDuty));
        lblOffDuty.setText(String.valueOf(offDuty));
    }

    private void populateCharts() {
        // ── Status Pie Chart ──
        statusPieChart.getData().clear();
        statusLegendBox.getChildren().clear();
        long onDuty = allWorkers.stream().filter(Worker::isOnDuty).count();
        long offDuty = allWorkers.size() - onDuty;
        int total = allWorkers.size();

        if (total == 0) {
            statusPieChart.getData().add(new PieChart.Data("No Workers", 1));
            javafx.application.Platform.runLater(() ->
                statusPieChart.getData().get(0).getNode().setStyle("-fx-pie-color:#e5e7eb;"));
            return;
        }

        PieChart.Data onData = new PieChart.Data("On Duty", onDuty);
        PieChart.Data offData = new PieChart.Data("Off Duty", offDuty);
        statusPieChart.getData().addAll(onData, offData);
        javafx.application.Platform.runLater(() -> {
            onData.getNode().setStyle("-fx-pie-color:#22c55e;");
            offData.getNode().setStyle("-fx-pie-color:#f87171;");
        });

        statusLegendBox.getChildren().addAll(
                buildLegendRow("#22c55e", "On Duty", onDuty, total > 0 ? (int)(onDuty * 100 / total) : 0),
                buildLegendRow("#f87171", "Off Duty", offDuty, total > 0 ? (int)(offDuty * 100 / total) : 0)
        );

    }

    private HBox buildLegendRow(String color, String label, long count, int pct) {
        Circle dot = new Circle(6);
        dot.setFill(Color.web(color));
        Label lbl = new Label(label + "  " + count + " (" + pct + "%)");
        lbl.setStyle("-fx-font-size:12;-fx-text-fill:#374151;");
        HBox row = new HBox(8, dot, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    @FXML
    private void onAddWorker() {
        Dialog<Worker> dialog = createWorkerDialog(null);
        dialog.showAndWait().ifPresent(worker -> {
            try {
                workerService.addWorker(worker);
                loadWorkers();
                updateSummaryCards();
                populateCharts();
            } catch (RuntimeException e) {
                // Rollback: delete enrolled fingerprint from R307 if save failed
                if (worker.getFingerprintId() != null && worker.getFingerprintId() > 0) {
                    int fpId = worker.getFingerprintId();
                    new Thread(() -> {
                        FingerprintService fps = new FingerprintService();
                        if (fps.autoConnect()) {
                            fps.deleteTemplate(fpId);
                            fps.disconnect();
                            System.out.println("Rolled back fingerprint ID " + fpId + " from R307");
                        }
                    }).start();
                }
                showAlert("Error", e.getMessage());
            }
        });
    }

    private void onEditWorker(Worker worker) {
        if (worker == null) return;
        Dialog<Worker> dialog = createWorkerDialog(worker);
        dialog.showAndWait().ifPresent(updated -> {
            try {
                updated.setWorkerId(worker.getWorkerId());
                workerService.updateWorkerData(updated);
                loadWorkers();
                updateSummaryCards();
                populateCharts();
            } catch (RuntimeException e) {
                showAlert("Error", e.getMessage());
            }
        });
    }

    private void onDeleteWorker(Worker worker) {
        if (worker == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + worker.getFullName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    // Delete fingerprint from R307 if enrolled
                    if (worker.getFingerprintId() != null && worker.getFingerprintId() > 0) {
                        int fpId = worker.getFingerprintId();
                        new Thread(() -> {
                            FingerprintService fps = new FingerprintService();
                            if (fps.autoConnect()) {
                                fps.deleteTemplate(fpId);
                                fps.disconnect();
                                System.out.println("Deleted fingerprint ID " + fpId + " from R307");
                            }
                        }).start();
                    }
                    workerService.deleteWorker(worker.getWorkerId());
                    loadWorkers();
                    updateSummaryCards();
                    populateCharts();
                } catch (RuntimeException e) {
                    showAlert("Error", e.getMessage());
                }
            }
        });
    }

    private Dialog<Worker> createWorkerDialog(Worker existing) {
        Dialog<Worker> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Worker" : "Edit Worker");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getFullName() : "");
        nameField.setPromptText("Full Name");
        TextField phoneField = new TextField(existing != null ? existing.getPhone() : "");
        phoneField.setPromptText("Phone");
        TextField emailField = new TextField(existing != null ? existing.getEmail() : "");
        emailField.setPromptText("Email");
        TextField jobField = new TextField(existing != null ? existing.getJobTitle() : "");
        jobField.setPromptText("Job Title");
        TextField skillsField = new TextField(existing != null ? existing.getSkills() : "");
        skillsField.setPromptText("Skills (comma separated)");
        // Fingerprint enrollment
        final int[] enrolledFpId = { existing != null && existing.getFingerprintId() != null
                ? existing.getFingerprintId() : -1 };
        Label fpStatusLabel = new Label(enrolledFpId[0] >= 0
                ? "Fingerprint enrolled (ID: " + enrolledFpId[0] + ")"
                : "No fingerprint enrolled");
        fpStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #6b7280;");
        fpStatusLabel.setWrapText(true);
        fpStatusLabel.setMaxWidth(300);
        Button enrollBtn = new Button(enrolledFpId[0] >= 0 ? "Re-enroll Fingerprint" : "Enroll Fingerprint");
        enrollBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 16;");
        enrollBtn.setOnAction(ev -> {
            enrollBtn.setDisable(true);
            fpStatusLabel.setText("Connecting to sensor...");
            new Thread(() -> {
                FingerprintService fps = new FingerprintService();
                try {
                    if (!fps.autoConnect()) {
                        javafx.application.Platform.runLater(() -> {
                            fpStatusLabel.setText("Cannot connect to ESP32. Close Arduino Serial Monitor first.");
                            enrollBtn.setDisable(false);
                        });
                        return;
                    }
                    int nextId = fps.getTemplateCount() + 1;
                    if (enrolledFpId[0] >= 0) nextId = enrolledFpId[0]; // re-enroll same slot

                    final int slotId = nextId;
                    boolean ok = fps.enroll(slotId, msg ->
                            javafx.application.Platform.runLater(() -> fpStatusLabel.setText(msg)));

                    if (ok) {
                        enrolledFpId[0] = slotId;
                        javafx.application.Platform.runLater(() -> {
                            fpStatusLabel.setText("Fingerprint enrolled (ID: " + slotId + ")");
                            enrollBtn.setText("Re-enroll Fingerprint");
                        });
                    }
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                            fpStatusLabel.setText("Error: " + ex.getMessage()));
                } finally {
                    fps.disconnect();
                    javafx.application.Platform.runLater(() -> enrollBtn.setDisable(false));
                }
            }).start();
        });
        VBox fpBox = new VBox(6, enrollBtn, fpStatusLabel);

        CheckBox onDutyCb = new CheckBox("On Duty");
        onDutyCb.setSelected(existing != null && existing.isOnDuty());

        VBox form = new VBox(10, nameField, phoneField, emailField, jobField, skillsField, fpBox, onDutyCb);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        Button saveBtnNode = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveBtnNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (nameField.getText().trim().isEmpty()) {
                showAlert("Validation", "Name is required"); event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                Worker worker = new Worker(name, phoneField.getText().trim(),
                        emailField.getText().trim(), "",
                        jobField.getText().trim(), skillsField.getText().trim(),
                        existing != null ? existing.getManagerId() : currentManagerId);
                worker.setOnDuty(onDutyCb.isSelected());
                if (enrolledFpId[0] >= 0) {
                    worker.setFingerprintId(enrolledFpId[0]);
                }
                return worker;
            }
            return null;
        });
        return dialog;
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}