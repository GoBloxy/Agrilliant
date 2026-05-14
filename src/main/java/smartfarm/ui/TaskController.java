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

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TaskController {

    @FXML private Label lblTotalTasks, lblPending, lblInProgress, lblCompleted;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private CharmListView<Task, String> taskList;
    @FXML private Button btnAddTask;

    private final TaskDAO taskDAO = new TaskDAO();
    private final WorkerDAO workerDAO = new WorkerDAO();
    private final ObservableList<Task> allTasks = FXCollections.observableArrayList();
    private final Map<Integer, Worker> workerCache = new HashMap<>();

    @FXML
    public void initialize() {
        loadWorkerCache();
        setupCellFactory();
        loadTasks();
        setupFilters();
        updateSummaryCards();
    }

    private void loadWorkerCache() {
        try {
            for (Worker w : workerDAO.getAll()) {
                workerCache.put(w.getWorkerId(), w);
            }
        } catch (SQLException e) {
            // cache will be empty
        }
    }

    /**
     * Builds each list row as a Gluon {@link ListTile}: a colour-coded
     * status icon on the left, three text lines (description, assignee +
     * plot, due date + priority + status), and advance / revert / delete
     * icon buttons on the right. Advance and revert are disabled at the
     * appropriate Status boundaries, matching the previous TableCell
     * behaviour.
     */
    private void setupCellFactory() {
        taskList.setCellFactory(view -> new TaskListCell());
    }

    private final class TaskListCell extends CharmListCell<Task> {
        private final ListTile tile = new ListTile();
        private final Button advanceBtn = new Button("", new FontIcon("fth-arrow-right"));
        private final Button revertBtn  = new Button("", new FontIcon("fth-arrow-left"));
        private final Button delBtn     = new Button("", new FontIcon("fth-trash-2"));
        private final HBox actionBox    = new HBox(4, advanceBtn, revertBtn, delBtn);
        private final FontIcon statusIcon = new FontIcon("fth-check-square");
        private final StackPane statusBadge = new StackPane(statusIcon);

        TaskListCell() {
            advanceBtn.getStyleClass().add("icon-btn");
            revertBtn.getStyleClass().add("icon-btn");
            delBtn.getStyleClass().add("icon-btn");
            actionBox.setAlignment(Pos.CENTER);
            advanceBtn.setOnAction(e -> {
                Task t = getItem();
                if (t != null) onAdvanceStatus(t);
            });
            revertBtn.setOnAction(e -> {
                Task t = getItem();
                if (t != null) onRevertStatus(t);
            });
            delBtn.setOnAction(e -> {
                Task t = getItem();
                if (t != null) onDeleteTask(t);
            });
            tile.setSecondaryGraphic(actionBox);

            statusIcon.setIconSize(20);
            statusBadge.setStyle(
                    "-fx-background-color:#e8f5e9;-fx-background-radius:10;"
                    + "-fx-min-width:42;-fx-min-height:42;-fx-pref-width:42;-fx-pref-height:42;");
            tile.setPrimaryGraphic(statusBadge);
        }

        @Override
        public void updateItem(Task t, boolean empty) {
            super.updateItem(t, empty);
            if (empty || t == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            String desc = t.getDescription();
            List<Integer> ids = t.getWorkerIds();
            String assignee = (ids == null || ids.isEmpty())
                    ? "Unassigned"
                    : ids.stream()
                            .map(id -> {
                                Worker w = workerCache.get(id);
                                return w != null ? w.getFullName() : "Worker #" + id;
                            })
                            .collect(Collectors.joining(", "));
            String due = t.getDueDate() != null ? t.getDueDate().toString() : "--";
            String priority;
            if (t.isOverdue()) priority = "Overdue";
            else if (t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now().plusDays(2))) priority = "High";
            else if (t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now().plusDays(5))) priority = "Medium";
            else priority = "Low";
            String s = t.getStatus().name();
            String statusLabel = s.equals("IN_PROGRESS") ? "In Progress"
                    : s.equals("DONE") ? "Done" : "Pending";

            // Status colour: same palette as the summary cards in tasks.fxml.
            String bg = switch (t.getStatus()) {
                case DONE        -> "#d1fae5";
                case IN_PROGRESS -> "#dbeafe";
                case PENDING     -> "#fef3c7";
            };
            String fg = switch (t.getStatus()) {
                case DONE        -> "#065f46";
                case IN_PROGRESS -> "#1d4ed8";
                case PENDING     -> "#d97706";
            };
            statusBadge.setStyle(
                    "-fx-background-color:" + bg + ";-fx-background-radius:10;"
                    + "-fx-min-width:42;-fx-min-height:42;-fx-pref-width:42;-fx-pref-height:42;");
            statusIcon.setIconColor(javafx.scene.paint.Color.web(fg));

            tile.setTextLine(0, desc);
            tile.setTextLine(1, assignee + "  ·  Plot " + t.getPlotId());
            tile.setTextLine(2, "Due " + due + "  ·  " + priority + "  ·  " + statusLabel);

            advanceBtn.setDisable(t.getStatus() == Task.Status.DONE);
            revertBtn.setDisable(t.getStatus() == Task.Status.PENDING);

            setText(null);
            setGraphic(tile);
        }
    }

    private void loadTasks() {
        try {
            allTasks.setAll(taskDAO.getAll());
        } catch (SQLException e) {
            allTasks.clear();
        }
        applyFilters();
    }

    private void setupFilters() {
        cmbStatus.getItems().addAll("All", "Pending", "In Progress", "Done", "Overdue");
        cmbStatus.setValue("All");
        cmbStatus.setOnAction(e -> applyFilters());
        txtSearch.textProperty().addListener((obs, old, val) -> applyFilters());
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String status = cmbStatus.getValue();
        List<Task> filtered = allTasks.stream()
                .filter(t -> search.isEmpty() || t.getDescription().toLowerCase().contains(search))
                .filter(t -> {
                    if ("All".equals(status)) return true;
                    if ("Overdue".equals(status)) return t.isOverdue();
                    if ("In Progress".equals(status)) return t.getStatus() == Task.Status.IN_PROGRESS;
                    if ("Done".equals(status)) return t.getStatus() == Task.Status.DONE;
                    if ("Pending".equals(status)) return t.getStatus() == Task.Status.PENDING;
                    return true;
                })
                .collect(Collectors.toList());
        taskList.setItems(FXCollections.observableArrayList(filtered));
    }

    private void updateSummaryCards() {
        int total = allTasks.size();
        long pending = allTasks.stream().filter(t -> t.getStatus() == Task.Status.PENDING).count();
        long inProgress = allTasks.stream().filter(t -> t.getStatus() == Task.Status.IN_PROGRESS).count();
        long completed = allTasks.stream().filter(t -> t.getStatus() == Task.Status.DONE).count();
        lblTotalTasks.setText(String.valueOf(total));
        lblPending.setText(String.valueOf(pending));
        lblInProgress.setText(String.valueOf(inProgress));
        lblCompleted.setText(String.valueOf(completed));
    }

    @FXML
    private void onAddTask() {
        Dialog<Task> dialog = createTaskDialog(null);
        dialog.showAndWait().ifPresent(task -> {
            try {
                taskDAO.save(task);
                loadTasks();
                updateSummaryCards();
            } catch (SQLException e) {
                showAlert("Error", "Failed to save: " + e.getMessage());
            }
        });
    }

    private void onAdvanceStatus(Task task) {
        if (task == null) return;
        task.advanceStatus();
        try {
            taskDAO.update(task);
            loadTasks();
            updateSummaryCards();
        } catch (SQLException e) {
            showAlert("Error", "Failed to update: " + e.getMessage());
        }
    }

    private void onRevertStatus(Task task) {
        if (task == null) return;
        task.revertStatus();
        try {
            taskDAO.update(task);
            loadTasks();
            updateSummaryCards();
        } catch (SQLException e) {
            showAlert("Error", "Failed to update: " + e.getMessage());
        }
    }

    private void onDeleteTask(Task task) {
        if (task == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete task \"" + task.getDescription() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    taskDAO.delete(task.getTaskId());
                    loadTasks();
                    updateSummaryCards();
                } catch (SQLException e) {
                    showAlert("Error", "Failed to delete: " + e.getMessage());
                }
            }
        });
    }

    private Dialog<Task> createTaskDialog(Task existing) {
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Task" : "Edit Task");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField descField = new TextField(existing != null ? existing.getDescription() : "");
        descField.setPromptText("Task Description");

        DatePicker duePicker = new DatePicker(existing != null ? existing.getDueDate() : LocalDate.now().plusDays(1));

        TextField plotField = new TextField(existing != null ? String.valueOf(existing.getPlotId()) : "");
        plotField.setPromptText("Plot ID");

        ComboBox<String> workerCombo = new ComboBox<>();
        workerCombo.setPromptText("Assign Worker");
        workerCombo.setMaxWidth(Double.MAX_VALUE);
        Map<String, Integer> workerNameToId = new HashMap<>();
        for (Worker w : workerCache.values()) {
            String label = w.getFullName() + " (" + (w.getJobTitle() != null ? w.getJobTitle() : "Worker") + ")";
            workerCombo.getItems().add(label);
            workerNameToId.put(label, w.getWorkerId());
        }
        if (existing != null && existing.getWorkerIds() != null && !existing.getWorkerIds().isEmpty()) {
            int wid = existing.getWorkerIds().get(0);
            Worker w = workerCache.get(wid);
            if (w != null) {
                String label = w.getFullName() + " (" + (w.getJobTitle() != null ? w.getJobTitle() : "Worker") + ")";
                workerCombo.setValue(label);
            }
        }

        VBox form = new VBox(10,
                new Label("Description:"), descField,
                new Label("Due Date:"), duePicker,
                new Label("Plot ID:"), plotField,
                new Label("Assign To:"), workerCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String desc = descField.getText().trim();
                if (desc.isEmpty()) {
                    showAlert("Validation", "Description is required");
                    return null;
                }
                int plotId;
                try {
                    plotId = Integer.parseInt(plotField.getText().trim());
                } catch (NumberFormatException e) {
                    showAlert("Validation", "Invalid Plot ID");
                    return null;
                }
                Task task = new Task(desc, duePicker.getValue(), plotId, null, 1, null);
                String selectedWorker = workerCombo.getValue();
                if (selectedWorker != null && workerNameToId.containsKey(selectedWorker)) {
                    task.addWorker(workerNameToId.get(selectedWorker));
                }
                if (existing != null) {
                    task.setTaskId(existing.getTaskId());
                    task.setStatus(existing.getStatus());
                }
                return task;
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