package smartfarm.service;

import smartfarm.dao.AdminDAO;
import smartfarm.dao.ManagerDAO;
import smartfarm.model.Admin;
import smartfarm.model.Manager;
import smartfarm.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;

/**
 * Authentication for ADMIN and MANAGER roles.
 * Workers don't sign in (no credentials in worker table).
 * Login uses email; both tables have unique email constraints.
 */
public class AuthService {
    private final AdminDAO adminDAO;
    private final ManagerDAO managerDAO;

    public AuthService(AdminDAO adminDAO, ManagerDAO managerDAO) {
        this.adminDAO = adminDAO;
        this.managerDAO = managerDAO;
    }

    public AuthService() {
        this(new AdminDAO(), new ManagerDAO());
    }

    public User signIn(String email, String password) {
        try {
            Admin admin = adminDAO.getByEmail(email);
            if (admin != null) {
                if (!admin.isActive()) throw new RuntimeException("Account is disabled");
                if (!verifyPassword(password, admin.getPasswordHash()))
                    throw new RuntimeException("The Password Is Incorrect, Try Again");
                return new User(admin.getAdminId(), admin.getUsername(), admin.getEmail(),
                                admin.getFullName(), User.Role.ADMIN);
            }

            Manager manager = managerDAO.getByEmail(email);
            if (manager != null) {
                if (!manager.isActive()) throw new RuntimeException("Account is disabled");
                if (!verifyPassword(password, manager.getPasswordHash()))
                    throw new RuntimeException("The Password Is Incorrect, Try Again");
                return new User(manager.getManagerId(), manager.getUsername(), manager.getEmail(),
                                manager.getFullName(), User.Role.MANAGER);
            }
        } catch (SQLException err) {
            throw new RuntimeException("Server Error Try Again Later");
        }
        throw new RuntimeException("The User Doesn't Exist, Try To Sign Up");
    }

    public User signUp(String fullName, String username, String email, String password,
                       String phone, User.Role role) {
        try {
            if (role == User.Role.ADMIN) {
                if (adminDAO.getByEmail(email) != null || adminDAO.getByUsername(username) != null)
                    throw new RuntimeException("The User Already Exists, Try To Sign In");
                Admin admin = new Admin(fullName, username, email, hashPassword(password), phone);
                adminDAO.save(admin);
                return new User(admin.getAdminId(), admin.getUsername(), admin.getEmail(),
                                admin.getFullName(), User.Role.ADMIN);
            } else if (role == User.Role.MANAGER) {
                if (managerDAO.getByEmail(email) != null || managerDAO.getByUsername(username) != null)
                    throw new RuntimeException("The User Already Exists, Try To Sign In");
                Manager manager = new Manager(fullName, username, email, hashPassword(password), phone);
                managerDAO.save(manager);
                return new User(manager.getManagerId(), manager.getUsername(), manager.getEmail(),
                                manager.getFullName(), User.Role.MANAGER);
            } else {
                throw new RuntimeException("Workers don't have login accounts in this system");
            }
        } catch (SQLException err) {
            throw new RuntimeException("Server Error Try Again Later");
        }
    }

    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    public boolean verifyPassword(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }
}
