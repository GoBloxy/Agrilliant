package smartfarm.model;

import java.time.LocalDate;

public class HarvestRecord {
    public enum Grade { A, B, C }

    private int recordId;
    private LocalDate harvestDate;
    private double quantityKg;
    private Grade grade;
    private int cropId;

    // TODO: Constructor, getters, setters
}
