package smartfarm.model;

public class Worker {
    private int workerId;
    private String name;
    private String role;
    private String phone;
    private int activeTaskCount;

    // TODO: Constructor, isAvailable(), getters, setters

    public Worker(int workerId, String name, String role, String phone, int activeTaskCount) {
        this.workerId = workerId;
        this.name = name;
        this.role = role;
        this.phone = phone;
        this.activeTaskCount = activeTaskCount;
    }

    public int getWorkerId() {
        return workerId;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public boolean isAvailable(){
        return getActiveTaskCount() == 0;
    }
}
