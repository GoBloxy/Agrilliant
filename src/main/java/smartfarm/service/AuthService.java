package smartfarm.service;
import smartfarm.dao.UserDAO;
import smartfarm.model.User;
import org.mindrot.jbcrypt.BCrypt;

public class AuthService {
    private final UserDAO authProcess;

    public AuthService(UserDAO authProcess) {
        this.authProcess = authProcess;
    }

    public User signIn(String email, String password) {
    User user = authProcess.findByEmail(email);
        if (user == null) {
        throw new RuntimeException("The User Doesn't Exist, Try To Sign Up");
    }
        if (!verifyPassword(password, user.getPasswordHash())) {
        throw new RuntimeException("The Password Is Incorrect, Try Again");
    }
    return user;
}
    public void signUp(String email, String password, String fullName, String role){
        String hashedPassword = hashPassword(password);
        if(authProcess.emailExists(email)){
            throw new RuntimeException("The User Already Exists, Try To Sign In");
        }
        User user = new User(email, hashedPassword, fullName, role);
        authProcess.register(user);
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
