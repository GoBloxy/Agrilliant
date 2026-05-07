package smartfarm.model;

import java.time.LocalDate;

public class Crop {

    public enum GrowthStage { SEED, SEEDLING, VEGETATIVE, FLOWERING, FRUITING, HARVESTED }

    private int cropId;
    private String cropName;
    private LocalDate plantingDate;
    private LocalDate harvestDate;
    private GrowthStage growthStage;
    private int plotId;
    private double expectedYield;

    public Crop(String cropName, LocalDate plantingDate, LocalDate harvestDate, int plotId, double expectedYield) {
        this.cropName = cropName;
        this.plantingDate = plantingDate;
        this.harvestDate = harvestDate;
        this.growthStage = GrowthStage.SEED;
        this.plotId = plotId;
        this.expectedYield = expectedYield;
    }

    // Advance to the next stage
    public void advanceStage() {
        switch (growthStage) {
            case SEED       -> growthStage = GrowthStage.SEEDLING;
            case SEEDLING   -> growthStage = GrowthStage.VEGETATIVE;
            case VEGETATIVE -> growthStage = GrowthStage.FLOWERING;
            case FLOWERING  -> growthStage = GrowthStage.FRUITING;
            case FRUITING   -> growthStage = GrowthStage.HARVESTED;
            case HARVESTED  -> { /* already done */ }
        }
    }

    /**
     * Check if crop is overdue for harvest
     */
    public boolean isOverdue() {
        return growthStage == GrowthStage.FRUITING && LocalDate.now().isAfter(harvestDate);
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

    public double getExpectedYield() {
        return expectedYield;
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

    public void setExpectedYield(double expectedYield) {
        this.expectedYield = expectedYield;
    }
}
