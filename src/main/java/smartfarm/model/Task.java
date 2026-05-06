package smartfarm.model;

import java.time.LocalDate;
import java.util.List;

public class Task {
    public enum Status { PENDING, IN_PROGRESS, DONE }

    private int taskId;
    private String description;
    private Status status;
    private LocalDate dueDate;
    private List<Integer> workerId; // change this to be array maybe multiple users can join the same task
    private int plotId;
    private String alertType;


    // with description and with id
    public Task(int taskId, String description, Status status, LocalDate dueDate, List<Integer> workerId, int plotId, String alertType) {
        this.taskId = taskId;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.workerId = workerId;
        this.plotId = plotId;
        this.alertType = alertType;
    }

    // with description and without id
    public Task(String description, Status status, LocalDate dueDate, List<Integer> workerId, int plotId, String alertType) {
        this.taskId = -1;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.workerId = workerId;
        this.plotId = plotId;
        this.alertType = alertType;
    }

    // without description and with id
    public Task(int taskId, Status status, LocalDate dueDate, List<Integer> workerId, int plotId, String alertType) {
        this.taskId = taskId;
        this.description = null;
        this.status = status;
        this.dueDate = dueDate;
        this.workerId = workerId;
        this.plotId = plotId;
        this.alertType = alertType;
    }

    // without description and without id
    public Task(Status status, LocalDate dueDate, List<Integer> workerId, int plotId, String alertType) {
        this.taskId = -1;
        this.description = null;
        this.status = status;
        this.dueDate = dueDate;
        this.workerId = workerId;
        this.plotId = plotId;
        this.alertType = alertType;
    }

    // without description and without id and without workerId
    public Task(Status status, LocalDate dueDate, int plotId, String alertType) {
        this.taskId = -1;
        this.description = null;
        this.status = status;
        this.dueDate = dueDate;
        this.workerId = null;
        this.plotId = plotId;
        this.alertType = alertType;
    }

    // with description and without id and without workerId
    public Task(String description, Status status, LocalDate dueDate, int plotId, String alertType) {
        this.taskId = -1;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.workerId = null;
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

    public List<Integer> getWorkerId() {
        return workerId;
    }

    public void setWorkerId(List<Integer> workerId) {
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
        LocalDate today = LocalDate.now();
        return today.isAfter(getDueDate());
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
