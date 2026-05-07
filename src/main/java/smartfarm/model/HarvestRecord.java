package smartfarm.model;

import java.time.LocalDate;

public class HarvestRecord {

    public enum Grade { A, A_MINUS, B_PLUS, B, C }

    private int recordId;
    private LocalDate harvestDate;
    private double quantityKg;
    private Grade grade;
    private double moisturePercent;
    private double expectedYieldKg;
    private int cropId;
    private int workerId;

    // Full constructor (loading from DB)
    public HarvestRecord(int recordId, LocalDate harvestDate, double quantityKg, Grade grade,
                         double moisturePercent, double expectedYieldKg, int cropId, int workerId) {
        this.recordId = recordId;
        this.harvestDate = harvestDate;
        this.quantityKg = quantityKg;
        this.grade = grade;
        this.moisturePercent = moisturePercent;
        this.expectedYieldKg = expectedYieldKg;
        this.cropId = cropId;
        this.workerId = workerId;
    }

    // Without recordId (creating new)
    public HarvestRecord(LocalDate harvestDate, double quantityKg, Grade grade,
                         double moisturePercent, double expectedYieldKg, int cropId, int workerId) {
        this.recordId = -1;
        this.harvestDate = harvestDate;
        this.quantityKg = quantityKg;
        this.grade = grade;
        this.moisturePercent = moisturePercent;
        this.expectedYieldKg = expectedYieldKg;
        this.cropId = cropId;
        this.workerId = workerId;
    }

    public double getYieldVsExpectedPercent() {
        if (expectedYieldKg == 0) return 0;
        return ((quantityKg - expectedYieldKg) / expectedYieldKg) * 100;
    }

    // ── Getters ──

    public int getRecordId() { return recordId; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public double getQuantityKg() { return quantityKg; }
    public Grade getGrade() { return grade; }
    public double getMoisturePercent() { return moisturePercent; }
    public double getExpectedYieldKg() { return expectedYieldKg; }
    public int getCropId() { return cropId; }
    public int getWorkerId() { return workerId; }

    // ── Setters ──

    public void setRecordId(int recordId) { this.recordId = recordId; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }
    public void setQuantityKg(double quantityKg) { this.quantityKg = quantityKg; }
    public void setGrade(Grade grade) { this.grade = grade; }
    public void setMoisturePercent(double moisturePercent) { this.moisturePercent = moisturePercent; }
    public void setExpectedYieldKg(double expectedYieldKg) { this.expectedYieldKg = expectedYieldKg; }
    public void setCropId(int cropId) { this.cropId = cropId; }
    public void setWorkerId(int workerId) { this.workerId = workerId; }

}

