package smartfarm.dao;

import smartfarm.model.HarvestRecord;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class HarvestDAO implements GenericDAO<HarvestRecord> {
    private final Connection conn = DBConnection.getInstance();

    // TODO: save, getByPlot, getByCrop, getTotalYield
}
