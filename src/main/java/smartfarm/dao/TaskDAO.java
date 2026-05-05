package smartfarm.dao;

import smartfarm.model.Task;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO implements GenericDAO<Task> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Task item) throws SQLException {

    }

    @Override
    public Task getById(int id) throws SQLException {
        return null;
    }

    @Override
    public ArrayList<Task> getAll() throws SQLException {
        return null;
    }

    @Override
    public void update(Task item) throws SQLException {

    }

    @Override
    public void delete(int id) throws SQLException {

    }

    // TODO: addTask, getActiveTaskCount, getOverdueTasks, advanceStatus, getTasksByWorker
}
