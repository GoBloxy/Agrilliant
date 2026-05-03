package smartfarm.dao;

import smartfarm.model.Task;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class TaskDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: addTask, getActiveTaskCount, getOverdueTasks, advanceStatus, getTasksByWorker
}
