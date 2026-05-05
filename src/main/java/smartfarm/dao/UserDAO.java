package smartfarm.dao;
import smartfarm.model.User;

import smartfarm.model.User;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class UserDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: register(User user) — INSERT into users
    public void register(User user){

    }

    // TODO: findByEmail(String email) — SELECT for login
    public User findByEmail(String email){
        return new User("placehlder", "placehlder", "placehlder", User.Role.FARMER);
    }

    // TODO: emailExists(String email) — check if already avaliable
    public boolean emailExists(String email){
        return false; //placeholder
    }

    // TODO: getHash(String email) — returns the hash to validate it
    public String getHash(String email){
        return "hash"; //placeholder
    }
}
