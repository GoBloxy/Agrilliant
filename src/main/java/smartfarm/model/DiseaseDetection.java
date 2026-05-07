package smartfarm.model;

import java.time.LocalDateTime;

/**
 * TODO: DiseaseDetection stores results from a computer vision model that identifies plant diseases.
 *
 * HOW TO INTEGRATE A CV MODEL:
 * Option A — External API (recommended for starting):
 *   1. Use a pre-trained model like PlantVillage (TensorFlow/PyTorch) or Roboflow hosted model.
 *   2. Deploy it as a REST API using: Flask/FastAPI (Python) on a cloud server (Railway, Render, AWS Lambda).
 *   3. From Java, call the API via HttpURLConnection or OkHttp: POST image → receive JSON with disease name + confidence.
 *   4. Store the result in this model.
 *
 * Option B — On-device (for drone):
 *   1. Run a lightweight model (YOLOv8, MobileNet) on a Raspberry Pi attached to the drone.
 *   2. Drone captures image → runs inference locally → sends result to backend via MQTT/HTTP.
 *
 * Option C — Hugging Face Inference API (easiest, free tier):
 *   1. Use models like "linkanjarad/mobilenet_v2_1.0_224-plant-disease-identification"
 *   2. POST image to https://api-inference.huggingface.co/models/{model_id}
 *   3. Parse the JSON response for label + score.
 *
 * Links to: Crop (which crop), Plot (where), Drone (if captured by drone, nullable).
 * Can auto-generate an Alert if confidence is above threshold.
 */
public class DiseaseDetection {
    public enum DetectionSource { DRONE_CAMERA, WORKER_UPLOAD, IOT_CAMERA }

    private int detectionId;
    private int cropId;
    private int plotId;
    private Integer droneId;          // nullable — only if captured by drone
    private String diseaseName;       // e.g. "Late Blight", "Powdery Mildew"
    private double confidencePercent; // model confidence 0-100
    private String imagePath;         // path/URL to the captured image
    private DetectionSource source;
    private LocalDateTime detectedAt;
    private boolean alertGenerated;   // whether an alert was auto-created
    private String modelVersion;      // track which model version produced this

    // TODO: Implement constructors, getters, setters
    // TODO: Implement DAO (DiseaseDetectionDAO)
    // TODO: Implement Service (DiseaseDetectionService):
    //       - receiveDetection(image, cropId, plotId) → call CV API → store result
    //       - if confidence > threshold → auto-create Alert via AlertService
    // TODO: Create a utility class (CvApiClient) that handles HTTP calls to the model endpoint
}
