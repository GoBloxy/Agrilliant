package smartfarm.dao;
import smartfarm.model.User;

import smartfarm.model.User;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDAO implements GenericDAO<User> {
    private final Connection conn = DBConnection.getInstance();


    public void save(User user) throws SQLException {

    }


    public User getById(int id) throws SQLException {
        return new User("placehlder", "placehlder", "placehlder", User.Role.FARMER);
    }


    public User getByEmail(String email) throws SQLException {
        return new User("placehlder", "placehlder", "placehlder", User.Role.FARMER);
    }


    public List<User> getAll() throws SQLException {
        return new ArrayList<>(); //placeholder
    }


    public void update(User item) throws SQLException {

    }


    public void delete(int id) throws SQLException {

    }



}