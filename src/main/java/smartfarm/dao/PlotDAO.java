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
        String sql = "DELETE FROM plots WHERE plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
