package smartfarm.model;

import java.time.LocalDate;

public class Crop {

    public enum GrowthStage {PLANTED, GROWING, READY, HARVESTED}

    private int cropId;
    private String cropName;
    private LocalDate plantingDate;
    private LocalDate harvestDate;
    private GrowthStage growthStage;
    private int plotId;
    private double expectedYieldKg;

    public Crop(String cropName, LocalDate plantingDate,
                LocalDate harvestDate, int plotId, double expectedYieldKg) {
        this.cropName = cropName;
        this.plantingDate = plantingDate;
        this.harvestDate = harvestDate;
        this.growthStage = GrowthStage.PLANTED;
        this.plotId = plotId;
        this.expectedYieldKg = expectedYieldKg;
    }

    /**
     * Advance to next growth stage
     */
    public void advanceStage() {
        switch (growthStage) {
            case PLANTED -> growthStage = GrowthStage.GROWING;
            case GROWING -> growthStage = GrowthStage.READY;
            case READY -> growthStage = GrowthStage.HARVESTED;
            case HARVESTED -> { /* already done */ }
        }
    }

    /**
     * Check if crop is overdue for harvest
     */
    public boolean isOverdue() {
        return growthStage == GrowthStage.READY
                && LocalDate.now().isAfter(harvestDate);
    }

    // ── Getters ──

    public int getCropId() {
        return cropId;
    }

    public String getCropName() {
        return cropName;
    }

    public LocalDate getPlantingDate() {
        return plantingDate;
    }

    public LocalDate getHarvestDate() {
        return harvestDate;
    }

    public GrowthStage getGrowthStage() {
        return growthStage;
    }

    public int getPlotId() {
        return plotId;
    }

    public double getExpectedYieldKg() {
        return expectedYieldKg;
    }

    // ── Setters ──

    public void setCropId(int cropId) {
        this.cropId = cropId;
    }

    public void setCropName(String cropName) {
        this.cropName = cropName;
    }

    public void setPlantingDate(LocalDate plantingDate) {
        this.plantingDate = plantingDate;
    }

    public void setHarvestDate(LocalDate harvestDate) {
        this.harvestDate = harvestDate;
    }

    public void setGrowthStage(GrowthStage growthStage) {
        this.growthStage = growthStage;
    }

    public void setPlotId(int plotId) {
        this.plotId = plotId;
    }

    public void setExpectedYieldKg(double expectedYieldKg) {
        this.expectedYieldKg = expectedYieldKg;
    }
}
