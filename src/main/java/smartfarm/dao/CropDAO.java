package smartfarm.dao;

import smartfarm.model.Crop;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CropDAO implements GenericDAO<Crop> {
    private final Connection conn = DBConnection.getInstance();

    private Crop extractCrop(ResultSet rs) throws SQLException {
        Crop crop = new Crop(
            rs.getString("crop_name"),
            rs.getObject("planting_date", java.time.LocalDate.class),
            rs.getObject("harvest_date", java.time.LocalDate.class),
            rs.getInt("plot_id"),
            rs.getDouble("expected_yield")
        );
        crop.setCropId(rs.getInt("crop_id"));
        crop.setGrowthStage(Crop.GrowthStage.valueOf(rs.getString("growth_stage")));
        return crop;
    }

    @Override
    public void save(Crop item) throws SQLException {
        String sql = "INSERT INTO crops (crop_name, planting_date, harvest_date, growth_stage, plot_id, expected_yield) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getCropName());
            stmt.setObject(2, item.getPlantingDate());
            stmt.setObject(3, item.getHarvestDate());
            stmt.setString(4, item.getGrowthStage().name());
            stmt.setInt(5, item.getPlotId());
            stmt.setDouble(6, item.getExpectedYield());
            stmt.executeUpdate();
        }
    }

    @Override
    public Crop getById(int id) throws SQLException {
        String sql = "SELECT * FROM crops WHERE crop_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return extractCrop(rs);
            }
        }
        return null;
    }

    @Override
    public ArrayList<Crop> getAll() throws SQLException {
        ArrayList<Crop> list = new ArrayList<>();
        String sql = "SELECT * FROM crops ORDER BY planting_date DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractCrop(rs));
            }
        }
        return list;
    }

    @Override
    public void update(Crop item) throws SQLException {
        String sql = "UPDATE crops SET crop_name = ?, planting_date = ?, harvest_date = ?, growth_stage = ?, plot_id = ?, expected_yield = ? WHERE crop_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getCropName());
            stmt.setObject(2, item.getPlantingDate());
            stmt.setObject(3, item.getHarvestDate());
            stmt.setString(4, item.getGrowthStage().name());
            stmt.setInt(5, item.getPlotId());
            stmt.setDouble(6, item.getExpectedYield());
            stmt.setInt(7, item.getCropId());
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM crops WHERE crop_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public List<Crop> getCropsByPlot(int plotId) throws SQLException {
        List<Crop> list = new ArrayList<>();
        String sql = "SELECT * FROM crops WHERE plot_id = ? ORDER BY planting_date DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractCrop(rs));
            }
        }
        return list;
    }

    public void updateGrowthStage(int cropId, Crop.GrowthStage growthStage) throws SQLException {
        String sql = "UPDATE crops SET growth_stage = ? WHERE crop_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, growthStage.name());
            stmt.setInt(2, cropId);
            stmt.executeUpdate();
        }
    }

    public List<Crop> getOverdueCrops() throws SQLException {
        List<Crop> list = new ArrayList<>();
        String sql = "SELECT * FROM crops WHERE growth_stage = ? AND harvest_date < CURRENT_DATE ORDER BY harvest_date ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, Crop.GrowthStage.FRUITING.name());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(extractCrop(rs));
            }
        }
        return list;
    }
}
