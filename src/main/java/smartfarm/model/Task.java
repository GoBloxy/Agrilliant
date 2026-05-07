package smartfarm.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Task {
    public enum Status { PENDING, IN_PROGRESS, DONE }

    private int taskId;
    private String description;
    private Status status;
    private LocalDate dueDate;
    private int plotId;
    private Integer alertId;          // nullable
    private int assignedByMgrId;
    private String alertType;         // denormalized
    private List<Integer> workerIds;  // populated via worker_task junction

    // Full constructor (loading from DB)
    public Task(int taskId, String description, Status status, LocalDate dueDate, int plotId,
                Integer alertId, int assignedByMgrId, String alertType, List<Integer> workerIds) {
        this.taskId = taskId;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.plotId = plotId;
        this.alertId = alertId;
        this.assignedByMgrId = assignedByMgrId;
        this.alertType = alertType;
        this.workerIds = workerIds != null ? workerIds : new ArrayList<>();
    }

    // Without taskId (creating a new task)
    public Task(String description, LocalDate dueDate, int plotId, Integer alertId,
                int assignedByMgrId, String alertType) {
        this.taskId = -1;
        this.description = description;
        this.status = Status.PENDING;
        this.dueDate = dueDate;
        this.plotId = plotId;
        this.alertId = alertId;
        this.assignedByMgrId = assignedByMgrId;
        this.alertType = alertType;
        this.workerIds = new ArrayList<>();
    }

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public int getPlotId() { return plotId; }
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public Integer getAlertId() { return alertId; }
    public void setAlertId(Integer alertId) { this.alertId = alertId; }
    public int getAssignedByMgrId() { return assignedByMgrId; }
    public void setAssignedByMgrId(int assignedByMgrId) { this.assignedByMgrId = assignedByMgrId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public List<Integer> getWorkerIds() { return workerIds; }
    public void setWorkerIds(List<Integer> workerIds) { this.workerIds = workerIds; }

    public void addWorker(int workerId) {
        if (!workerIds.contains(workerId)) workerIds.add(workerId);
    }

    public void removeWorker(int workerId) {
        workerIds.remove(Integer.valueOf(workerId));
    }

    public boolean isOverdue() {
        return status != Status.DONE && dueDate != null && LocalDate.now().isAfter(dueDate);
    }

    public void advanceStatus() {
        switch (status) {
            case PENDING -> status = Status.IN_PROGRESS;
            case IN_PROGRESS -> status = Status.DONE;
            case DONE -> { /* terminal */ }
        }
    }

    public void revertStatus() {
        switch (status) {
            case DONE -> status = Status.IN_PROGRESS;
            case IN_PROGRESS -> status = Status.PENDING;
            case PENDING -> { /* already at start */ }
        }
    }
}
