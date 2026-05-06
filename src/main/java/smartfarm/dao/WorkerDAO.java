package smartfarm.dao;

import smartfarm.model.User;
import smartfarm.model.Worker;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class WorkerDAO implements GenericDAO<Worker> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Worker item) throws SQLException{};

    @Override
    public Worker getById(int id) throws SQLException{
        return null;
    };

    public Worker getByEmail(String email) throws SQLException {
        return null;
    }

    @Override
    public List<Worker> getAll() throws SQLException {
        return null;
    };

    @Override
    public void update(Worker item) throws SQLException{};

    @Override
    public void delete(int id) throws SQLException {};
}
