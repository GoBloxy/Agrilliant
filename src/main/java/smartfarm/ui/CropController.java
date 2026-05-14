package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.CropDAO;
import smartfarm.dao.PlotDAO;
import smartfarm.model.Crop;
import smartfarm.model.Plot;
import smartfarm.model.SensorReading;
import smartfarm.service.LiveSensorData;
import smartfarm.util.CSVExporter;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CropController {

    // ── List View ──
    @FXML private VBox listView;
    @FXML private Label lblTotalCrops, lblGrowing, lblReady, lblAtRisk, lblPagination;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbPlot, cmbStage;
    @FXML private TableView<Crop> cropTable;
    @FXML private TableColumn<Crop, String> colCropName, colPlot, colPlantedDate, colHarvestDate, colStatus, colActions;
    @FXML private Button btnAddCrop, btnExport;

    // ── Detail View ──
    @FXML private VBox detailView;
    @FXML private HBox tabBar;
    @FXML private StackPane tabContent;
    @FXML private Button tabOverview, tabTimeline, tabHistory, tabPhotos, tabNotes;
    @FXML private Button btnAdvanceStage;

    // Tab panes
    @FXML private VBox overviewPane, timelinePane, historyPane, photosPane, notesPane;
    @FXML private VBox timelineEntries, careEntries;
    @FXML private TextArea txtNotes;

    // Detail labels
    @FXML private Label lblDetailName, lblDetailPlot, lblDetailStatus, lblDetailPlanted;
    @FXML private Label lblDetailArea, lblDetailDays, lblDetailStage, lblDetailHarvest;
    @FXML private Label lblProgressPercent, lblTargetProgress, lblExpectedProgress, lblBehindSchedule;
    @FXML private Label lblNextMilestone, lblMilestoneEta;
    @FXML private Label lblHealthOverall, lblHealthLeaf, lblHealthGrowth, lblHealthPest, lblHealthDisease;
    @FXML private Circle progressArc;

    // Yield
    @FXML private Label lblYieldEstimate;
    @FXML private StackPane yieldBar;

    // Stage Timeline
    @FXML private HBox stageTimeline, stageLabels;
    @FXML private Label lblElapsedDays, lblRemainingDays, lblTotalLifecycle;
    @FXML private ProgressBar lifecycleBar;

    // Environmental Conditions
    @FXML private Label lblConditionsSource;
    @FXML private Label lblEnvTemp, lblEnvTempStatus;
    @FXML private Label lblEnvHumidity, lblEnvHumStatus;
    @FXML private Label lblEnvSoil, lblEnvSoilStatus;

    private final CropDAO cropDAO = new CropDAO();
    private final PlotDAO plotDAO = new PlotDAO();
    private final ObservableList<Crop> allCrops = FXCollections.observableArrayList();
    private final Map<Integer, Plot> plotCache = new HashMap<>();
    private Crop selectedCrop;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    @FXML
    public void initialize() {
        setupTableColumns();
        loadCrops();
        setupFilters();
        updateSummaryCards();
        loadPlotCache();
    }

    private void loadPlotCache() {
        try {
            for (Plot p : plotDAO.getAll()) plotCache.put(p.getPlotId(), p);
        } catch (SQLException ignored) {}
    }

    // ═══════════ TABLE SETUP ═══════════

    private void setupTableColumns() {
        cropTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colCropName.setResizable(false);
        colPlot.setResizable(false);
        colPlantedDate.setResizable(false);
        colHarvestDate.setResizable(false);
        colStatus.setResizable(false);
        colActions.setResizable(false);

        colCropName.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getCropName()));
        colPlot.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(getPlotLabel(data.getValue().getPlotId())));
        colPlantedDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getPlantingDate() != null ? data.getValue().getPlantingDate().format(DATE_FMT) : "—"));
        colHarvestDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getHarvestDate() != null ? data.getValue().getHarvestDate().format(DATE_FMT) : "—"));
        colStatus.setCellValueFactory(data -> {
            Crop c = data.getValue();
            return new javafx.beans.property.SimpleStringProperty(getStatusLabel(c));
        });
        colStatus.setCellFactory(col -> new TableCell<Crop, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    switch (item) {
                        case "Growing"   -> badge.setStyle("-fx-background-color:#d1fae5;-fx-text-fill:#065f46;");
                        case "Ready"     -> badge.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#92400e;");
                        case "At Risk"   -> badge.setStyle("-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;");
                        case "Harvested" -> badge.setStyle("-fx-background-color:#e0e7ff;-fx-text-fill:#3730a3;");
                    }
                    badge.setStyle(badge.getStyle() + "-fx-padding:2 8;-fx-background-radius:10;-fx-font-size:11;-fx-font-weight:bold;");
                    setGraphic(badge);
                }
            }
        });

        colActions.setCellFactory(col -> new TableCell<Crop, String>() {
            private final Button viewBtn = new Button("", new FontIcon("fth-eye"));
            private final Button editBtn = new Button("", new FontIcon("fth-edit-2"));
            private final Button delBtn  = new Button("", new FontIcon("fth-trash-2"));
            private final HBox box = new HBox(6, viewBtn, editBtn, delBtn);
            {
                viewBtn.getStyleClass().add("icon-btn");
                editBtn.getStyleClass().add("icon-btn");
                delBtn.getStyleClass().add("icon-btn");
                viewBtn.setOnAction(e -> {
                    Crop c = getTableRow() != null ? getTableRow().getItem() : null;
                    if (c != null) showCropDetail(c);
                });
                editBtn.setOnAction(e -> {
                    Crop c = getTableRow() != null ? getTableRow().getItem() : null;
                    if (c != null) onEditCrop(c);
                });
                delBtn.setOnAction(e -> {
                    Crop c = getTableRow() != null ? getTableRow().getItem() : null;
                    if (c != null) onDeleteCrop(c);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Double-click to open details
        cropTable.setRowFactory(tv -> {
            TableRow<Crop> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showCropDetail(row.getItem());
                }
            });
            return row;
        });
    }

    // ═══════════ VIEW SWITCHING ═══════════

    private void showCropDetail(Crop crop) {
        selectedCrop = crop;
        populateDetail(crop);
        listView.setVisible(false);
        listView.setManaged(false);
        detailView.setVisible(true);
        detailView.setManaged(true);
    }

    @FXML
    private void onBackToCrops() {
        detailView.setVisible(false);
        detailView.setManaged(false);
        listView.setVisible(true);
        listView.setManaged(true);
        loadCrops();
        updateSummaryCards();
    }

    // ═══════════ DETAIL POPULATION ═══════════

    private void populateDetail(Crop crop) {
        // ── Crop info ──
        lblDetailName.setText(crop.getCropName());
        Plot plot = plotCache.get(crop.getPlotId());
        lblDetailPlot.setText(plot != null ? plot.getName() : "Plot " + crop.getPlotId());

        String status = getStatusLabel(crop);
        lblDetailStatus.setText(status);
        String badgeBase = "-fx-padding:3 12;-fx-background-radius:12;-fx-font-size:11;-fx-font-weight:bold;";
        switch (status) {
            case "Growing"   -> lblDetailStatus.setStyle("-fx-background-color:#d1fae5;-fx-text-fill:#065f46;" + badgeBase);
            case "Ready"     -> lblDetailStatus.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#92400e;" + badgeBase);
            case "At Risk"   -> lblDetailStatus.setStyle("-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;" + badgeBase);
            case "Harvested" -> lblDetailStatus.setStyle("-fx-background-color:#e0e7ff;-fx-text-fill:#3730a3;" + badgeBase);
            default          -> lblDetailStatus.setStyle("-fx-background-color:#d1fae5;-fx-text-fill:#065f46;" + badgeBase);
        }

        lblDetailPlanted.setText("Planted  " + (crop.getPlantingDate() != null ? crop.getPlantingDate().format(DATE_FMT) : "—"));
        lblDetailArea.setText(plot != null ? String.format("%.1f ha", plot.getSizeAcres() * 0.4047) : "—");

        long daysSincePlanted = crop.getPlantingDate() != null ? ChronoUnit.DAYS.between(crop.getPlantingDate(), LocalDate.now()) : 0;
        lblDetailDays.setText(daysSincePlanted + " days");

        String stageName = crop.getGrowthStage().name();
        lblDetailStage.setText(stageName.charAt(0) + stageName.substring(1).toLowerCase());

        lblDetailHarvest.setText(crop.getHarvestDate() != null ? crop.getHarvestDate().format(DATE_FMT) : "—");

        // ── Donut chart ──
        int progressPct = calcGrowthProgress(crop);
        int expectedPct = calcExpectedProgress(crop);
        int behindPct = Math.max(0, expectedPct - progressPct);

        lblProgressPercent.setText(progressPct + "%");
        lblTargetProgress.setText(progressPct + "%");
        lblExpectedProgress.setText(expectedPct + "%");
        lblBehindSchedule.setText(behindPct + "%");

        double circumference = 2 * Math.PI * 70;
        double dashLen = circumference * progressPct / 100.0;
        progressArc.getStrokeDashArray().clear();
        progressArc.getStrokeDashArray().addAll(dashLen, circumference - dashLen);

        // ── Next milestone ──
        Crop.GrowthStage nextStage = getNextStage(crop.getGrowthStage());
        long totalDays = crop.getPlantingDate() != null && crop.getHarvestDate() != null
                ? ChronoUnit.DAYS.between(crop.getPlantingDate(), crop.getHarvestDate()) : 120;
        if (nextStage != null) {
            lblNextMilestone.setText(formatStageName(nextStage) + " Stage");
            long stagesLeft = Crop.GrowthStage.HARVESTED.ordinal() - crop.getGrowthStage().ordinal();
            long daysPerStage = stagesLeft > 0 ? Math.max(1, (totalDays - daysSincePlanted) / stagesLeft) : 0;
            lblMilestoneEta.setText(daysPerStage + " days");
        } else {
            lblNextMilestone.setText("Completed");
            lblMilestoneEta.setText("Harvested");
        }

        // ── Health indicators ──
        boolean isHealthy = !"At Risk".equals(status) && !"Harvested".equals(status);
        setHealthLabel(lblHealthOverall, isHealthy ? "Good" : ("At Risk".equals(status) ? "Poor" : "N/A"));
        setHealthLabel(lblHealthLeaf,    isHealthy ? "Good" : "Fair");
        setHealthLabel(lblHealthGrowth,  isHealthy ? "Good" : "Slow");
        setHealthLabel(lblHealthPest,    isHealthy ? "Low" : "Medium");
        setHealthLabel(lblHealthDisease, isHealthy ? "Low" : "Medium");

        // ── Yield estimate ──
        lblYieldEstimate.setText(String.format("%.0f kg", crop.getExpectedYield()));
        double yieldPct = Math.min(1.0, progressPct / 100.0);
        yieldBar.setMaxWidth(180 * yieldPct);

        // ── Stage timeline visual ──
        buildStageTimeline(crop);

        // ── Lifecycle days ──
        long remaining = Math.max(0, totalDays - daysSincePlanted);
        lblElapsedDays.setText(String.valueOf(daysSincePlanted));
        lblRemainingDays.setText(String.valueOf(remaining));
        lblTotalLifecycle.setText(totalDays + " days");
        lifecycleBar.setProgress(totalDays > 0 ? Math.min(1.0, daysSincePlanted / (double) totalDays) : 0);

        // ── Environmental conditions from live sensors ──
        populateEnvironment(crop.getPlotId());

        // ── Growth Timeline tab content ──
        buildTimelineTab(crop, daysSincePlanted, totalDays);

        // ── Care History tab content ──
        buildCareHistoryTab(crop);

        // ── Advance stage button visibility ──
        btnAdvanceStage.setVisible(crop.getGrowthStage() != Crop.GrowthStage.HARVESTED);
        btnAdvanceStage.setManaged(crop.getGrowthStage() != Crop.GrowthStage.HARVESTED);

        // ── Reset to overview tab ──
        showTabPane(overviewPane);
        setActiveTab(tabOverview);
    }

    private void setHealthLabel(Label lbl, String value) {
        lbl.setText(value);
        switch (value) {
            case "Good" -> lbl.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#16a34a;");
            case "Low"  -> lbl.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#3b82f6;");
            case "Fair", "Medium" -> lbl.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#d97706;");
            case "Poor", "High"  -> lbl.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#dc2626;");
            default     -> lbl.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#9ca3af;");
        }
    }

    private int calcGrowthProgress(Crop crop) {
        int stageIndex = crop.getGrowthStage().ordinal();
        int totalStages = Crop.GrowthStage.values().length - 1; // exclude HARVESTED as 100%
        return Math.min(100, (int) ((stageIndex / (double) totalStages) * 100));
    }

    private int calcExpectedProgress(Crop crop) {
        if (crop.getPlantingDate() == null || crop.getHarvestDate() == null) return 0;
        long totalDays = ChronoUnit.DAYS.between(crop.getPlantingDate(), crop.getHarvestDate());
        long elapsed = ChronoUnit.DAYS.between(crop.getPlantingDate(), LocalDate.now());
        if (totalDays <= 0) return 100;
        return Math.min(100, Math.max(0, (int) ((elapsed * 100) / totalDays)));
    }

    private Crop.GrowthStage getNextStage(Crop.GrowthStage current) {
        return switch (current) {
            case SEED       -> Crop.GrowthStage.SEEDLING;
            case SEEDLING   -> Crop.GrowthStage.VEGETATIVE;
            case VEGETATIVE -> Crop.GrowthStage.FLOWERING;
            case FLOWERING  -> Crop.GrowthStage.FRUITING;
            case FRUITING   -> Crop.GrowthStage.HARVESTED;
            case HARVESTED  -> null;
        };
    }

    // ═══════════ TAB HANDLERS ═══════════

    @FXML private void onTabOverview()  { setActiveTab(tabOverview);  showTabPane(overviewPane);  }
    @FXML private void onTabTimeline()  { setActiveTab(tabTimeline);  showTabPane(timelinePane);  }
    @FXML private void onTabHistory()   { setActiveTab(tabHistory);   showTabPane(historyPane);   }
    @FXML private void onTabPhotos()    { setActiveTab(tabPhotos);    showTabPane(photosPane);    }
    @FXML private void onTabNotes()     { setActiveTab(tabNotes);     showTabPane(notesPane);     }

    private void setActiveTab(Button active) {
        for (var node : tabBar.getChildren()) {
            if (node instanceof Button btn) {
                btn.getStyleClass().remove("crop-tab-active");
            }
        }
        if (!active.getStyleClass().contains("crop-tab-active")) {
            active.getStyleClass().add("crop-tab-active");
        }
    }

    private void showTabPane(VBox pane) {
        VBox[] panes = { overviewPane, timelinePane, historyPane, photosPane, notesPane };
        for (VBox p : panes) {
            boolean show = p == pane;
            p.setVisible(show);
            p.setManaged(show);
        }
    }

    // ═══════════ ADVANCE STAGE ═══════════

    @FXML
    private void onAdvanceStage() {
        if (selectedCrop == null || selectedCrop.getGrowthStage() == Crop.GrowthStage.HARVESTED) return;
        selectedCrop.advanceStage();
        try {
            cropDAO.update(selectedCrop);
            populateDetail(selectedCrop);
        } catch (SQLException e) {
            showAlert("Error", "Failed to advance stage: " + e.getMessage());
        }
    }

    // ═══════════ STAGE TIMELINE VISUAL ═══════════

    private String formatStageName(Crop.GrowthStage stage) {
        String name = stage.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private void buildStageTimeline(Crop crop) {
        stageTimeline.getChildren().clear();
        stageLabels.getChildren().clear();

        Crop.GrowthStage[] stages = Crop.GrowthStage.values();
        int currentIdx = crop.getGrowthStage().ordinal();

        for (int i = 0; i < stages.length; i++) {
            boolean completed = i <= currentIdx;
            boolean isCurrent = i == currentIdx;

            // Stage circle
            StackPane circlePane = new StackPane();
            circlePane.setPrefSize(36, 36);
            circlePane.setMinSize(36, 36);
            circlePane.setMaxSize(36, 36);

            Circle circle = new Circle(16);
            if (isCurrent) {
                circle.setFill(Color.web("#2e7d32"));
                circle.setStroke(Color.web("#2e7d32"));
                circle.setStrokeWidth(3);
            } else if (completed) {
                circle.setFill(Color.web("#2e7d32"));
                circle.setStroke(Color.TRANSPARENT);
            } else {
                circle.setFill(Color.web("#e5e7eb"));
                circle.setStroke(Color.TRANSPARENT);
            }

            Label numLabel = new Label(String.valueOf(i + 1));
            numLabel.setStyle("-fx-font-size:11;-fx-font-weight:bold;-fx-text-fill:" + (completed ? "white" : "#9ca3af") + ";");
            circlePane.getChildren().addAll(circle, numLabel);

            stageTimeline.getChildren().add(circlePane);

            // Connecting line (except after last)
            if (i < stages.length - 1) {
                Region line = new Region();
                line.setPrefHeight(3);
                line.setMinHeight(3);
                line.setMaxHeight(3);
                line.setPrefWidth(60);
                HBox.setHgrow(line, Priority.ALWAYS);
                line.setStyle("-fx-background-color:" + (i < currentIdx ? "#2e7d32" : "#e5e7eb") + ";-fx-background-radius:2;");
                stageTimeline.getChildren().add(line);
            }

            // Stage label
            Label stageLbl = new Label(formatStageName(stages[i]));
            stageLbl.setStyle("-fx-font-size:10;-fx-text-fill:" + (isCurrent ? "#2e7d32;-fx-font-weight:bold" : "#6b7280") + ";");
            stageLbl.setPrefWidth(36);
            stageLbl.setAlignment(Pos.CENTER);
            stageLbl.setWrapText(true);
            stageLabels.getChildren().add(stageLbl);

            // Spacer for label row to match lines
            if (i < stages.length - 1) {
                Region spacer = new Region();
                spacer.setPrefWidth(60);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                stageLabels.getChildren().add(spacer);
            }
        }
    }

    // ═══════════ ENVIRONMENTAL CONDITIONS ═══════════

    private void populateEnvironment(int plotId) {
        LiveSensorData live = LiveSensorData.getInstance();
        String deviceCode = "plot" + plotId + "_sensor";
        SensorReading reading = live.getLatestReading(deviceCode);

        if (reading != null) {
            lblConditionsSource.setText("Live from " + deviceCode);
            float t = reading.getTemperature();
            lblEnvTemp.setText(String.format("%.1f °C", t));
            setEnvStatus(lblEnvTempStatus, t > 35 ? "High" : t < 10 ? "Low" : "Normal");

            float h = reading.getHumidity();
            lblEnvHumidity.setText(String.format("%.0f %%", h));
            setEnvStatus(lblEnvHumStatus, h > 80 ? "High" : h < 30 ? "Low" : "Normal");

            float s = reading.getSoilMoisture();
            if (!Float.isNaN(s)) {
                lblEnvSoil.setText(String.format("%.0f %%", s));
                setEnvStatus(lblEnvSoilStatus, s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal");
            } else {
                lblEnvSoil.setText("-- %");
                setEnvStatus(lblEnvSoilStatus, "N/A");
            }
        } else {
            lblConditionsSource.setText("No live data for plot " + plotId);
            lblEnvTemp.setText("-- °C");
            lblEnvHumidity.setText("-- %");
            lblEnvSoil.setText("-- %");
            setEnvStatus(lblEnvTempStatus, "Offline");
            setEnvStatus(lblEnvHumStatus, "Offline");
            setEnvStatus(lblEnvSoilStatus, "Offline");
        }
    }

    private void setEnvStatus(Label lbl, String status) {
        lbl.setText(status);
        switch (status) {
            case "Normal" -> lbl.setStyle("-fx-font-size:10;-fx-text-fill:#16a34a;-fx-font-weight:bold;");
            case "High", "Wet" -> lbl.setStyle("-fx-font-size:10;-fx-text-fill:#dc2626;-fx-font-weight:bold;");
            case "Low", "Dry" -> lbl.setStyle("-fx-font-size:10;-fx-text-fill:#d97706;-fx-font-weight:bold;");
            default -> lbl.setStyle("-fx-font-size:10;-fx-text-fill:#9ca3af;-fx-font-weight:bold;");
        }
    }

    // ═══════════ TIMELINE TAB CONTENT ═══════════

    private void buildTimelineTab(Crop crop, long daysSincePlanted, long totalDays) {
        timelineEntries.getChildren().clear();
        Crop.GrowthStage[] stages = Crop.GrowthStage.values();
        int currentIdx = crop.getGrowthStage().ordinal();
        long daysPerStage = totalDays > 0 ? totalDays / stages.length : 20;

        for (int i = stages.length - 1; i >= 0; i--) {
            boolean completed = i <= currentIdx;
            boolean isCurrent = i == currentIdx;

            // Calculate estimated date for this stage
            long daysOffset = daysPerStage * i;
            LocalDate stageDate = crop.getPlantingDate() != null ? crop.getPlantingDate().plusDays(daysOffset) : null;

            HBox entry = new HBox(14);
            entry.setAlignment(Pos.TOP_LEFT);
            entry.setPadding(new Insets(0, 0, 0, 0));

            // Vertical indicator
            VBox indicator = new VBox();
            indicator.setAlignment(Pos.TOP_CENTER);
            indicator.setMinWidth(28);
            indicator.setPrefWidth(28);

            Circle dot = new Circle(7);
            if (isCurrent) {
                dot.setFill(Color.web("#2e7d32"));
                dot.setStroke(Color.web("#bbf7d0"));
                dot.setStrokeWidth(3);
            } else if (completed) {
                dot.setFill(Color.web("#2e7d32"));
                dot.setStroke(Color.TRANSPARENT);
            } else {
                dot.setFill(Color.web("#d1d5db"));
                dot.setStroke(Color.TRANSPARENT);
            }
            indicator.getChildren().add(dot);

            // Vertical line below dot (except for last entry which is first stage)
            if (i > 0) {
                Region line = new Region();
                line.setPrefWidth(2);
                line.setMinWidth(2);
                line.setMaxWidth(2);
                line.setPrefHeight(40);
                line.setStyle("-fx-background-color:" + (completed ? "#bbf7d0" : "#e5e7eb") + ";");
                VBox.setMargin(line, new Insets(4, 0, 0, 0));
                indicator.getChildren().add(line);
            }

            // Content
            VBox content = new VBox(2);
            Label title = new Label(formatStageName(stages[i]) + " Stage");
            title.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:" + (completed ? "#111827" : "#9ca3af") + ";");

            String dateStr = stageDate != null ? stageDate.format(DATE_FMT) : "—";
            String statusStr = isCurrent ? "  Current" : completed ? "  Completed" : "  Upcoming";
            Label detail = new Label(dateStr + statusStr);
            detail.setStyle("-fx-font-size:11;-fx-text-fill:" + (isCurrent ? "#2e7d32" : "#6b7280") + ";");

            content.getChildren().addAll(title, detail);
            entry.getChildren().addAll(indicator, content);
            timelineEntries.getChildren().add(entry);
        }
    }

    // ═══════════ CARE HISTORY TAB ═══════════

    private void buildCareHistoryTab(Crop crop) {
        careEntries.getChildren().clear();

        // Show relevant tasks for this crop's plot from the task table
        try {
            smartfarm.dao.TaskDAO taskDAO = new smartfarm.dao.TaskDAO();
            List<smartfarm.model.Task> tasks = taskDAO.getAll();
            List<smartfarm.model.Task> plotTasks = tasks.stream()
                    .filter(t -> t.getPlotId() == crop.getPlotId())
                    .sorted((a, b) -> {
                        if (a.getDueDate() == null) return 1;
                        if (b.getDueDate() == null) return -1;
                        return b.getDueDate().compareTo(a.getDueDate());
                    })
                    .limit(10)
                    .collect(Collectors.toList());

            if (plotTasks.isEmpty()) {
                Label empty = new Label("No care activities recorded for this plot.");
                empty.setStyle("-fx-font-size:13;-fx-text-fill:#9ca3af;-fx-padding:16 0;");
                careEntries.getChildren().add(empty);
                return;
            }

            for (smartfarm.model.Task task : plotTasks) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 12, 10, 12));
                row.setStyle("-fx-border-color:transparent transparent #f3f4f6 transparent;-fx-border-width:0 0 1 0;");

                // Icon
                String iconCode = task.getAlertType() != null && task.getAlertType().contains("IRRIGATION")
                        ? "fth-droplet" : "fth-clipboard";
                FontIcon icon = new FontIcon(iconCode);
                icon.setIconSize(14);
                icon.setIconColor(Color.web("#6b7280"));

                // Description
                VBox desc = new VBox(2);
                HBox.setHgrow(desc, Priority.ALWAYS);
                Label descLabel = new Label(task.getDescription());
                descLabel.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#111827;");
                Label dateLabel = new Label(task.getDueDate() != null ? task.getDueDate().format(DATE_FMT) : "—");
                dateLabel.setStyle("-fx-font-size:10;-fx-text-fill:#9ca3af;");
                desc.getChildren().addAll(descLabel, dateLabel);

                // Status badge
                String tStatus = task.getStatus().name();
                Label badge = new Label(tStatus);
                badge.setStyle("-fx-font-size:10;-fx-font-weight:bold;-fx-padding:2 8;-fx-background-radius:8;" +
                        (tStatus.equals("COMPLETED") ? "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;" :
                         tStatus.equals("IN_PROGRESS") ? "-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;" :
                         "-fx-background-color:#fef3c7;-fx-text-fill:#92400e;"));

                row.getChildren().addAll(icon, desc, badge);
                careEntries.getChildren().add(row);
            }
        } catch (SQLException e) {
            Label err = new Label("Could not load care history.");
            err.setStyle("-fx-font-size:13;-fx-text-fill:#dc2626;");
            careEntries.getChildren().add(err);
        }
    }

    // ═══════════ FILTERS & DATA ═══════════

    private void loadCrops() {
        try {
            allCrops.setAll(cropDAO.getAll());
        } catch (SQLException e) {
            allCrops.clear();
        }
        applyFilters();
    }

    private void setupFilters() {
        cmbPlot.getItems().add("All Plots");
        allCrops.stream().map(c -> getPlotLabel(c.getPlotId())).distinct().sorted()
                .forEach(p -> cmbPlot.getItems().add(p));
        cmbPlot.setValue("All Plots");
        cmbPlot.setOnAction(e -> applyFilters());

        cmbStage.getItems().addAll("All Stages", "Growing", "Ready", "At Risk", "Harvested");
        cmbStage.setValue("All Stages");
        cmbStage.setOnAction(e -> applyFilters());

        txtSearch.textProperty().addListener((obs, old, val) -> applyFilters());
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String plot = cmbPlot.getValue();
        String stage = cmbStage.getValue();

        List<Crop> filtered = allCrops.stream()
                .filter(c -> search.isEmpty() || c.getCropName().toLowerCase().contains(search))
                .filter(c -> "All Plots".equals(plot) || getPlotLabel(c.getPlotId()).equals(plot))
                .filter(c -> {
                    if ("All Stages".equals(stage)) return true;
                    return getStatusLabel(c).equals(stage);
                })
                .collect(Collectors.toList());

        cropTable.setItems(FXCollections.observableArrayList(filtered));
        lblPagination.setText("Showing 1 to " + filtered.size() + " of " + allCrops.size() + " crops");
    }

    private String getPlotLabel(int plotId) {
        Plot p = plotCache.get(plotId);
        return p != null ? p.getName() : "Plot " + plotId;
    }

    private String getStatusLabel(Crop c) {
        if (c.getGrowthStage() == Crop.GrowthStage.HARVESTED) return "Harvested";
        if (c.getGrowthStage() == Crop.GrowthStage.FRUITING) return "Ready";
        if (c.isOverdue()) return "At Risk";
        return "Growing";
    }

    private void updateSummaryCards() {
        int total = allCrops.size();
        long growing = allCrops.stream().filter(c -> getStatusLabel(c).equals("Growing")).count();
        long ready = allCrops.stream().filter(c -> getStatusLabel(c).equals("Ready")).count();
        long atRisk = allCrops.stream().filter(c -> getStatusLabel(c).equals("At Risk")).count();

        lblTotalCrops.setText(String.valueOf(total));
        lblGrowing.setText(String.valueOf(growing));
        lblReady.setText(String.valueOf(ready));
        lblAtRisk.setText(String.valueOf(atRisk));
    }

    // ═══════════ CRUD ═══════════

    @FXML
    private void onAddCrop() {
        Dialog<Crop> dialog = createCropDialog(null);
        dialog.showAndWait().ifPresent(crop -> {
            try {
                cropDAO.save(crop);
                loadCrops();
                updateSummaryCards();
                setupFilters();
            } catch (SQLException e) {
                showAlert("Error", "Failed to save: " + e.getMessage());
            }
        });
    }

    private void onEditCrop(Crop crop) {
        if (crop == null) return;
        Dialog<Crop> dialog = createCropDialog(crop);
        dialog.showAndWait().ifPresent(updated -> {
            try {
                updated.setCropId(crop.getCropId());
                cropDAO.update(updated);
                loadCrops();
                updateSummaryCards();
            } catch (SQLException e) {
                showAlert("Error", "Failed to update: " + e.getMessage());
            }
        });
    }

    private void onDeleteCrop(Crop crop) {
        if (crop == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + crop.getCropName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    cropDAO.delete(crop.getCropId());
                    loadCrops();
                    updateSummaryCards();
                } catch (SQLException e) {
                    showAlert("Error", "Failed to delete: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void onExport() {
        try {
            StringBuilder csv = new StringBuilder("Crop Name,Plot,Planted Date,Harvest Date,Stage,Expected Yield\n");
            for (Crop c : allCrops) {
                csv.append(String.format("%s,Plot %d,%s,%s,%s,%.1f\n",
                        c.getCropName(), c.getPlotId(),
                        c.getPlantingDate(), c.getHarvestDate(),
                        c.getGrowthStage(), c.getExpectedYield()));
            }
            File saved = CSVExporter.saveCsv(csv.toString(), "crops.csv");
            showAlert("Export", "Exported to " + saved.getName());
        } catch (IOException e) {
            showAlert("Error", "Failed to export: " + e.getMessage());
        }
    }

    private Dialog<Crop> createCropDialog(Crop existing) {
        Dialog<Crop> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Crop" : "Edit Crop");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getCropName() : "");
        nameField.setPromptText("Crop Name");

        TextField plotField = new TextField(existing != null ? String.valueOf(existing.getPlotId()) : "");
        plotField.setPromptText("Plot ID");

        DatePicker plantedPicker = new DatePicker(existing != null ? existing.getPlantingDate() : LocalDate.now());
        DatePicker harvestPicker = new DatePicker(existing != null ? existing.getHarvestDate() : LocalDate.now().plusMonths(3));

        TextField yieldField = new TextField(existing != null ? String.valueOf(existing.getExpectedYield()) : "");
        yieldField.setPromptText("Expected Yield (kg)");

        ComboBox<Crop.GrowthStage> stageCombo = new ComboBox<>();
        stageCombo.getItems().addAll(Crop.GrowthStage.values());
        stageCombo.setValue(existing != null ? existing.getGrowthStage() : Crop.GrowthStage.SEED);
        stageCombo.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(10,
                new Label("Crop Name:"), nameField,
                new Label("Plot ID:"), plotField,
                new Label("Planted Date:"), plantedPicker,
                new Label("Harvest Date:"), harvestPicker,
                new Label("Expected Yield (kg):"), yieldField,
                new Label("Growth Stage:"), stageCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { showAlert("Validation", "Crop name is required"); return null; }
                int plotId;
                try { plotId = Integer.parseInt(plotField.getText().trim()); }
                catch (NumberFormatException e) { showAlert("Validation", "Invalid Plot ID"); return null; }
                double yield;
                try { yield = Double.parseDouble(yieldField.getText().trim()); }
                catch (NumberFormatException e) { yield = 0; }

                Crop crop = new Crop(name, plantedPicker.getValue(), harvestPicker.getValue(), plotId, yield);
                crop.setGrowthStage(stageCombo.getValue());
                return crop;
            }
            return null;
        });
        return dialog;
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}