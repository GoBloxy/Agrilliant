package smartfarm.ui;

import com.gluonhq.charm.glisten.control.CharmListCell;
import com.gluonhq.charm.glisten.control.CharmListView;
import com.gluonhq.charm.glisten.control.ListTile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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

    @FXML private Label lblTotalWorkers, lblOnDuty, lblAvailable, lblBusy;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private CharmListView<Worker, String> workerList;
    @FXML private Button btnAddWorker;

    private final WorkerService workerService = new WorkerService(new WorkerDAO(), new TaskDAO());
    private final ObservableList<Worker> allWorkers = FXCollections.observableArrayList();
    private List<Task> allTasks;

    // Set by DashboardController so new workers get the correct manager
    private static int currentManagerId = 1;
    public static void setCurrentManagerId(int id) { currentManagerId = id; }

    @FXML
    public void initialize() {
        loadTasks();
        setupCellFactory();
        setupFilters();
        loadWorkers();
        updateSummaryCards();
    }

    private void loadTasks() {
        try {
            allTasks = new TaskDAO().getAll();
        } catch (SQLException e) {
            allTasks = List.of();
        }
    }

    /**
     * Builds each list row as a Gluon {@link ListTile}: a circular avatar on
     * the left, three text lines (name, role + status, workload + phone),
     * and edit/delete icon buttons on the right. The pattern collapses
     * cleanly onto a phone row without horizontal scrolling.
     */
    private void setupCellFactory() {
        workerList.setCellFactory(view -> new WorkerListCell());
    }

    private final class WorkerListCell extends CharmListCell<Worker> {
        private final ListTile tile = new ListTile();
        private final Button editBtn = new Button("", new FontIcon("fth-edit-2"));
        private final Button delBtn  = new Button("", new FontIcon("fth-trash-2"));
        private final HBox actionBox = new HBox(6, editBtn, delBtn);

        WorkerListCell() {
            editBtn.getStyleClass().add("icon-btn");
            delBtn.getStyleClass().add("icon-btn");
            actionBox.setAlignment(Pos.CENTER);
            editBtn.setOnAction(e -> {
                Worker w = getItem();
                if (w != null) onEditWorker(w);
            });
            delBtn.setOnAction(e -> {
                Worker w = getItem();
                if (w != null) onDeleteWorker(w);
            });
            tile.setSecondaryGraphic(actionBox);

            FontIcon avatarIcon = new FontIcon("fth-user");
            avatarIcon.setIconSize(20);
            StackPane avatar = new StackPane(avatarIcon);
            avatar.setStyle(
                    "-fx-background-color:#e8f5e9;-fx-background-radius:100;"
                    + "-fx-min-width:44;-fx-min-height:44;-fx-pref-width:44;-fx-pref-height:44;");
            tile.setPrimaryGraphic(avatar);
        }

        @Override
        public void updateItem(Worker w, boolean empty) {
            super.updateItem(w, empty);
            if (empty || w == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String name     = w.getFullName();
            String role     = w.getJobTitle() != null ? w.getJobTitle() : "--";
            String status   = w.isOnDuty() ? "On Duty" : "Off Duty";
            String workload = w.getActiveTaskCount(allTasks) + " tasks";
            String phone    = w.getPhone() != null ? w.getPhone() : "--";
            String fpStatus = w.getFingerprintId() != null
                    ? "FP ID: " + w.getFingerprintId()
                    : "FP: not enrolled";

            tile.setTextLine(0, name);
            tile.setTextLine(1, role + "  ·  " + status);
            tile.setTextLine(2, workload + "  ·  " + phone + "  ·  " + fpStatus);
            setText(null);
            setGraphic(tile);
        }
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
        workerList.setItems(filteredWorkers);
    }

    private void updateSummaryCards() {
        int total = allWorkers.size();
        long onDuty = allWorkers.stream().filter(Worker::isOnDuty).count();
        long available = allWorkers.stream().filter(w -> w.isOnDuty() && w.isAvailable(allTasks)).count();
        long busy = onDuty - available;
        lblTotalWorkers.setText(String.valueOf(total));
        lblOnDuty.setText(String.valueOf(onDuty));
        lblAvailable.setText(String.valueOf(available));
        lblBusy.setText(String.valueOf(busy));
    }

    @FXML
    private void onAddWorker() {
        Dialog<Worker> dialog = createWorkerDialog(null);
        dialog.showAndWait().ifPresent(worker -> {
            try {
                workerService.addWorker(worker);
                loadWorkers();
                updateSummaryCards();
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

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    showAlert("Validation", "Name is required");
                    return null;
                }
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