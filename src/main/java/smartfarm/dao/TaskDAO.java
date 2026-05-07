package smartfarm.dao;

import smartfarm.model.Task;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO implements GenericDAO<Task> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Task item) throws SQLException {
        if (conn == null) return;
        String sql = "INSERT INTO tasks (description, status, due_date, plot_id, alert_id, assigned_by_mgr_id, alert_type) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getDescription());
            ps.setString(2, item.getStatus().name());
            if (item.getDueDate() != null) ps.setDate(3, Date.valueOf(item.getDueDate()));
            else ps.setNull(3, Types.DATE);
            ps.setInt(4, item.getPlotId());
            if (item.getAlertId() != null) ps.setInt(5, item.getAlertId());
            else ps.setNull(5, Types.INTEGER);
            ps.setInt(6, item.getAssignedByMgrId());
            ps.setString(7, item.getAlertType());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) item.setTaskId(keys.getInt(1));
            }
        }
        saveWorkerAssignments(item);
    }

    @Override
    public Task getById(int id) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM tasks WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Task task = mapRow(rs);
                    task.setWorkerIds(getWorkerIdsForTask(task.getTaskId()));
                    return task;
                }
            }
        }
        return null;
    }

    @Override
    public List<Task> getAll() throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM tasks ORDER BY due_date";
        List<Task> tasks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Task task = mapRow(rs);
                task.setWorkerIds(getWorkerIdsForTask(task.getTaskId()));
                tasks.add(task);
            }
        }
        return tasks;
    }

    @Override
    public void update(Task item) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE tasks SET description = ?, status = ?, due_date = ?, plot_id = ?, alert_id = ?, assigned_by_mgr_id = ?, alert_type = ? WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getDescription());
            ps.setString(2, item.getStatus().name());
            if (item.getDueDate() != null) ps.setDate(3, Date.valueOf(item.getDueDate()));
            else ps.setNull(3, Types.DATE);
            ps.setInt(4, item.getPlotId());
            if (item.getAlertId() != null) ps.setInt(5, item.getAlertId());
            else ps.setNull(5, Types.INTEGER);
            ps.setInt(6, item.getAssignedByMgrId());
            ps.setString(7, item.getAlertType());
            ps.setInt(8, item.getTaskId());
            ps.executeUpdate();
        }
        deleteWorkerAssignments(item.getTaskId());
        saveWorkerAssignments(item);
    }

    @Override
    public void delete(int id) throws SQLException {
        if (conn == null) return;
        deleteWorkerAssignments(id);
        String sql = "DELETE FROM tasks WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private void saveWorkerAssignments(Task task) throws SQLException {
        if (task.getWorkerIds() == null || task.getWorkerIds().isEmpty()) return;
        String sql = "INSERT INTO worker_task (worker_id, task_id, assigned_by_mgr_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int workerId : task.getWorkerIds()) {
                ps.setInt(1, workerId);
                ps.setInt(2, task.getTaskId());
                ps.setInt(3, task.getAssignedByMgrId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteWorkerAssignments(int taskId) throws SQLException {
        String sql = "DELETE FROM worker_task WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.executeUpdate();
        }
    }

    private List<Integer> getWorkerIdsForTask(int taskId) throws SQLException {
        List<Integer> workerIds = new ArrayList<>();
        String sql = "SELECT worker_id FROM worker_task WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) workerIds.add(rs.getInt("worker_id"));
            }
        }
        return workerIds;
    }

    private Task mapRow(ResultSet rs) throws SQLException {
        int alertIdRaw = rs.getInt("alert_id");
        Integer alertId = rs.wasNull() ? null : alertIdRaw;

        Date due = rs.getDate("due_date");
        return new Task(
            rs.getInt("task_id"),
            rs.getString("description"),
            Task.Status.valueOf(rs.getString("status")),
            due != null ? due.toLocalDate() : null,
            rs.getInt("plot_id"),
            alertId,
            rs.getInt("assigned_by_mgr_id"),
            rs.getString("alert_type"),
            new ArrayList<>()
        );
    }
}
