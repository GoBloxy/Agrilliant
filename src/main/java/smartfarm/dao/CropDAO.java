package smartfarm.dao;

import smartfarm.model.Crop;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CropDAO implements GenericDAO<Crop> {
    private final Connection conn = DBConnection.getInstance();

    @Override public void save(Crop item) throws SQLException {}
    @Override public Crop getById(int id) throws SQLException { return null; }
    @Override public ArrayList<Crop> getAll() throws SQLException { return new ArrayList<>(); }
    @Override public void update(Crop item) throws SQLException {}
    @Override public void delete(int id) throws SQLException {}

    // TODO: getCropsByPlot, updateGrowthStage, getOverdueCrops
}
