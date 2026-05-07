package smartfarm.model;

public class DiseaseDetection {
    public enum Confidence { HIGH, MEDIUM, LOW }
    public enum Status { PENDING, CONFIRMED, DISMISSED }

    private int detectionId;
    private int plotId;
    private int cropId;
    private String imagePath;
    private String diseaseName;
    private Confidence confidence;
    private String recommendation;
    private Status status;
    private int detectedByWorkerId;

    public DiseaseDetection(int detectionId, int plotId, int cropId, String imagePath, String diseaseName,
                            Confidence confidence, String recommendation, Status status, int detectedByWorkerId) {
        this.detectionId = detectionId;
        this.plotId = plotId;
        this.cropId = cropId;
        this.imagePath = imagePath;
        this.diseaseName = diseaseName;
        this.confidence = confidence;
        this.recommendation = recommendation;
        this.status = status;
        this.detectedByWorkerId = detectedByWorkerId;
    }

    public DiseaseDetection(int plotId, int cropId, String imagePath, String diseaseName,
                            Confidence confidence, String recommendation, int detectedByWorkerId) {
        this.detectionId = -1;
        this.plotId = plotId;
        this.cropId = cropId;
        this.imagePath = imagePath;
        this.diseaseName = diseaseName;
        this.confidence = confidence;
        this.recommendation = recommendation;
        this.status = Status.PENDING;
        this.detectedByWorkerId = detectedByWorkerId;
    }

    public int getDetectionId() { return detectionId; }
    public void setDetectionId(int detectionId) { this.detectionId = detectionId; }
    public int getPlotId() { return plotId; }
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public int getCropId() { return cropId; }
    public void setCropId(int cropId) { this.cropId = cropId; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }
    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getDetectedByWorkerId() { return detectedByWorkerId; }
    public void setDetectedByWorkerId(int detectedByWorkerId) { this.detectedByWorkerId = detectedByWorkerId; }
}
