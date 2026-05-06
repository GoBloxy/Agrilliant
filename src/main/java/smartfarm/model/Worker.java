package smartfarm.model;

import java.util.List;

public class Worker extends User {
    private String phone;
    private int activeTaskCount;
    private List<Integer> taskId;

    // with userID && Without tasksID
    public Worker(int userId, String email, String passwordHash, String fullName, String phone, int activeTaskCount) {
        super(userId, email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
        this.activeTaskCount = activeTaskCount;
        this.taskId = null;
    }

    // without userID && without tasksID
    public Worker(String email, String passwordHash, String fullName, String phone) {
        super(email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
        this.activeTaskCount = 0;
        this.taskId = null;
    }

    // with userID && With tasksID
    public Worker(int userId, String email, String passwordHash, String fullName, String phone, int activeTaskCount, List<Integer> taskId) {
        super(userId, email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
        this.activeTaskCount = activeTaskCount;
        this.taskId = taskId;
    }

    // without userID && with tasksID
    public Worker(String email, String passwordHash, String fullName, String phone, List<Integer> taskId) {
        super(email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
        this.activeTaskCount = 0;
        this.taskId = taskId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<Integer> getTaskId() {
        return taskId;
    }

    public void setTaskId(List<Integer> taskId) {
        this.taskId = taskId;
    }

    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    public void setActiveTaskCount(int activeTaskCount) {
        this.activeTaskCount = activeTaskCount;
    }

    public boolean isAvailable() {
        return activeTaskCount == 0;
    }
}
