package smartfarm.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Task {
    public enum Status { PENDING, IN_PROGRESS, DONE }
    public enum Priority { HIGH, MEDIUM, LOW }

    private int taskId;
    private String taskName;
    private String description;
    private Status status;
    private Priority priority;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private List<Integer> workerIds;
    private int plotId;
    private Integer alertId;

    // Full constructor (loading from DB)
    public Task(int taskId, String taskName, String description, Status status, Priority priority,
                LocalDate dueDate, LocalDateTime createdAt, List<Integer> workerIds, int plotId, Integer alertId) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.workerIds = workerIds != null ? workerIds : new ArrayList<>();
        this.plotId = plotId;
        this.alertId = alertId;
    }

    // Without taskId (creating a new task)
    public Task(String taskName, String description, Priority priority, LocalDate dueDate, int plotId, Integer alertId) {
        this.taskId = -1;
        this.taskName = taskName;
        this.description = description;
        this.status = Status.PENDING;
        this.priority = priority;
        this.dueDate = dueDate;
        this.createdAt = LocalDateTime.now();
        this.workerIds = new ArrayList<>();
        this.plotId = plotId;
        this.alertId = alertId;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Integer> getWorkerIds() {
        return workerIds;
    }

    public void setWorkerIds(List<Integer> workerIds) {
        this.workerIds = workerIds;
    }

    public void addWorker(int workerId) {
        if (!workerIds.contains(workerId)) {
            workerIds.add(workerId);
        }
    }

    public void removeWorker(int workerId) {
        workerIds.remove(Integer.valueOf(workerId));
    }

    public int getPlotId() {
        return plotId;
    }

    public void setPlotId(int plotId) {
        this.plotId = plotId;
    }

    public Integer getAlertId() {
        return alertId;
    }

    public void setAlertId(Integer alertId) {
        this.alertId = alertId;
    }

    public boolean isOverdue(){
        return status != Status.DONE && LocalDate.now().isAfter(getDueDate());
    }

    public void advanceStatus(){
        switch (getStatus()){
            case PENDING -> setStatus(Status.IN_PROGRESS);
            case IN_PROGRESS -> setStatus(Status.DONE);
        }
    }

    public void revertStatus(){
        switch (getStatus()){
            case DONE -> setStatus(Status.IN_PROGRESS);
            case IN_PROGRESS -> setStatus(Status.PENDING);
        }
    }
}
