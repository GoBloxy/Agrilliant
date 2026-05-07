package smartfarm.dao;

import smartfarm.model.Task;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO implements GenericDAO<Task> {
    private final Connection conn = DBConnection.getInstance();

    // ═══════════════ SAVE ═══════════════
    @Override
    public void save(Task item) throws SQLException {
        if (conn == null) return;
        String sql = "INSERT INTO tasks (task_name, description, status, priority, due_date, created_at, plot_id, alert_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getTaskName());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getStatus().name());
            ps.setString(4, item.getPriority().name());
            ps.setDate(5, Date.valueOf(item.getDueDate()));
            ps.setTimestamp(6, Timestamp.valueOf(item.getCreatedAt()));
            ps.setInt(7, item.getPlotId());
            if (item.getAlertId() != null) {
                ps.setInt(8, item.getAlertId());
            } else {
                ps.setNull(8, Types.INTEGER);
            }
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setTaskId(keys.getInt(1));
                }
            }
        }
        // Insert worker assignments into junction table
        saveWorkerAssignments(item);
    }

    // ═══════════════ GET BY ID ═══════════════
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

    // ═══════════════ GET ALL ═══════════════
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

    // ═══════════════ UPDATE ═══════════════
    @Override
    public void update(Task item) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE tasks SET task_name = ?, description = ?, status = ?, priority = ?, due_date = ?, plot_id = ?, alert_id = ? WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getTaskName());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getStatus().name());
            ps.setString(4, item.getPriority().name());
            ps.setDate(5, Date.valueOf(item.getDueDate()));
            ps.setInt(6, item.getPlotId());
            if (item.getAlertId() != null) {
                ps.setInt(7, item.getAlertId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setInt(8, item.getTaskId());
            ps.executeUpdate();
        }
        // Re-sync worker assignments
        deleteWorkerAssignments(item.getTaskId());
        saveWorkerAssignments(item);
    }

    // ═══════════════ DELETE ═══════════════
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

    // ═══════════════ JUNCTION TABLE HELPERS ═══════════════

    private void saveWorkerAssignments(Task task) throws SQLException {
        if (task.getWorkerIds() == null || task.getWorkerIds().isEmpty()) return;
        String sql = "INSERT INTO worker_task (worker_id, task_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int workerId : task.getWorkerIds()) {
                ps.setInt(1, workerId);
                ps.setInt(2, task.getTaskId());
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
                while (rs.next()) {
                    workerIds.add(rs.getInt("worker_id"));
                }
            }
        }
        return workerIds;
    }

    // ═══════════════ ROW MAPPER ═══════════════
    private Task mapRow(ResultSet rs) throws SQLException {
        int alertIdRaw = rs.getInt("alert_id");
        Integer alertId = rs.wasNull() ? null : alertIdRaw;

        return new Task(
            rs.getInt("task_id"),
            rs.getString("task_name"),
            rs.getString("description"),
            Task.Status.valueOf(rs.getString("status")),
            Task.Priority.valueOf(rs.getString("priority")),
            rs.getDate("due_date").toLocalDate(),
            rs.getTimestamp("created_at").toLocalDateTime(),
            new ArrayList<>(),  // populated after by getWorkerIdsForTask
            rs.getInt("plot_id"),
            alertId
        );
    }
}
