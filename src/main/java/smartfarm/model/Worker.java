package smartfarm.model;

public class Worker extends User {
    private String phone;

    public Worker(int userId, String email, String passwordHash, String fullName, String phone) {
        super(userId, email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
    }

    public Worker(String email, String passwordHash, String fullName, String phone) {
        super(email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
