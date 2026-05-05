package smartfarm.dao;

import smartfarm.model.User;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class UserDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: register(User user) — INSERT into users
    // TODO: findByEmail(String email) — SELECT for login
    // TODO: emailExists(String email) — check if already avaliable
}
