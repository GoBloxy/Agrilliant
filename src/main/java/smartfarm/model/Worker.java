package smartfarm.model;

public class Worker extends User {
    private String phone;
    private int activeTaskCount;

    // Full constructor (loading from DB)
    public Worker(int userId, String email, String passwordHash, String fullName, String phone, int activeTaskCount) {
        super(userId, email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
        this.activeTaskCount = activeTaskCount;
    }

    // Without userId (registering a new worker)
    public Worker(String email, String passwordHash, String fullName, String phone) {
        super(email, passwordHash, fullName, Role.WORKER);
        this.phone = phone;
        this.activeTaskCount = 0;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
