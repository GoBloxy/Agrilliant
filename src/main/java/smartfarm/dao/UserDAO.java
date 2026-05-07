package smartfarm.dao;
import smartfarm.model.User;

import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class UserDAO implements GenericDAO<User> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(User user) throws SQLException {

    }

    @Override
    public User getById(int id) throws SQLException {
        return new User("placehlder", "placehlder", "placehlder", User.Role.MANAGER);
    }


    public User getByEmail(String email) throws SQLException {
        return new User("placehlder", "placehlder", "placehlder", User.Role.MANAGER);
    }

    @Override
    public List<User> getAll() throws SQLException {
        return null; //placeholder
    }

    @Override
    public void update(User item) throws SQLException {

    }

    @Override
    public void delete(int id) throws SQLException {

    }



}