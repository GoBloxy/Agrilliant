package smartfarm.dao;

import smartfarm.model.Plot;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PlotDAO implements GenericDAO<Plot> {
    private final Connection conn = DBConnection.getInstance();

    // Detect once if the irrigation_type column exists (lets the app work
    // even when the schema migration hasn't been run yet).
    private static Boolean hasIrrigationCol = null;
    private static Boolean hasStatusCol = null;

    private boolean hasIrrigationColumn() {
        if (hasIrrigationCol != null) return hasIrrigationCol;
        try {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "plots", "irrigation_type")) {
                hasIrrigationCol = rs.next();
            }
        } catch (SQLException e) {
            hasIrrigationCol = false;
        }
        return hasIrrigationCol;
    }

    private boolean hasStatusColumn() {
        if (hasStatusCol != null) return hasStatusCol;
        try {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "plots", "status")) {
                hasStatusCol = rs.next();
            }
        } catch (SQLException e) {
            hasStatusCol = false;
        }
        return hasStatusCol;
    }

    @Override
    public void save(Plot plot) throws SQLException {
        boolean hasIrr = hasIrrigationColumn();
        boolean hasSts = hasStatusColumn();
        StringBuilder sb = new StringBuilder("INSERT INTO plots (name, location, size_acres, soil_type, manager_id");
        if (hasIrr) sb.append(", irrigation_type");
        if (hasSts) sb.append(", status");
        sb.append(", created_at, updated_at) VALUES (?, ?, ?, ?, ?");
        if (hasIrr) sb.append(", ?");
        if (hasSts) sb.append(", ?");
        sb.append(", ?, ?)");
        try (PreparedStatement stmt = conn.prepareStatement(sb.toString(), Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, plot.getName());
            stmt.setString(2, plot.getLocation());
            stmt.setDouble(3, plot.getSizeAcres());
            stmt.setString(4, plot.getSoilType());
            stmt.setInt(5, plot.getManagerId());
            int idx = 6;
            if (hasIrr) stmt.setString(idx++, plot.getIrrigationType());
            if (hasSts) stmt.setString(idx++, plot.getStatus());
            stmt.setTimestamp(idx++, Timestamp.valueOf(plot.getCreatedAt()));
            stmt.setTimestamp(idx, Timestamp.valueOf(plot.getUpdatedAt()));
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    plot.setPlotId(rs.getInt(1));
                }
            }
        }
    }

    @Override
    public Plot getById(int id) throws SQLException {
        String sql = "SELECT * FROM plots WHERE plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlot(rs);
            }
        }
        return null;
    }

    @Override
    public ArrayList<Plot> getAll() throws SQLException {
        String sql = "SELECT * FROM plots ORDER BY created_at DESC";
        ArrayList<Plot> plots = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                plots.add(mapResultSetToPlot(rs));
            }
        }
        return plots;
    }

    @Override
    public void update(Plot plot) throws SQLException {
        boolean hasIrr = hasIrrigationColumn();
        boolean hasSts = hasStatusColumn();
        StringBuilder sb = new StringBuilder("UPDATE plots SET name = ?, location = ?, size_acres = ?, soil_type = ?, manager_id = ?");
        if (hasIrr) sb.append(", irrigation_type = ?");
        if (hasSts) sb.append(", status = ?");
        sb.append(", updated_at = ? WHERE plot_id = ?");
        try (PreparedStatement stmt = conn.prepareStatement(sb.toString())) {
            stmt.setString(1, plot.getName());
            stmt.setString(2, plot.getLocation());
            stmt.setDouble(3, plot.getSizeAcres());
            stmt.setString(4, plot.getSoilType());
            stmt.setInt(5, plot.getManagerId());
            int idx = 6;
            if (hasIrr) stmt.setString(idx++, plot.getIrrigationType());
            if (hasSts) stmt.setString(idx++, plot.getStatus());
            stmt.setTimestamp(idx++, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(idx, plot.getPlotId());
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        // 1. Delete harvest records for all crops in this plot
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE hr FROM harvest_records hr JOIN crops c ON hr.crop_id = c.crop_id WHERE c.plot_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        // 2. Delete crops
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM crops WHERE plot_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        // 3. Delete worker_task entries for tasks in this plot
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE wt FROM worker_task wt JOIN tasks t ON wt.task_id = t.task_id WHERE t.plot_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        // 4. Delete tasks
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM tasks WHERE plot_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        // 5. Delete sensor readings for devices linked to this plot
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE sr FROM sensor_readings sr JOIN devices d ON sr.device_id = d.device_id WHERE d.plot_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        // 6. Delete devices
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM devices WHERE plot_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        // 7. Delete the plot
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM plots WHERE plot_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public List<Plot> getByManager(int managerId) throws SQLException {
        String sql = "SELECT * FROM plots WHERE manager_id = ? ORDER BY name";
        List<Plot> plots = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, managerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                plots.add(mapResultSetToPlot(rs));
            }
        }
        return plots;
    }

    public List<Plot> searchByName(String searchTerm) throws SQLException {
        String sql = "SELECT * FROM plots WHERE name LIKE ? OR location LIKE ? ORDER BY name";
        List<Plot> plots = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + searchTerm + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                plots.add(mapResultSetToPlot(rs));
            }
        }
        return plots;
    }

    private Plot mapResultSetToPlot(ResultSet rs) throws SQLException {
        String irr = hasIrrigationColumn() ? rs.getString("irrigation_type") : null;
        String sts = hasStatusColumn() ? rs.getString("status") : null;
        return new Plot(
            rs.getInt("plot_id"),
            rs.getString("name"),
            rs.getString("location"),
            rs.getDouble("size_acres"),
            rs.getString("soil_type"),
            rs.getInt("manager_id"),
            irr,
            sts,
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
