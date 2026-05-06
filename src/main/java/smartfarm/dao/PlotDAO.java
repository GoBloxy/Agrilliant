package smartfarm.dao;

import smartfarm.model.Plot;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PlotDAO implements GenericDAO<Plot> {
    private final Connection conn = DBConnection.getInstance();

    @Override public void save(Plot item) throws SQLException {}
    @Override public Plot getById(int id) throws SQLException { return null; }
    @Override public ArrayList<Plot> getAll() throws SQLException { return new ArrayList<>(); }
    @Override public void update(Plot item) throws SQLException {}
    @Override public void delete(int id) throws SQLException {}
}
