package smartfarm.service;

import smartfarm.dao.CropDAO;
import smartfarm.model.Crop;

import java.sql.SQLException;
import java.util.List;

public class CropService {
    private final CropDAO cropDAO;

    public CropService(CropDAO cropDAO) {
        this.cropDAO = cropDAO;
    }

    public void plantCrop(Crop crop) {
        if (crop.getCropId() != 0) {
            throw new RuntimeException("The Crop ID Already Exists");
        }
        try {
            cropDAO.save(crop);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void advanceCropStage(int cropId) {
        if (cropId == 0) {
            throw new RuntimeException("The Crop Doesn't Exist");
        }
        Crop crop;
        try {
            crop = cropDAO.getById(cropId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
        if (crop == null) {
            throw new RuntimeException("Crop Not Found");
        }
        crop.advanceStage();
        try {
            cropDAO.updateGrowthStage(cropId, crop.getGrowthStage());
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Crop> checkOverdueCrops() {
        try {
            return cropDAO.getOverdueCrops();
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void updateCrop(Crop crop) {
        if (crop.getCropId() == 0) {
            throw new RuntimeException("The Crop Doesn't Exist");
        }
        try {
            cropDAO.update(crop);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public Crop getCropById(int cropId) {
        if (cropId == 0) {
            throw new RuntimeException("The Crop Doesn't Exist");
        }
        try {
            return cropDAO.getById(cropId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Crop> getAllCrops() {
        try {
            return cropDAO.getAll();
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Crop> getCropsByPlot(int plotId) {
        try {
            return cropDAO.getCropsByPlot(plotId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void deleteCrop(int cropId) {
        try {
            cropDAO.delete(cropId);
        } catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }
}
