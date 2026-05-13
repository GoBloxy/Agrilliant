package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.TaskDAO;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import smartfarm.service.WorkerService;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WorkerController {

    @FXML private Label lblTotalWorkers, lblOnDuty, lblAvailable, lblBusy;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TableView<Worker> workerTable;
    @FXML private TableColumn<Worker, String> colName, colPhone, colJobTitle, colSkills, colStatus, colWorkload, colFingerprint, colActions;
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
        setupTableColumns();
        loadWorkers();
        setupFilters();
        updateSummaryCards();
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
        colPhone.setResizable(false);
        colJobTitle.setResizable(false);
        colSkills.setResizable(false);
        colStatus.setResizable(false);
        colWorkload.setResizable(false);
        colFingerprint.setResizable(false);
        colActions.setResizable(false);

        colName.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFullName()));
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
                    Worker w = getTableRow() != null ? getTableRow().getItem() : null;
                    if (w != null) onEditWorker(w);
                });
                delBtn.setOnAction(e -> {
                    Worker w = getTableRow() != null ? getTableRow().getItem() : null;
                    if (w != null) onDeleteWorker(w);
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

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String status = cmbStatus.getValue();
        List<Worker> filtered = allWorkers.stream()
                .filter(w -> search.isEmpty()
                        || w.getFullName().toLowerCase().contains(search)
                        || (w.getJobTitle() != null && w.getJobTitle().toLowerCase().contains(search)))
                .filter(w -> "All".equals(status)
                        || ("On Duty".equals(status) && w.isOnDuty())
                        || ("Off Duty".equals(status) && !w.isOnDuty()))
                .collect(Collectors.toList());
        workerTable.setItems(FXCollections.observableArrayList(filtered));
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
        TextField fpField = new TextField(existing != null && existing.getFingerprintId() != null
                ? String.valueOf(existing.getFingerprintId()) : "");
        fpField.setPromptText("R307 Fingerprint ID (e.g. 1, 2, 3...)");
        CheckBox onDutyCb = new CheckBox("On Duty");
        onDutyCb.setSelected(existing != null && existing.isOnDuty());

<<<<<<< HEAD
        VBox form = new VBox(10, nameField, phoneField, jobField, skillsField, fpField, onDutyCb);
=======
        VBox form = new VBox(10, nameField, phoneField, emailField, jobField, skillsField, onDutyCb);
>>>>>>> 83dc482b39cd9e222d5bd246f0547b770d8abfd2
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
                        emailField.getText().trim(), "", // email and empty password hash
                        jobField.getText().trim(), skillsField.getText().trim(),
                        existing != null ? existing.getManagerId() : currentManagerId);
                worker.setOnDuty(onDutyCb.isSelected());
                String fpText = fpField.getText().trim();
                if (!fpText.isEmpty()) {
                    try {
                        worker.setFingerprintId(Integer.parseInt(fpText));
                    } catch (NumberFormatException e) {
                        showAlert("Validation", "Fingerprint ID must be a number");
                        return null;
                    }
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