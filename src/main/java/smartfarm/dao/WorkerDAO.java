package smartfarm.dao;

import smartfarm.model.User;
import smartfarm.model.Worker;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WorkerDAO implements GenericDAO<Worker> {
    // TODO: addWorker, getWorkerById, getAllWorkers, updateWorker, deleteWorker
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Worker item) throws SQLException{};

    @Override
    public Worker getById(int id) throws SQLException{
        return new Worker("placeholder", "placeholder", "placeholder", "placeholder");

    };

    public Worker getByEmail(String email) throws SQLException {
        return new Worker("placehlder", "placehlder", "placehlder", "placeholder");
    }

    @Override
    public ArrayList<Worker> getAll() throws SQLException {
        ArrayList<Worker> placeholder = new ArrayList<>();
        return placeholder;
    };

    @Override
    public void update(Worker item) throws SQLException{};

    @Override
    public void delete(int id) throws SQLException {};
}
