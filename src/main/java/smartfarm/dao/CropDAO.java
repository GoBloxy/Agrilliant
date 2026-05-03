package smartfarm.dao;

import smartfarm.model.Crop;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class CropDAO {
    private final Connection conn = DBConnection.getInstance();

    // TODO: addCrop, getCropById, getCropsByPlot, updateGrowthStage, getOverdueCrops
}
