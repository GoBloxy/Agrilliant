package smartfarm.model;

import java.time.LocalDate;

public class Task {
    public enum Status { PENDING, IN_PROGRESS, DONE }

    private int taskId;
    private String description;
    private Status status;
    private LocalDate dueDate;
    private int workerId;
    private int plotId;
    private String alertType;

    // Full constructor (loading from DB)
    public Task(int taskId, String description, Status status, LocalDate dueDate, int workerId, int plotId, String alertType) {
        this.taskId = taskId;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.workerId = workerId;
        this.plotId = plotId;
        this.alertType = alertType;
    }

    // Without taskId (creating a new task)
    public Task(String description, LocalDate dueDate, int workerId, int plotId, String alertType) {
        this.taskId = -1;
        this.description = description;
        this.status = Status.PENDING;
        this.dueDate = dueDate;
        this.workerId = workerId;
        this.plotId = plotId;
        this.alertType = alertType;
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

    public int getWorkerId() {
        return workerId;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public int getPlotId() {
        return plotId;
    }

    public void setPlotId(int plotId) {
        this.plotId = plotId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
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
