package smartfarm.model;

public class Worker extends User {
    public enum Status { ACTIVE, ON_LEAVE, INACTIVE }

    private String phone;
    private String jobTitle;
    private Status status;

    // Full constructor (loading from DB)
    public Worker(int userId, String email, String passwordHash, String fullName,
                  String phone, String jobTitle, Status status) {
        super(userId, email, passwordHash, fullName, Role.WORKER);
        this.phone    = phone;
        this.jobTitle = jobTitle;
        this.status   = status;
    }

    // Without userId (creating new)
    public Worker(String email, String passwordHash, String fullName,
                  String phone, String jobTitle) {
        super(email, passwordHash, fullName, Role.WORKER);
        this.phone    = phone;
        this.jobTitle = jobTitle;
        this.status   = Status.ACTIVE;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Computes the active task count from a given list of tasks.
     * A task is "active" if it is PENDING or IN_PROGRESS and this worker is assigned.
     */
    public int getActiveTaskCount(java.util.List<Task> allTasks) {
        return (int) allTasks.stream()
                .filter(t -> t.getStatus() != Task.Status.DONE)
                .filter(t -> t.getWorkerIds().contains(this.getUserId()))
                .count();
    }

    public boolean isAvailable(java.util.List<Task> allTasks) {
        return getActiveTaskCount(allTasks) == 0;
    }
}
