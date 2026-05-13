package smartfarm.dao;

import smartfarm.model.Alert;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for {@link smartfarm.model.Alert} rows.
 *
 * <h2>Thread-safety (post-Android-migration H4 / H5)</h2>
 *
 * All public methods on this class are safe to call from a
 * background thread. JDBC is blocking, so callers must invoke
 * them from
 * {@link smartfarm.util.DBConnection#runAsync(java.util.concurrent.Callable)}
 * (or a controller {@code Task<T>}) — never from the JavaFX UI
 * thread, otherwise the UI freezes for the duration of the
 * round-trip.
 *
 * <p>None of the methods log to {@link System#out} /
 * {@link System#err} in a hot loop (Android logcat noise).
 * Exceptions are propagated via {@code throws SQLException} so
 * the caller decides whether to surface them in the UI (typically
 * by calling {@link smartfarm.util.Logger#e(String, String, Throwable)}).
 */
public class AlertDAO implements GenericDAO<Alert> {
    // TODO(phase-2): consider replacing this cached field with a
    //   private Connection conn() { return DBConnection.getInstance(); }
    // method so a torn connection auto-recovers via H4's getInstance()
    // re-create logic. Currently if this handle dies the DAO needs to
    // be re-instantiated; that's acceptable for now since DAOs are
    // typically short-lived (controller scope).
    private final Connection conn = DBConnection.getInstance();

    // ── Helper: builds an Alert object from a ResultSet row ──
    private Alert extractAlert(ResultSet rs) throws SQLException {
        return new Alert(
            rs.getInt("alert_id"),
            rs.getString("alert_type"),
            Alert.Severity.valueOf(rs.getString("severity")),
            rs.getString("message"),
            rs.getBoolean("resolved"),
            rs.getObject("timestamp", LocalDateTime.class),
            rs.getInt("plot_id")
        );
    }

    // ── GenericDAO methods ──

    @Override
    public void save(Alert item) throws SQLException {
        String sql = "INSERT INTO alerts (alert_type, severity, message, resolved, timestamp, plot_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getAlertType());
            stmt.setString(2, item.getSeverity().name());
            stmt.setString(3, item.getMessage());
            stmt.setBoolean(4, item.isResolved());
            stmt.setObject(5, item.getTimestamp());
            stmt.setInt(6, item.getPlotId());
            stmt.executeUpdate();
        }
    }

    @Override
    public Alert getById(int id) throws SQLException {
        String sql = "SELECT * FROM alerts WHERE alert_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return extractAlert(rs);
            }
        }
        return null;
    }

    @Override
    public ArrayList<Alert> getAll() throws SQLException {
        ArrayList<Alert> list = new ArrayList<>();
        String sql = "SELECT * FROM alerts ORDER BY timestamp DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractAlert(rs));
            }
        }
        return list;
    }

    @Override
    public void update(Alert item) throws SQLException {
        String sql = "UPDATE alerts SET alert_type = ?, severity = ?, message = ?, resolved = ?, timestamp = ?, plot_id = ? WHERE alert_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getAlertType());
            stmt.setString(2, item.getSeverity().name());
            stmt.setString(3, item.getMessage());
            stmt.setBoolean(4, item.isResolved());
            stmt.setObject(5, item.getTimestamp());
            stmt.setInt(6, item.getPlotId());
            stmt.setInt(7, item.getAlertId());
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM alerts WHERE alert_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    // ── Alert-specific queries ──

    // Get all unresolved alerts (newest first)
    public List<Alert> getUnresolved() throws SQLException {
        List<Alert> list = new ArrayList<>();
        String sql = "SELECT * FROM alerts WHERE resolved = false ORDER BY timestamp DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractAlert(rs));
            }
        }
        return list;
    }

    // Mark a single alert as resolved by its ID
    public void markResolved(int alertId) throws SQLException {
        String sql = "UPDATE alerts SET resolved = true WHERE alert_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, alertId);
            stmt.executeUpdate();
        }
    }

    // Get all alerts for a specific plot (newest first)
    public List<Alert> getAlertsByPlot(int plotId) throws SQLException {
        List<Alert> list = new ArrayList<>();
        String sql = "SELECT * FROM alerts WHERE plot_id = ? ORDER BY timestamp DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractAlert(rs));
            }
        }
        return list;
    }
}
