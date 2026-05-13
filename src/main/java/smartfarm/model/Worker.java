package smartfarm.model;

import java.time.LocalDateTime;
import java.util.List;

public class Worker {
    private int workerId;
    private String fullName;
    private String phone;
    private String email;
    private String passwordHash;
    private String jobTitle;
    private String skills;
    private boolean onDuty;
    private Integer fingerprintId;
    private int managerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Full constructor (loading from DB)
    public Worker(int workerId, String fullName, String phone, String email, String passwordHash,
                  String jobTitle, String skills, boolean onDuty, Integer fingerprintId,
                  int managerId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.workerId      = workerId;
        this.fullName      = fullName;
        this.phone         = phone;
        this.email         = email;
        this.passwordHash  = passwordHash;
        this.jobTitle      = jobTitle;
        this.skills        = skills;
        this.onDuty        = onDuty;
        this.fingerprintId = fingerprintId;
        this.managerId     = managerId;
        this.createdAt     = createdAt;
        this.updatedAt     = updatedAt;
    }

    // Without workerId (creating new)
    public Worker(String fullName, String phone, String email, String passwordHash,
                  String jobTitle, String skills, int managerId) {
        this.workerId      = -1;
        this.fullName      = fullName;
        this.phone         = phone;
        this.email         = email;
        this.passwordHash  = passwordHash;
        this.jobTitle      = jobTitle;
        this.skills        = skills;
        this.onDuty        = false;
        this.fingerprintId = null;
        this.managerId     = managerId;
        this.createdAt     = LocalDateTime.now();
        this.updatedAt     = LocalDateTime.now();
    }

    public int getWorkerId() { return workerId; }
    public void setWorkerId(int workerId) { this.workerId = workerId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public boolean isOnDuty() { return onDuty; }
    public void setOnDuty(boolean onDuty) { this.onDuty = onDuty; }
    public Integer getFingerprintId() { return fingerprintId; }
    public void setFingerprintId(Integer fingerprintId) { this.fingerprintId = fingerprintId; }
    public int getManagerId() { return managerId; }
    public void setManagerId(int managerId) { this.managerId = managerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Computes the active task count from a given list of tasks.
     * A task is "active" if it is not DONE and this worker is assigned.
     */
    public int getActiveTaskCount(List<Task> allTasks) {
        return (int) allTasks.stream()
                .filter(t -> t.getStatus() != Task.Status.DONE)
                .filter(t -> t.getWorkerIds().contains(this.workerId))
                .count();
    }

    public boolean isAvailable(List<Task> allTasks) {
        return getActiveTaskCount(allTasks) == 0;
    }
}
