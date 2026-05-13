package smartfarm.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import smartfarm.model.DiseaseDetection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * Service for crop disease detection using the Crop.health API (by Kindwise).
 *
 * API docs: https://crop.kindwise.com/docs
 * Dashboard: https://admin.kindwise.com/
 *
 * Flow:
 *   1. Place your API key in src/main/resources/crop-health.properties
 *   2. Call analyzeImage(imagePath) → returns disease suggestions
 *   3. Use toDiseaseDetection() to convert to the DiseaseDetection model
 */
public class PlantIdService {

    // ─── Configuration ─────────────────────────────────────────────────
    private static final String API_BASE_URL = "https://crop.kindwise.com/api/v1";
    private static final String PROPS_FILE   = "crop-health.properties";
    private static final int    TIMEOUT_SECS = 30;

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    // ─── Singleton ─────────────────────────────────────────────────────
    private static PlantIdService instance;

    public static synchronized PlantIdService getInstance() {
        if (instance == null) {
            instance = new PlantIdService();
        }
        return instance;
    }

    private PlantIdService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECS))
                .build();
        this.gson = new Gson();
        this.apiKey = loadApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[CropHealth] API key not found! " +
                    "Set it in src/main/resources/" + PROPS_FILE);
        } else {
            System.out.println("[CropHealth] API key loaded. Service ready.");
        }
    }

    // ─── Public API ────────────────────────────────────────────────────

    /**
     * Returns true if the API key is configured.
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Analyze a crop image for diseases using the Crop.health API.
     *
     * @param imagePath absolute path to the image file (JPG/PNG, max 1MB)
     * @return parsed result with disease info, or null on failure
     */
    public CropHealthResult analyzeImage(String imagePath) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "API key not configured. Set it in src/main/resources/" + PROPS_FILE);
        }

        Path imgPath = Paths.get(imagePath);
        if (!Files.exists(imgPath)) {
            throw new IllegalArgumentException("Image file not found: " + imagePath);
        }

        try {
            // ── Step 1: Read and encode the image as base64 ─────────
            byte[] imageBytes = Files.readAllBytes(imgPath);
            String mimeType = Files.probeContentType(imgPath);
            if (mimeType == null) mimeType = "image/jpeg";
            String base64Image = "data:" + mimeType + ";base64,"
                    + Base64.getEncoder().encodeToString(imageBytes);

            System.out.println("[CropHealth] Analyzing image: " + imagePath
                    + " (" + (imageBytes.length / 1024) + " KB)");

            // ── Step 2: Build the JSON request body ─────────────────
            JsonObject body = new JsonObject();
            JsonArray images = new JsonArray();
            images.add(base64Image);
            body.add("images", images);
            body.addProperty("similar_images", true);

            // ── Step 3: POST to the API ─────────────────────────────
            String url = API_BASE_URL + "/identification"
                    + "?details=description,treatment,common_names,severity";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Api-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECS))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("[CropHealth] Response status: " + response.statusCode());

            if (response.statusCode() != 201 && response.statusCode() != 200) {
                System.err.println("[CropHealth] API error: " + response.body());
                return null;
            }

            // ── Step 4: Parse the response ──────────────────────────
            return parseResponse(response.body());

        } catch (IOException | InterruptedException e) {
            System.err.println("[CropHealth] Request failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check remaining API credits.
     */
    public void printUsageInfo() {
        if (!isAvailable()) return;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/usage_info"))
                    .header("Api-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            boolean active = json.get("active").getAsBoolean();
            boolean canUse = json.getAsJsonObject("can_use_credits")
                    .get("value").getAsBoolean();
            JsonObject used = json.getAsJsonObject("used");

            System.out.println("[CropHealth] API Key active: " + active);
            System.out.println("[CropHealth] Can use credits: " + canUse);
            System.out.println("[CropHealth] Used today: " + used.get("day"));
            System.out.println("[CropHealth] Used total: " + used.get("total"));
        } catch (Exception e) {
            System.err.println("[CropHealth] Failed to get usage info: " + e.getMessage());
        }
    }

    // ─── Response Parsing ──────────────────────────────────────────────

    private CropHealthResult parseResponse(String responseBody) {
        CropHealthResult result = new CropHealthResult();
        result.rawJson = responseBody;

        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            result.accessToken = json.has("access_token")
                    ? json.get("access_token").getAsString() : null;

            JsonObject resultObj = json.getAsJsonObject("result");
            if (resultObj == null) return result;

            // ── Is it a plant? ──────────────────────────────────────
            if (resultObj.has("is_plant")) {
                result.isPlant = resultObj.getAsJsonObject("is_plant")
                        .get("binary").getAsBoolean();
            }

            // ── Disease suggestions ─────────────────────────────────
            if (resultObj.has("disease")) {
                JsonArray suggestions = resultObj.getAsJsonObject("disease")
                        .getAsJsonArray("suggestions");

                for (JsonElement elem : suggestions) {
                    JsonObject s = elem.getAsJsonObject();
                    DiseaseSuggestion ds = new DiseaseSuggestion();
                    ds.name = s.get("name").getAsString();
                    ds.scientificName = s.get("scientific_name").getAsString();
                    ds.probability = s.get("probability").getAsDouble();

                    // Parse details if present
                    if (s.has("details") && !s.get("details").isJsonNull()) {
                        JsonObject details = s.getAsJsonObject("details");
                        if (details.has("description") && !details.get("description").isJsonNull())
                            ds.description = details.get("description").getAsString();
                        if (details.has("severity") && !details.get("severity").isJsonNull())
                            ds.severity = details.get("severity").getAsString();
                        if (details.has("treatment") && !details.get("treatment").isJsonNull()) {
                            JsonObject treatment = details.getAsJsonObject("treatment");
                            ds.treatment = parseTreatment(treatment);
                        }
                    }

                    result.diseases.add(ds);
                }
            }

            // ── Crop suggestions ────────────────────────────────────
            if (resultObj.has("crop")) {
                JsonArray suggestions = resultObj.getAsJsonObject("crop")
                        .getAsJsonArray("suggestions");

                if (suggestions.size() > 0) {
                    JsonObject top = suggestions.get(0).getAsJsonObject();
                    result.cropName = top.get("name").getAsString();
                    result.cropScientificName = top.get("scientific_name").getAsString();
                    result.cropProbability = top.get("probability").getAsDouble();
                }
            }

            // ── Determine health status ─────────────────────────────
            if (!result.diseases.isEmpty()) {
                DiseaseSuggestion top = result.diseases.get(0);
                result.isHealthy = top.name.equalsIgnoreCase("healthy");
            }

            System.out.println("[CropHealth] Result — Crop: " + result.cropName
                    + " | Disease: " + (result.diseases.isEmpty() ? "none" : result.diseases.get(0))
                    + " | Healthy: " + result.isHealthy);

        } catch (Exception e) {
            System.err.println("[CropHealth] Error parsing response: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private String parseTreatment(JsonObject treatment) {
        StringBuilder sb = new StringBuilder();
        if (treatment.has("prevention") && !treatment.get("prevention").isJsonNull()) {
            sb.append("Prevention: ");
            appendList(sb, treatment.getAsJsonArray("prevention"));
        }
        if (treatment.has("biological") && !treatment.get("biological").isJsonNull()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Biological: ");
            appendList(sb, treatment.getAsJsonArray("biological"));
        }
        if (treatment.has("chemical") && !treatment.get("chemical").isJsonNull()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Chemical: ");
            appendList(sb, treatment.getAsJsonArray("chemical"));
        }
        return sb.toString();
    }

    private void appendList(StringBuilder sb, JsonArray arr) {
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(arr.get(i).getAsString());
        }
    }

    // ─── API Key Loading ───────────────────────────────────────────────

    private String loadApiKey() {
        // Try properties file on classpath
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(PROPS_FILE)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String key = props.getProperty("api.key", "").trim();
                if (!key.isBlank()) return key;
            }
        } catch (IOException e) {
            System.err.println("[CropHealth] Error reading " + PROPS_FILE + ": " + e.getMessage());
        }

        // Fallback: environment variable
        String envKey = System.getenv("CROP_HEALTH_API_KEY");
        if (envKey != null && !envKey.isBlank()) return envKey;

        return null;
    }

    // ─── Result Models ─────────────────────────────────────────────────

    /**
     * A single disease/pest suggestion from the API.
     */
    public static class DiseaseSuggestion {
        public String name;
        public String scientificName;
        public double probability;
        public String description;
        public String severity;
        public String treatment;

        @Override
        public String toString() {
            return name + " (" + String.format("%.1f%%", probability * 100) + ")";
        }
    }

    /**
     * Full result from a Crop.health identification request.
     */
    public static class CropHealthResult {
        public boolean isPlant = true;
        public boolean isHealthy = true;
        public String accessToken;
        public String cropName;
        public String cropScientificName;
        public double cropProbability;
        public List<DiseaseSuggestion> diseases = new ArrayList<>();
        public String rawJson;

        /**
         * Returns the top disease suggestion, or null if healthy / no results.
         */
        public DiseaseSuggestion getTopDisease() {
            if (diseases.isEmpty()) return null;
            DiseaseSuggestion top = diseases.get(0);
            return top.name.equalsIgnoreCase("healthy") ? null : top;
        }

        /**
         * Converts this result into a DiseaseDetection model object.
         */
        public DiseaseDetection toDiseaseDetection(int plotId, int cropId,
                                                    String imagePath, int workerId) {
            DiseaseSuggestion top = getTopDisease();

            if (top == null) {
                return new DiseaseDetection(plotId, cropId, imagePath, "Healthy",
                        DiseaseDetection.Confidence.HIGH, "Plant appears healthy.", workerId);
            }

            DiseaseDetection.Confidence confidence;
            if (top.probability >= 0.8) {
                confidence = DiseaseDetection.Confidence.HIGH;
            } else if (top.probability >= 0.5) {
                confidence = DiseaseDetection.Confidence.MEDIUM;
            } else {
                confidence = DiseaseDetection.Confidence.LOW;
            }

            String recommendation = top.treatment != null ? top.treatment
                    : (top.description != null ? top.description : "No recommendation available.");

            return new DiseaseDetection(plotId, cropId, imagePath, top.name,
                    confidence, recommendation, workerId);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("CropHealthResult{");
            sb.append("crop='").append(cropName).append("'");
            sb.append(", healthy=").append(isHealthy);
            for (DiseaseSuggestion d : diseases) {
                sb.append("\n  ").append(d.name)
                  .append(" (").append(String.format("%.1f%%", d.probability * 100)).append(")")
                  .append(d.description != null ? " — " + d.description : "");
            }
            sb.append("\n}");
            return sb.toString();
        }
    }

    // ─── Quick Launch ──────────────────────────────────────────────────
    /**
     * Run from IntelliJ: right-click → Run 'PlantIdService.main()'.
     * Pass an image path to analyze, or "usage" to check credits.
     */
    public static void main(String[] args) {
        PlantIdService service = PlantIdService.getInstance();

        if (args.length == 0 || args[0].equalsIgnoreCase("usage")) {
            service.printUsageInfo();
        } else {
            CropHealthResult result = service.analyzeImage(args[0]);
            if (result != null) {
                System.out.println(result);
            }
        }
    }
}
