package smartfarm.dao;

import smartfarm.model.Plot;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PlotDAO implements GenericDAO<Plot> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Plot plot) throws SQLException {
        String sql = "INSERT INTO plots (name, location, size_acres, soil_type, manager_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, plot.getName());
            stmt.setString(2, plot.getLocation());
            stmt.setDouble(3, plot.getSizeAcres());
            stmt.setString(4, plot.getSoilType());
            stmt.setInt(5, plot.getManagerId());
            stmt.setTimestamp(6, Timestamp.valueOf(plot.getCreatedAt()));
            stmt.setTimestamp(7, Timestamp.valueOf(plot.getUpdatedAt()));
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
        String sql = "UPDATE plots SET name = ?, location = ?, size_acres = ?, soil_type = ?, manager_id = ?, updated_at = ? WHERE plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, plot.getName());
            stmt.setString(2, plot.getLocation());
            stmt.setDouble(3, plot.getSizeAcres());
            stmt.setString(4, plot.getSoilType());
            stmt.setInt(5, plot.getManagerId());
            stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(7, plot.getPlotId());
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
        return new Plot(
            rs.getInt("plot_id"),
            rs.getString("name"),
            rs.getString("location"),
            rs.getDouble("size_acres"),
            rs.getString("soil_type"),
            rs.getInt("manager_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
