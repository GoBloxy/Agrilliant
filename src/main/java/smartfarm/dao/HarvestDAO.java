package smartfarm.dao;

import smartfarm.model.HarvestRecord;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HarvestDAO implements GenericDAO<HarvestRecord> {
    private final Connection conn = DBConnection.getInstance();

    @Override public void save(HarvestRecord item) throws SQLException {}
    @Override public HarvestRecord getById(int id) throws SQLException { return null; }
    @Override public ArrayList<HarvestRecord> getAll() throws SQLException { return new ArrayList<>(); }
    @Override public void update(HarvestRecord item) throws SQLException {}
    @Override public void delete(int id) throws SQLException {}

    // TODO: getByPlot, getByCrop, getTotalYield
}
