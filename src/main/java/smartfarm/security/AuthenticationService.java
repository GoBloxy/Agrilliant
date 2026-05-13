package smartfarm.security;

import smartfarm.model.Admin;
import smartfarm.model.Manager;
import smartfarm.model.Worker;
import smartfarm.dao.AdminDAO;
import smartfarm.dao.ManagerDAO;
import smartfarm.dao.WorkerDAO;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Comprehensive authentication and authorization service
 * Handles login, session management, JWT tokens, and role-based access
 */
public class AuthenticationService {
    private static final Logger logger = Logger.getLogger(AuthenticationService.class.getName());
    private static AuthenticationService instance;
    
    private final AdminDAO adminDAO = new AdminDAO();
    private final ManagerDAO managerDAO = new ManagerDAO();
    private final WorkerDAO workerDAO = new WorkerDAO();
    
    // Session management
    private final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    // JWT Secret (in production, use environment variable)
    private static final String JWT_SECRET = "AGRILLIANT_JWT_SECRET_KEY_CHANGE_IN_PRODUCTION";
    private static final long SESSION_TIMEOUT_MS = 8 * 60 * 60 * 1000; // 8 hours
    
    private AuthenticationService() {}
    
    public static synchronized AuthenticationService getInstance() {
        if (instance == null) {
            instance = new AuthenticationService();
        }
        return instance;
    }
    
    /**
     * Authenticate user with email/username and password
     */
    public AuthResult authenticate(String identifier, String password) {
        try {
            // Try admin first
            Admin admin = adminDAO.getByEmail(identifier);
            if (admin != null && verifyPassword(password, admin.getPasswordHash())) {
                String token = generateJWT(admin.getEmail(), "ADMIN", admin.getAdminId());
                UserSession session = new UserSession(token, admin.getEmail(), "ADMIN", admin.getAdminId(), System.currentTimeMillis());
                activeSessions.put(token, session);
                return new AuthResult(true, token, "ADMIN", admin.getAdminId(), admin.getFullName());
            }
            
            // Try manager
            Manager manager = managerDAO.getByEmail(identifier);
            if (manager != null && verifyPassword(password, manager.getPasswordHash())) {
                String token = generateJWT(manager.getEmail(), "MANAGER", manager.getManagerId());
                UserSession session = new UserSession(token, manager.getEmail(), "MANAGER", manager.getManagerId(), System.currentTimeMillis());
                activeSessions.put(token, session);
                return new AuthResult(true, token, "MANAGER", manager.getManagerId(), manager.getFullName());
            }
            
            // Try worker
            Worker worker = workerDAO.getByPhoneOrEmail(identifier);
            if (worker != null && verifyPassword(password, worker.getPasswordHash())) {
                String token = generateJWT(worker.getEmail(), "WORKER", worker.getWorkerId());
                UserSession session = new UserSession(token, worker.getEmail(), "WORKER", worker.getWorkerId(), System.currentTimeMillis());
                activeSessions.put(token, session);
                return new AuthResult(true, token, "WORKER", worker.getWorkerId(), worker.getFullName());
            }
            
            return new AuthResult(false, null, null, -1, "Invalid credentials");
            
        } catch (Exception e) {
            logger.severe("Authentication error: " + e.getMessage());
            return new AuthResult(false, null, null, -1, "Authentication failed");
        }
    }
    
    /**
     * Validate JWT token and return user session
     */
    public UserSession validateToken(String token) {
        UserSession session = activeSessions.get(token);
        if (session == null) {
            return null;
        }
        
        // Check session timeout
        if (System.currentTimeMillis() - session.getCreatedAt() > SESSION_TIMEOUT_MS) {
            activeSessions.remove(token);
            return null;
        }
        
        return session;
    }
    
    /**
     * Logout user by invalidating token
     */
    public boolean logout(String token) {
        return activeSessions.remove(token) != null;
    }
    
    /**
     * Check if user has required permission
     */
    public boolean hasPermission(String token, Permission permission) {
        UserSession session = validateToken(token);
        if (session == null) {
            return false;
        }
        
        String role = session.getRole();
        return switch (role) {
            case "ADMIN" -> true; // Admin has all permissions
            case "MANAGER" -> permission != Permission.SYSTEM_ADMIN && permission != Permission.USER_MANAGEMENT;
            case "WORKER" -> permission == Permission.VIEW_DASHBOARD || permission == Permission.TASK_MANAGEMENT;
            default -> false;
        };
    }
    
    /**
     * Change user password
     */
    public boolean changePassword(String token, String currentPassword, String newPassword) {
        UserSession session = validateToken(token);
        if (session == null) {
            return false;
        }
        
        try {
            String email = session.getEmail();
            String role = session.getRole();
            String hashedNewPassword = hashPassword(newPassword);
            
            switch (role) {
                case "ADMIN" -> {
                    Admin admin = adminDAO.getByEmail(email);
                    if (admin != null && verifyPassword(currentPassword, admin.getPasswordHash())) {
                        admin.setPasswordHash(hashedNewPassword);
                        adminDAO.update(admin);
                        return true;
                    }
                }
                case "MANAGER" -> {
                    Manager manager = managerDAO.getByEmail(email);
                    if (manager != null && verifyPassword(currentPassword, manager.getPasswordHash())) {
                        manager.setPasswordHash(hashedNewPassword);
                        managerDAO.update(manager);
                        return true;
                    }
                }
                case "WORKER" -> {
                    Worker worker = workerDAO.getByPhoneOrEmail(email);
                    if (worker != null && verifyPassword(currentPassword, worker.getPasswordHash())) {
                        worker.setPasswordHash(hashedNewPassword);
                        workerDAO.update(worker);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Password change error: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Create new user (admin only)
     */
    public boolean createUser(String adminToken, String role, String email, String fullName, String phone, String password) {
        if (!hasPermission(adminToken, Permission.USER_MANAGEMENT)) {
            return false;
        }
        
        try {
            String hashedPassword = hashPassword(password);
            
            switch (role.toUpperCase()) {
                case "ADMIN" -> {
                    Admin admin = new Admin(email, hashedPassword, fullName, LocalDateTime.now());
                    adminDAO.save(admin);
                    return true;
                }
                case "MANAGER" -> {
                    Manager manager = new Manager(email, hashedPassword, fullName, phone, LocalDateTime.now());
                    managerDAO.save(manager);
                    return true;
                }
                case "WORKER" -> {
                    // Worker needs manager_id - for now use current admin's ID as placeholder
                    Worker worker = new Worker(fullName, phone, email, hashedPassword, "General Worker", "", 1);
                    workerDAO.save(worker);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.severe("User creation error: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get session statistics
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", activeSessions.size());
        
        long adminCount = activeSessions.values().stream().mapToLong(s -> "ADMIN".equals(s.getRole()) ? 1 : 0).sum();
        long managerCount = activeSessions.values().stream().mapToLong(s -> "MANAGER".equals(s.getRole()) ? 1 : 0).sum();
        long workerCount = activeSessions.values().stream().mapToLong(s -> "WORKER".equals(s.getRole()) ? 1 : 0).sum();
        
        stats.put("adminSessions", adminCount);
        stats.put("managerSessions", managerCount);
        stats.put("workerSessions", workerCount);
        
        return stats;
    }
    
    // Private helper methods
    
    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes());
            
            // Combine salt and hash
            byte[] combined = new byte[salt.length + hashedPassword.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hashedPassword, 0, combined, salt.length, hashedPassword.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }
    
    private boolean verifyPassword(String password, String hashedPassword) {
        try {
            byte[] combined = Base64.getDecoder().decode(hashedPassword);
            byte[] salt = new byte[16];
            byte[] hash = new byte[combined.length - 16];
            
            System.arraycopy(combined, 0, salt, 0, 16);
            System.arraycopy(combined, 16, hash, 0, hash.length);
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] computedHash = md.digest(password.getBytes());
            
            return MessageDigest.isEqual(hash, computedHash);
        } catch (Exception e) {
            logger.severe("Password verification error: " + e.getMessage());
            return false;
        }
    }
    
    private String generateJWT(String email, String role, int userId) {
        // Simple JWT-like token (in production use proper JWT library)
        String payload = String.format("{\"email\":\"%s\",\"role\":\"%s\",\"userId\":%d,\"exp\":%d,\"jti\":\"%s\"}",
            email, role, userId, System.currentTimeMillis() + SESSION_TIMEOUT_MS, UUID.randomUUID().toString());
        
        // Simple encoding (in production use proper JWT signing)
        return Base64.getEncoder().encodeToString((JWT_SECRET + "." + payload).getBytes());
    }
    
    // Data classes
    
    public static class AuthResult {
        private final boolean success;
        private final String token;
        private final String role;
        private final int userId;
        private final String message;
        private final String fullName;
        
        public AuthResult(boolean success, String token, String role, int userId, String message) {
            this.success = success;
            this.token = token;
            this.role = role;
            this.userId = userId;
            this.message = message;
            this.fullName = message; // For now, message contains full name
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getToken() { return token; }
        public String getRole() { return role; }
        public int getUserId() { return userId; }
        public String getMessage() { return message; }
        public String getFullName() { return fullName; }
    }
    
    public static class UserSession {
        private final String token;
        private final String email;
        private final String role;
        private final int userId;
        private final long createdAt;
        
        public UserSession(String token, String email, String role, int userId, long createdAt) {
            this.token = token;
            this.email = email;
            this.role = role;
            this.userId = userId;
            this.createdAt = createdAt;
        }
        
        // Getters
        public String getToken() { return token; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public int getUserId() { return userId; }
        public long getCreatedAt() { return createdAt; }
    }
    
    public enum Permission {
        VIEW_DASHBOARD,
        CROP_MANAGEMENT,
        TASK_MANAGEMENT,
        DEVICE_MANAGEMENT,
        ALERT_MANAGEMENT,
        REPORT_GENERATION,
        USER_MANAGEMENT,
        SYSTEM_ADMIN
    }
}
