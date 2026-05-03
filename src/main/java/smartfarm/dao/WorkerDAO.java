package smartfarm.dao;

import smartfarm.model.Worker;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class WorkerDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: addWorker, getWorkerById, getAllWorkers, updateWorker, deleteWorker
}
