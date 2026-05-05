package smartfarm.dao;

import smartfarm.model.User;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class UserDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: register(User user) — INSERT into users
    // TODO: findByUsername(String username) — SELECT for login
    // TODO: usernameExists(String username) — check duplicates
}
