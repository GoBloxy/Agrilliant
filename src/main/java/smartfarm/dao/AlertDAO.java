package smartfarm.dao;

import smartfarm.model.Alert;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AlertDAO implements GenericDAO<Alert> {
    private final Connection conn = DBConnection.getInstance();

    @Override public void save(Alert item) throws SQLException {}
    @Override public Alert getById(int id) throws SQLException { return null; }
    @Override public ArrayList<Alert> getAll() throws SQLException { return new ArrayList<>(); }
    @Override public void update(Alert item) throws SQLException {}
    @Override public void delete(int id) throws SQLException {}

    // TODO: getUnresolved, markResolved, getAlertsByPlot
}
