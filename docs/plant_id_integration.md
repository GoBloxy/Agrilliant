# Crop Health — Disease Detection Integration

## Overview

Agrilliant uses the [Crop.health API](https://crop.kindwise.com/) by Kindwise for **crop disease detection**.
The service sends a base64-encoded image via REST API and receives structured JSON with disease/crop
identification, probabilities, descriptions, and treatment recommendations.

- **API docs**: https://crop.kindwise.com/docs
- **Postman collection**: https://documenter.getpostman.com/view/3802128/2sA2xh1CXy
- **Admin dashboard**: https://admin.kindwise.com/

## Architecture

```
User uploads image in app
        │
        ▼
PlantIdService.analyzeImage(imagePath)
        │
        ├── Read image file → encode as base64
        ├── POST to https://crop.kindwise.com/api/v1/identification
        │     Headers: Api-Key, Content-Type: application/json
        │     Body: { "images": ["data:image/jpeg;base64,..."], "similar_images": true }
        │     Params: ?details=description,treatment,common_names,severity
        ├── Parse JSON response
        │     ├── result.disease.suggestions[] → disease name, probability, treatment
        │     ├── result.crop.suggestions[]    → crop name, scientific name
        │     └── result.is_plant              → plant detection confidence
        └── Return CropHealthResult → convert to DiseaseDetection model
```

## Setup

### Step 1 — Get an API Key

1. Go to https://admin.kindwise.com/
2. Create an account and generate an API key for the **crop.health** system
3. Note the API key (format: long alphanumeric string)

### Step 2 — Configure the API Key

Create or edit `src/main/resources/crop-health.properties`:

```properties
api.key=YOUR_API_KEY_HERE
```

> This file is **gitignored** — never commit your API key.

Alternatively, set the environment variable `CROP_HEALTH_API_KEY`.

### Step 3 — Maven Dependencies

The service uses Java `HttpClient` (built-in) and Gson for JSON parsing.
Both are already configured in `pom.xml`. No extra setup needed.

## Usage in Code

### Analyze an Image

```java
PlantIdService service = PlantIdService.getInstance();

if (service.isAvailable()) {
    PlantIdService.CropHealthResult result = service.analyzeImage("C:/photos/sick_leaf.jpg");

    if (result != null) {
        System.out.println("Crop: " + result.cropName);
        System.out.println("Healthy: " + result.isHealthy);

        // Get the top disease (null if healthy)
        PlantIdService.DiseaseSuggestion disease = result.getTopDisease();
        if (disease != null) {
            System.out.println("Disease: " + disease.name + " (" + disease.probability + ")");
            System.out.println("Treatment: " + disease.treatment);
        }

        // Convert to DiseaseDetection model for database storage
        DiseaseDetection detection = result.toDiseaseDetection(plotId, cropId, imagePath, workerId);
    }
}
```

### Check API Credits

```java
PlantIdService.getInstance().printUsageInfo();
```

Output:
```
[CropHealth] API Key active: true
[CropHealth] Can use credits: true
[CropHealth] Used today: 3
[CropHealth] Used total: 7
```

### Quick Test from IntelliJ

Run `PlantIdService.main()`:
- **No args** or `"usage"` → prints credit usage
- **Image path as arg** → runs disease detection and prints results

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/identification` | Create identification (send images) |
| `GET` | `/identification/:token` | Retrieve past identification |
| `DELETE` | `/identification/:token` | Delete identification |
| `GET` | `/usage_info` | Check API key credits & limits |
| `POST` | `/identification/:token/feedback` | Send user feedback |

## Response Structure

The API returns disease and crop suggestions ranked by probability:

```json
{
  "result": {
    "is_plant": { "binary": true, "probability": 1.0 },
    "disease": {
      "suggestions": [
        { "name": "late blight", "probability": 0.98, "scientific_name": "Phytophthora infestans" },
        { "name": "healthy", "probability": 0.01 }
      ]
    },
    "crop": {
      "suggestions": [
        { "name": "potato", "probability": 0.87, "scientific_name": "Solanum tuberosum" }
      ]
    }
  }
}
```

Available **details** (requested via query params): `description`, `treatment`, `common_names`,
`severity`, `spreading`, `symptoms`, `type`, `wiki_url`, `wiki_description`, `eppo_code`, `taxonomy`.

## Credits

- Each identification uses **1 credit**
- Monitor credits via `printUsageInfo()` or the admin dashboard
- Current allocation: **50 credits**

## File Reference

| File | Purpose |
|------|---------|
| `src/.../resources/crop-health.properties` | API key (gitignored) |
| `src/.../service/PlantIdService.java` | Crop.health API service |
| `src/.../model/DiseaseDetection.java` | Data model for detection results |
| `sql/schema.sql` | `disease_detection` table definition |

## Security Notes

- **`crop-health.properties` is gitignored** — never commit your API key
- The API key should be kept on your backend; do not embed it in client-side code
- Use environment variable `CROP_HEALTH_API_KEY` as an alternative to the properties file
