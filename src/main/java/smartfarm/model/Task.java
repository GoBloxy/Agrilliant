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
    private List<Integer> workerIds;
    private int plotId;
    private Integer alertId;

    // Full constructor (loading from DB)
    public Task(int taskId, String description, Status status, LocalDate dueDate, List<Integer> workerIds, int plotId, Integer alertId) {
        this.taskId = taskId;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.workerIds = workerIds != null ? workerIds : new ArrayList<>();
        this.plotId = plotId;
        this.alertId = alertId;
    }

    // Without taskId (creating a new task)
    public Task(String description, LocalDate dueDate, int plotId, Integer alertId) {
        this.taskId = -1;
        this.description = description;
        this.status = Status.PENDING;
        this.dueDate = dueDate;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
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
