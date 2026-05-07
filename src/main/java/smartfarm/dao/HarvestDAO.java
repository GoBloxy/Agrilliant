package smartfarm.dao;

import smartfarm.model.HarvestRecord;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HarvestDAO implements GenericDAO<HarvestRecord> {
    private final Connection conn = DBConnection.getInstance();

    private HarvestRecord extractHarvestRecord(ResultSet rs) throws SQLException {
        return new HarvestRecord(
            rs.getInt("record_id"),
            rs.getObject("harvest_date", java.time.LocalDate.class),
            rs.getDouble("quantity_kg"),
            HarvestRecord.Grade.valueOf(rs.getString("grade")),
            rs.getInt("crop_id")
        );
    }

    @Override
    public void save(HarvestRecord item) throws SQLException {
        String sql = "INSERT INTO harvest_records (harvest_date, quantity_kg, grade, crop_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setObject(1, item.getHarvestDate());
            stmt.setDouble(2, item.getQuantityKg());
            stmt.setString(3, item.getGrade().name());
            stmt.setInt(4, item.getCropId());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setRecordId(keys.getInt(1));
                }
            }
        }
    }

    @Override
    public HarvestRecord getById(int id) throws SQLException {
        String sql = "SELECT * FROM harvest_records WHERE record_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return extractHarvestRecord(rs);
            }
        }
        return null;
    }

    @Override
    public ArrayList<HarvestRecord> getAll() throws SQLException {
        ArrayList<HarvestRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM harvest_records ORDER BY harvest_date DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractHarvestRecord(rs));
            }
        }
        return list;
    }

    @Override
    public void update(HarvestRecord item) throws SQLException {
        String sql = "UPDATE harvest_records SET harvest_date = ?, quantity_kg = ?, grade = ?, crop_id = ? WHERE record_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, item.getHarvestDate());
            stmt.setDouble(2, item.getQuantityKg());
            stmt.setString(3, item.getGrade().name());
            stmt.setInt(4, item.getCropId());
            stmt.setInt(5, item.getRecordId());
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM harvest_records WHERE record_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public List<HarvestRecord> getByPlot(int plotId) throws SQLException {
        List<HarvestRecord> list = new ArrayList<>();
        String sql = "SELECT hr.* FROM harvest_records hr JOIN crops c ON hr.crop_id = c.crop_id WHERE c.plot_id = ? ORDER BY hr.harvest_date DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractHarvestRecord(rs));
            }
        }
        return list;
    }

    public List<HarvestRecord> getByCrop(int cropId) throws SQLException {
        List<HarvestRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM harvest_records WHERE crop_id = ? ORDER BY harvest_date DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cropId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractHarvestRecord(rs));
            }
        }
        return list;
    }

    public double getTotalYield() throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity_kg), 0) AS total_yield FROM harvest_records";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total_yield");
            }
        }
        return 0;
    }

    public double getTotalYieldByPlot(int plotId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(hr.quantity_kg), 0) AS total_yield FROM harvest_records hr JOIN crops c ON hr.crop_id = c.crop_id WHERE c.plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total_yield");
            }
        }
        return 0;
    }

    public double getTotalYieldByCrop(int cropId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity_kg), 0) AS total_yield FROM harvest_records WHERE crop_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cropId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total_yield");
            }
        }
        return 0;
    }
}
