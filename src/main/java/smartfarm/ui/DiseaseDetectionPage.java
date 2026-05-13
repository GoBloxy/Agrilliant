package smartfarm.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import smartfarm.service.PlantIdService;
import smartfarm.service.PlantIdService.CropHealthResult;
import smartfarm.service.PlantIdService.DiseaseSuggestion;

import java.io.File;

public class DiseaseDetectionPage extends VBox {

    // ─── UI Components ─────────────────────────────────────────────────
    private File selectedFile;
    private final ImageView imagePreview = new ImageView();
    private VBox placeholder;
    private final Label lblFileName = new Label("No file selected");
    private final Button btnAnalyze = new Button("Analyze");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final VBox resultsContainer = new VBox(16);
    private final Label lblStatus = new Label();

    // ─── Result labels ─────────────────────────────────────────────────
    private final Label lblCropName = new Label("—");
    private final Label lblCropScientific = new Label("");
    private final Label lblCropProb = new Label("");
    private final Label lblDiseaseName = new Label("—");
    private final Label lblDiseaseScientific = new Label("");
    private final Label lblDiseaseProb = new Label("");
    private final Label lblHealthy = new Label("—");
    private final Label lblSeverity = new Label("—");
    private final Label lblDescription = new Label("");
    private final Label lblTreatment = new Label("");

    public DiseaseDetectionPage() {
        setSpacing(20);
        setPadding(new Insets(20, 24, 24, 24));
        setStyle("-fx-background-color: #f5f7fa;");

        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);
        btnAnalyze.setDisable(true);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox content = new VBox(20);
        content.getChildren().addAll(
                buildHeader(),
                buildUploadSection(),
                resultsContainer
        );
        scrollPane.setContent(content);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);

        resultsContainer.setVisible(false);
        resultsContainer.setManaged(false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HEADER
    // ═══════════════════════════════════════════════════════════════════
    private HBox buildHeader() {
        Label title = new Label("Disease Detection");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #111827;");

        Label subtitle = new Label("Upload a crop photo to identify diseases using AI");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13;");

        VBox left = new VBox(4, title, subtitle);

        // Credits info
        Button btnUsage = new Button("Check Credits");
        btnUsage.setStyle("-fx-font-size: 12; -fx-padding: 6 14; -fx-background-radius: 6; " +
                "-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-cursor: hand;");
        btnUsage.setGraphic(createIcon("fth-info", 14, "#6b7280"));
        btnUsage.setOnAction(e -> checkCredits());

        HBox header = new HBox(left, new Region(), btnUsage);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UPLOAD SECTION
    // ═══════════════════════════════════════════════════════════════════
    private VBox buildUploadSection() {
        // Image preview area
        imagePreview.setFitWidth(320);
        imagePreview.setFitHeight(240);
        imagePreview.setPreserveRatio(true);
        imagePreview.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);");

        StackPane previewPane = new StackPane(imagePreview);
        previewPane.setPrefSize(340, 250);
        previewPane.setMaxSize(340, 250);
        previewPane.setStyle("-fx-background-color: #f9fafb; -fx-background-radius: 12; " +
                "-fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-border-style: dashed; -fx-border-width: 2;");

        // Placeholder icon when no image
        placeholder = new VBox(8);
        placeholder.setAlignment(Pos.CENTER);
        FontIcon camIcon = createIcon("fth-camera", 40, "#d1d5db");
        Label placeholderText = new Label("Click to select an image");
        placeholderText.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13;");
        placeholder.getChildren().addAll(camIcon, placeholderText);
        previewPane.getChildren().add(0, placeholder);
        previewPane.setOnMouseClicked(e -> selectImage());
        previewPane.setStyle(previewPane.getStyle() + "-fx-cursor: hand;");

        // File info row
        lblFileName.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12;");

        Button btnBrowse = new Button("Browse Image");
        btnBrowse.setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-radius: 6; " +
                "-fx-background-radius: 6; -fx-font-size: 13; -fx-padding: 8 18; -fx-cursor: hand;");
        btnBrowse.setGraphic(createIcon("fth-upload", 14, "#374151"));
        btnBrowse.setOnAction(e -> selectImage());

        // Analyze button
        btnAnalyze.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-size: 14; " +
                "-fx-font-weight: bold; -fx-padding: 10 32; -fx-background-radius: 8; -fx-cursor: hand;");
        btnAnalyze.setGraphic(createIcon("fth-search", 16, "white"));
        btnAnalyze.setOnAction(e -> runAnalysis());

        // Status
        lblStatus.setStyle("-fx-font-size: 12; -fx-text-fill: #6b7280;");

        HBox actionRow = new HBox(12, btnBrowse, btnAnalyze, spinner, lblStatus);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox uploadCard = new VBox(16, previewPane, lblFileName, actionRow);
        uploadCard.setPadding(new Insets(24));
        uploadCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");
        uploadCard.setAlignment(Pos.CENTER_LEFT);

        return uploadCard;
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESULTS SECTION
    // ═══════════════════════════════════════════════════════════════════
    private void buildResultsUI(CropHealthResult result) {
        resultsContainer.getChildren().clear();

        Label resultsTitle = new Label("Analysis Results");
        resultsTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        resultsTitle.setStyle("-fx-text-fill: #111827;");

        HBox cards = new HBox(16);

        // ── Crop Identified Card ────────────────────────────────────
        VBox cropCard = buildResultCard("Crop Identified", "fth-feather", "#2e7d32", "#e8f5e9");
        styleResultLabel(lblCropName, 18, true, "#111827");
        styleResultLabel(lblCropScientific, 12, false, "#6b7280");
        styleResultLabel(lblCropProb, 12, false, "#9ca3af");
        cropCard.getChildren().addAll(lblCropName, lblCropScientific, lblCropProb);

        // ── Disease Card ────────────────────────────────────────────
        String diseaseColor = result.isHealthy ? "#2e7d32" : "#dc2626";
        String diseaseBg = result.isHealthy ? "#e8f5e9" : "#fee2e2";
        VBox diseaseCard = buildResultCard("Disease Status", "fth-alert-circle", diseaseColor, diseaseBg);
        styleResultLabel(lblDiseaseName, 18, true, diseaseColor);
        styleResultLabel(lblDiseaseScientific, 12, false, "#6b7280");
        styleResultLabel(lblDiseaseProb, 12, true, "#374151");
        cropCard.getChildren().size(); // no-op, just for clarity
        diseaseCard.getChildren().addAll(lblDiseaseName, lblDiseaseScientific, lblDiseaseProb);

        // ── Health & Severity Card ──────────────────────────────────
        VBox healthCard = buildResultCard("Health Summary", "fth-heart", "#6366f1", "#eef2ff");
        styleResultLabel(lblHealthy, 16, true, result.isHealthy ? "#16a34a" : "#dc2626");
        styleResultLabel(lblSeverity, 13, false, "#374151");
        healthCard.getChildren().addAll(lblHealthy, lblSeverity);

        cards.getChildren().addAll(cropCard, diseaseCard, healthCard);
        HBox.setHgrow(cropCard, Priority.ALWAYS);
        HBox.setHgrow(diseaseCard, Priority.ALWAYS);
        HBox.setHgrow(healthCard, Priority.ALWAYS);

        // ── Description & Treatment Card ────────────────────────────
        VBox detailsCard = new VBox(14);
        detailsCard.setPadding(new Insets(20));
        detailsCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");

        if (lblDescription.getText() != null && !lblDescription.getText().isBlank()) {
            Label descTitle = new Label("Description");
            descTitle.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
            descTitle.setStyle("-fx-text-fill: #111827;");
            lblDescription.setWrapText(true);
            lblDescription.setStyle("-fx-text-fill: #374151; -fx-font-size: 13;");
            detailsCard.getChildren().addAll(descTitle, lblDescription);
        }

        if (lblTreatment.getText() != null && !lblTreatment.getText().isBlank()) {
            Label treatTitle = new Label("Treatment");
            treatTitle.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
            treatTitle.setStyle("-fx-text-fill: #111827;");
            lblTreatment.setWrapText(true);
            lblTreatment.setStyle("-fx-text-fill: #374151; -fx-font-size: 13; -fx-line-spacing: 4;");

            VBox treatBox = new VBox(6, treatTitle, lblTreatment);
            treatBox.setPadding(new Insets(12));
            treatBox.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 8;");
            detailsCard.getChildren().add(treatBox);
        }

        resultsContainer.getChildren().addAll(resultsTitle, cards);
        if (!detailsCard.getChildren().isEmpty()) {
            resultsContainer.getChildren().add(detailsCard);
        }
        resultsContainer.setVisible(true);
        resultsContainer.setManaged(true);
    }

    private VBox buildResultCard(String title, String icon, String iconColor, String bgColor) {
        StackPane iconPane = new StackPane(createIcon(icon, 20, iconColor));
        iconPane.setPrefSize(42, 42);
        iconPane.setMaxSize(42, 42);
        iconPane.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10;");

        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-size: 12; -fx-text-fill: #6b7280;");

        HBox header = new HBox(10, iconPane, lbl);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, header);
        card.setMinWidth(200);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");
        return card;
    }

    private void styleResultLabel(Label lbl, int size, boolean bold, String color) {
        String weight = bold ? "-fx-font-weight: bold; " : "";
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-size: " + size + "; " + weight + "-fx-text-fill: " + color + ";");
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════════
    private void selectImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Crop Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            selectedFile = file;
            lblFileName.setText(file.getName() + "  (" + (file.length() / 1024) + " KB)");
            imagePreview.setImage(new Image(file.toURI().toString()));
            placeholder.setVisible(false);
            placeholder.setManaged(false);
            btnAnalyze.setDisable(false);
            resultsContainer.setVisible(false);
            resultsContainer.setManaged(false);
        }
    }

    private void runAnalysis() {
        if (selectedFile == null) return;

        PlantIdService service = PlantIdService.getInstance();
        if (!service.isAvailable()) {
            showError("API key not configured. See src/main/resources/crop-health.properties");
            return;
        }

        btnAnalyze.setDisable(true);
        spinner.setVisible(true);
        lblStatus.setText("Analyzing...");
        lblStatus.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 12;");

        Task<CropHealthResult> task = new Task<>() {
            @Override
            protected CropHealthResult call() {
                return service.analyzeImage(selectedFile.getAbsolutePath());
            }
        };

        task.setOnSucceeded(e -> {
            spinner.setVisible(false);
            CropHealthResult result = task.getValue();

            if (result == null) {
                lblStatus.setText("Analysis failed. Check console for details.");
                lblStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12;");
                btnAnalyze.setDisable(false);
                return;
            }

            lblStatus.setText("Done — 1 credit used");
            lblStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12;");
            btnAnalyze.setDisable(false);
            populateResults(result);
        });

        task.setOnFailed(e -> {
            spinner.setVisible(false);
            lblStatus.setText("Error: " + task.getException().getMessage());
            lblStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12;");
            btnAnalyze.setDisable(false);
        });

        new Thread(task, "crop-health-api").start();
    }

    private void populateResults(CropHealthResult result) {
        // Crop info
        lblCropName.setText(result.cropName != null ? result.cropName : "Unknown");
        lblCropScientific.setText(result.cropScientificName != null ? result.cropScientificName : "");
        lblCropProb.setText(result.cropProbability > 0
                ? String.format("%.1f%% confidence", result.cropProbability * 100) : "");

        // Disease info
        DiseaseSuggestion top = result.getTopDisease();
        if (top != null) {
            lblDiseaseName.setText(top.name);
            lblDiseaseScientific.setText(top.scientificName != null ? top.scientificName : "");
            lblDiseaseProb.setText(String.format("%.1f%% probability", top.probability * 100));
            lblHealthy.setText("Unhealthy");
            lblSeverity.setText(top.severity != null ? "Severity: " + top.severity : "");
            lblDescription.setText(top.description != null ? top.description : "");
            lblTreatment.setText(top.treatment != null ? top.treatment : "");
        } else {
            lblDiseaseName.setText("Healthy");
            lblDiseaseScientific.setText("");
            lblDiseaseProb.setText("No diseases detected");
            lblHealthy.setText("Healthy");
            lblSeverity.setText("");
            lblDescription.setText("The plant appears to be healthy with no visible signs of disease.");
            lblTreatment.setText("");
        }

        buildResultsUI(result);
    }

    private void checkCredits() {
        PlantIdService service = PlantIdService.getInstance();
        if (!service.isAvailable()) {
            showError("API key not configured.");
            return;
        }

        lblStatus.setText("Checking credits...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                service.printUsageInfo();
                return null;
            }
        };
        task.setOnSucceeded(e -> lblStatus.setText("Credits info printed to console."));
        new Thread(task, "crop-health-usage").start();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Disease Detection Error");
        alert.showAndWait();
    }

    private FontIcon createIcon(String literal, int size, String color) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(size);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        return icon;
    }
}
