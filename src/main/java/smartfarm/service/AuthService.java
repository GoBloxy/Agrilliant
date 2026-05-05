package smartfarm.service;
import smartfarm.dao.UserDAO;
import smartfarm.model.User;
import org.mindrot.jbcrypt.BCrypt;

public class AuthService {
    private final UserDAO authProcess;

    public AuthService(UserDAO authProcess) {
        this.authProcess = authProcess;
    }

    public void signIn(String email, String password) {
        if(!authProcess.emailExists(email)){
            throw new Error("The User Doesnt Exist, Try To Sign Up");
        }
        if(!verifyPassword(password, authProcess.getHash(email))) {
            throw new Error("The Password Is Incorrect, Try Again");
        }
    }
    public void signUp(String email, String password, String fullName, String role){
        String hashedPassword = hashPassword(password);
        if(authProcess.emailExists(email)){
            throw new Error("The User Already Exists, Try To Sign In");
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
