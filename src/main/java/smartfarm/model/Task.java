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

    // TODO: Constructor, advanceStatus(), isOverdue(), getters, setters
}
