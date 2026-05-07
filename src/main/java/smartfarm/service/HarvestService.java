package smartfarm.service;

import smartfarm.dao.CropDAO;
import smartfarm.dao.HarvestDAO;
import smartfarm.model.Crop;
import smartfarm.model.HarvestRecord;

import java.sql.SQLException;
import java.util.List;

public class HarvestService {
    private final HarvestDAO harvestDAO;
    private final CropDAO cropDAO;

    public HarvestService(HarvestDAO harvestDAO, CropDAO cropDAO) {
        this.harvestDAO = harvestDAO;
        this.cropDAO = cropDAO;
    }

    public void recordHarvest(HarvestRecord harvestRecord) {
        if (harvestRecord.getRecordId() != 0) {
            throw new RuntimeException("The Harvest Record ID Already Exists");
        }
        if (harvestRecord.getQuantityKg() <= 0) {
            throw new RuntimeException("Harvest quantity must be greater than zero");
        }
        try {
            harvestDAO.save(harvestRecord);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public double getPlotYieldSummary(int plotId) {
        try {
            return harvestDAO.getTotalYieldByPlot(plotId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public double getYieldEfficiency(int cropId) {
        Crop crop;
        try {
            crop = cropDAO.getById(cropId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
        if (crop == null) {
            throw new RuntimeException("Crop Not Found");
        }
        if (crop.getExpectedYield() <= 0) {
            return 0;
        }
        try {
            return harvestDAO.getTotalYieldByCrop(cropId) / crop.getExpectedYield();
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public HarvestRecord getHarvestRecordById(int recordId) {
        try {
            return harvestDAO.getById(recordId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<HarvestRecord> getAllHarvestRecords() {
        try {
            return harvestDAO.getAll();
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<HarvestRecord> getHarvestRecordsByPlot(int plotId) {
        try {
            return harvestDAO.getByPlot(plotId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<HarvestRecord> getHarvestRecordsByCrop(int cropId) {
        try {
            return harvestDAO.getByCrop(cropId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public double getTotalYield() {
        try {
            return harvestDAO.getTotalYield();
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void updateHarvestRecord(HarvestRecord harvestRecord) {
        if (harvestRecord.getRecordId() == 0) {
            throw new RuntimeException("The Harvest Record Doesn't Exist");
        }
        if (harvestRecord.getQuantityKg() <= 0) {
            throw new RuntimeException("Harvest quantity must be greater than zero");
        }
        try {
            harvestDAO.update(harvestRecord);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void deleteHarvestRecord(int recordId) {
        try {
            harvestDAO.delete(recordId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }
}
