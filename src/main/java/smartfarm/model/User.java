package smartfarm.model;

/**
 * Lightweight authenticated-principal used by the UI session.
 * Not persisted: the backing tables are admin / manager / worker.
 * Workers have no login credentials in the new schema, so role is ADMIN or MANAGER.
 */
public class User {
    public enum Role { ADMIN, MANAGER, WORKER }

    private int id;            // admin_id or manager_id (or worker_id)
    private String username;
    private String email;
    private String fullName;
    private Role role;

    public User(int id, String username, String email, String fullName, Role role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
