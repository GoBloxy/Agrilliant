package smartfarm.dao;

import smartfarm.model.Alert;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AlertDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: save, getUnresolved, markResolved, getAlertsByPlot
}
