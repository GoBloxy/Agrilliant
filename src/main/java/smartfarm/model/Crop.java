package smartfarm.model;

import java.time.LocalDate;

public class Crop {
    public enum GrowthStage { PLANTED, GROWING, READY, HARVESTED }

    private int cropId;
    private String cropName;
    private LocalDate plantingDate;
    private LocalDate harvestDate;
    private GrowthStage growthStage;
    private int plotId;
    private double expectedYieldKg;

    // TODO: Constructor, advanceStage(), isOverdue(), getters, setters
}
