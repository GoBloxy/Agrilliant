package smartfarm.dao;

import smartfarm.model.Worker;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WorkerDAO implements GenericDAO<Worker> {
    // TODO: addWorker, getWorkerById, getAllWorkers, updateWorker, deleteWorker
    private final Connection conn = DBConnection.getInstance();
    public void save(Worker item) throws SQLException{};
    public Worker getById(int id) throws SQLException{
        Worker worker = new Worker("placeholder", "placeholder", "placeholder", "placeholder");
        return worker;
    };
    public List<Worker> getAll() throws SQLException {
        List<Worker> placeholder = new ArrayList<>();
        return placeholder;
    };
    public void update(Worker item) throws SQLException{};
    public void delete(int id) throws SQLException {};
}
