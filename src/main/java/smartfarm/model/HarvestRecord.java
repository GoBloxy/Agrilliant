package smartfarm.model;

import java.time.LocalDate;

public class HarvestRecord {

    public enum Grade { A, B, C }

    private int recordId;
    private LocalDate harvestDate;
    private double quantityKg;
    private Grade grade;
    private int cropId;
    private String txHash;

    // Full constructor (loading from DB)
    public HarvestRecord(int recordId, LocalDate harvestDate, double quantityKg, Grade grade, int cropId, String txHash) {
        this.recordId = recordId;
        this.harvestDate = harvestDate;
        this.quantityKg = quantityKg;
        this.grade = grade;
        this.cropId = cropId;
        this.txHash = txHash;
    }

    // Without recordId (creating new)
    public HarvestRecord(LocalDate harvestDate, double quantityKg, Grade grade, int cropId) {
        this.recordId = -1;
        this.harvestDate = harvestDate;
        this.quantityKg = quantityKg;
        this.grade = grade;
        this.cropId = cropId;
    }

    public int getRecordId() { return recordId; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public double getQuantityKg() { return quantityKg; }
    public Grade getGrade() { return grade; }
    public int getCropId() { return cropId; }

    public void setRecordId(int recordId) { this.recordId = recordId; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }
    public void setQuantityKg(double quantityKg) { this.quantityKg = quantityKg; }
    public void setGrade(Grade grade) { this.grade = grade; }
    public void setCropId(int cropId) { this.cropId = cropId; }

    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }
}

