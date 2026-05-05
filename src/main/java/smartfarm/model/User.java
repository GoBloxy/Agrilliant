package smartfarm.model;

public class User {
    public enum Role { ADMIN, FARMER, WORKER }

    private int userId;
    private String email;
    private String passwordHash;
    private String fullName;
    private Role role;



    public User(int userId, String email, String passwordHash, String fullName, Role role) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }
    // without userId not generated && auto incremented in the database
    public User(String email, String passwordHash, String fullName, Role role) {
        this.userId = -1;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
