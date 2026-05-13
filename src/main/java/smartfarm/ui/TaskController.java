package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.PlotDAO;
import smartfarm.dao.TaskDAO;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Plot;
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
    @FXML private TableView<Task> taskTable;
    @FXML private TableColumn<Task, String> colTask, colAssignedTo, colPlot, colDueDate, colAlertType, colStatus, colActions;
    @FXML private Button btnAddTask;

    private final TaskDAO taskDAO = new TaskDAO();
    private final WorkerDAO workerDAO = new WorkerDAO();
    private final PlotDAO plotDAO = new PlotDAO();
    private final ObservableList<Task> allTasks = FXCollections.observableArrayList();
    private final Map<Integer, Worker> workerCache = new HashMap<>();
    private final Map<String, Integer> plotNameToId = new HashMap<>();
    private final Map<Integer, String> plotIdToName = new HashMap<>();
    private final List<String> plotLabels = new java.util.ArrayList<>();

    private static int currentManagerId = 1;
    public static void setCurrentManagerId(int id) { currentManagerId = id; }

    @FXML
    public void initialize() {
        loadWorkerCache();
        loadPlotCache();
        setupTableColumns();
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

    private void loadPlotCache() {
        plotNameToId.clear();
        plotIdToName.clear();
        plotLabels.clear();
        try {
            for (Plot p : plotDAO.getAll()) {
                String label = p.getName() + " (ID: " + p.getPlotId() + ")";
                plotNameToId.put(label, p.getPlotId());
                plotIdToName.put(p.getPlotId(), p.getName());
                plotLabels.add(label);
            }
        } catch (SQLException e) {
            // cache will be empty
        }
    }

    private void setupTableColumns() {
        taskTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colTask.setResizable(false);
        colAssignedTo.setResizable(false);
        colPlot.setResizable(false);
        colDueDate.setResizable(false);
        colAlertType.setResizable(false);
        colStatus.setResizable(false);
        colActions.setResizable(false);

        colTask.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));
        colAssignedTo.setCellValueFactory(data -> {
            List<Integer> ids = data.getValue().getWorkerIds();
            if (ids == null || ids.isEmpty()) return new javafx.beans.property.SimpleStringProperty("Unassigned");
            String names = ids.stream()
                    .map(id -> {
                        Worker w = workerCache.get(id);
                        return w != null ? w.getFullName() : "Worker #" + id;
                    })
                    .collect(Collectors.joining(", "));
            return new javafx.beans.property.SimpleStringProperty(names);
        });
        colPlot.setCellValueFactory(data -> {
            String name = plotIdToName.get(data.getValue().getPlotId());
            return new javafx.beans.property.SimpleStringProperty(name != null ? name : "Plot " + data.getValue().getPlotId());
        });
        colDueDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getDueDate() != null ? data.getValue().getDueDate().toString() : "--"));
        colAlertType.setCellValueFactory(data -> {
            String at = data.getValue().getAlertType();
            if (at == null || at.isEmpty()) return new javafx.beans.property.SimpleStringProperty("Manual");
            String[] parts = at.toLowerCase().split("_");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
            return new javafx.beans.property.SimpleStringProperty(sb.toString().trim());
        });
        colStatus.setCellValueFactory(data -> {
            String s = data.getValue().getStatus().name();
            return new javafx.beans.property.SimpleStringProperty(
                    s.equals("IN_PROGRESS") ? "In Progress" : s.equals("DONE") ? "Done" : "Pending");
        });

        colActions.setCellFactory(col -> new TableCell<Task, String>() {
            private final Button advanceBtn = new Button("", new FontIcon("fth-arrow-right"));
            private final Button revertBtn = new Button("", new FontIcon("fth-arrow-left"));
            private final Button editBtn = new Button("", new FontIcon("fth-edit"));
            private final Button delBtn = new Button("", new FontIcon("fth-trash-2"));
            private final HBox box = new HBox(4, advanceBtn, revertBtn, editBtn, delBtn);
            {
                advanceBtn.getStyleClass().add("icon-btn");
                revertBtn.getStyleClass().add("icon-btn");
                editBtn.getStyleClass().add("icon-btn");
                delBtn.getStyleClass().add("icon-btn");
                advanceBtn.setOnAction(e -> {
                    Task t = getTableRow() != null ? getTableRow().getItem() : null;
                    if (t != null) onAdvanceStatus(t);
                });
                revertBtn.setOnAction(e -> {
                    Task t = getTableRow() != null ? getTableRow().getItem() : null;
                    if (t != null) onRevertStatus(t);
                });
                editBtn.setOnAction(e -> {
                    Task t = getTableRow() != null ? getTableRow().getItem() : null;
                    if (t != null) onEditTask(t);
                });
                delBtn.setOnAction(e -> {
                    Task t = getTableRow() != null ? getTableRow().getItem() : null;
                    if (t != null) onDeleteTask(t);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Task t = getTableRow().getItem();
                    if (t != null) {
                        advanceBtn.setDisable(t.getStatus() == Task.Status.DONE);
                        revertBtn.setDisable(t.getStatus() == Task.Status.PENDING);
                    }
                    setGraphic(box);
                }
            }
        });
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
                .filter(t -> {
                    if (search.isEmpty()) return true;
                    if (t.getDescription().toLowerCase().contains(search)) return true;
                    String plotName = plotIdToName.getOrDefault(t.getPlotId(), "");
                    if (plotName.toLowerCase().contains(search)) return true;
                    String alertType = t.getAlertType() != null ? t.getAlertType().toLowerCase().replace("_", " ") : "";
                    if (alertType.contains(search)) return true;
                    if (t.getWorkerIds() != null) {
                        for (int wid : t.getWorkerIds()) {
                            Worker w = workerCache.get(wid);
                            if (w != null && w.getFullName().toLowerCase().contains(search)) return true;
                        }
                    }
                    return false;
                })
                .filter(t -> {
                    if ("All".equals(status)) return true;
                    if ("Overdue".equals(status)) return t.isOverdue();
                    if ("In Progress".equals(status)) return t.getStatus() == Task.Status.IN_PROGRESS;
                    if ("Done".equals(status)) return t.getStatus() == Task.Status.DONE;
                    if ("Pending".equals(status)) return t.getStatus() == Task.Status.PENDING;
                    return true;
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty() && !search.isEmpty()) {
            taskTable.setPlaceholder(new Label("No tasks matching \"" + search + "\""));
        } else if (filtered.isEmpty()) {
            taskTable.setPlaceholder(new Label("No tasks found"));
        }
        taskTable.setItems(FXCollections.observableArrayList(filtered));
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

    private void onEditTask(Task task) {
        if (task == null) return;
        Dialog<Task> dialog = createTaskDialog(task);
        dialog.showAndWait().ifPresent(updated -> {
            try {
                taskDAO.update(updated);
                loadTasks();
                updateSummaryCards();
            } catch (SQLException e) {
                showAlert("Error", "Failed to update: " + e.getMessage());
            }
        });
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

        ComboBox<String> plotCombo = new ComboBox<>();
        plotCombo.setPromptText("Select Plot");
        plotCombo.setMaxWidth(Double.MAX_VALUE);
        plotCombo.getItems().addAll(plotLabels);
        if (existing != null) {
            for (Map.Entry<String, Integer> entry : plotNameToId.entrySet()) {
                if (entry.getValue() == existing.getPlotId()) {
                    plotCombo.setValue(entry.getKey());
                    break;
                }
            }
        }

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
                new Label("Plot:"), plotCombo,
                new Label("Assign To:"), workerCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        Button saveBtnNode = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveBtnNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (descField.getText().trim().isEmpty()) {
                showAlert("Validation", "Description is required"); event.consume(); return;
            }
            if (plotCombo.getValue() == null) {
                showAlert("Validation", "Please select a plot"); event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String desc = descField.getText().trim();
                int plotId = plotNameToId.get(plotCombo.getValue());
                Task task = new Task(desc, duePicker.getValue(), plotId, null, currentManagerId, null);
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