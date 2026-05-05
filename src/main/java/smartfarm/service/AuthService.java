package smartfarm.service;
import smartfarm.dao.UserDAO;
import smartfarm.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;

public class AuthService {
    private final UserDAO authProcess;

    public AuthService(UserDAO authProcess) {
        this.authProcess = authProcess;
    }

    public User signIn(String email, String password) {
        User fetch;
        try{
            fetch = authProcess.getByEmail(email);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error Try Again Later");
        }
        if (fetch == null) {
        throw new RuntimeException("The User Doesn't Exist, Try To Sign Up");
    }
        if (!verifyPassword(password, fetch.getPasswordHash())) {
        throw new RuntimeException("The Password Is Incorrect, Try Again");
    }
    return fetch;
}
    public void signUp(String email, String password, String fullName, User.Role role){
        User fecth;
        try{
            fecth = authProcess.getByEmail(email);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error Try Again Later");
        }
        boolean emailExists = fecth!=null;
        if(emailExists){
            throw new RuntimeException("The User Already Exists, Try To Sign In");
        }
        String hashedPassword = hashPassword(password);
        User user = new User(email, hashedPassword, fullName, role);
        try{
            authProcess.save(user);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error Try Again Later");
        }
    }
    public static String hashPassword(String password){
        int logRounds = 10;
        String salt = BCrypt.gensalt(logRounds);
        return BCrypt.hashpw(password, salt);
    }
    public boolean verifyPassword(String password, String hash){
        return BCrypt.checkpw(password, hash);
    }
}
