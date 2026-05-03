package smartfarm.dao;

import smartfarm.model.SensorReading;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class SensorDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: saveReading, getLast50Readings, getReadingsByDevice
}
