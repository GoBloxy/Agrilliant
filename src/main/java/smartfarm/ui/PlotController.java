package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.CropDAO;
import smartfarm.dao.PlotDAO;
import smartfarm.model.Crop;
import smartfarm.service.SystemLogManager;
import smartfarm.model.Plot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlotController {

    // ── Summary Cards ──
    @FXML private Label lblTotalPlots, lblCultivation, lblAvailable, lblFallow;

    // ── Overview Panel ──
    @FXML private StackPane chartContainer;
    @FXML private Label lblChartTotal;
    @FXML private Label lblLegendCultivation, lblLegendAvailable, lblLegendFallow;
    @FXML private Label lblTotalArea, lblCultivatedArea, lblAvailableArea;
    @FXML private Label lblStatusCultCount, lblStatusAvailCount, lblStatusFallowCount;

    // ── Map ──
    @FXML private Pane plotMapPane;
    @FXML private ToggleButton btnDrawMode;
    @FXML private Button btnDeletePlot;

    // ── Filters ──
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbField, cmbStatus, cmbCrop;

    // ── Buttons ──
    @FXML private Button btnAddPlot, btnExport;

    // ── Table ──
    @FXML private TableView<PlotRecord> plotTable;
    @FXML private TableColumn<PlotRecord, String> colPlotId, colField, colCrop, colArea;
    @FXML private TableColumn<PlotRecord, String> colStatus, colSoil, colIrrigation, colLastActivity, colActions;
    @FXML private Label lblPagination;

    // ── View Switching ──
    @FXML private VBox listView;
    @FXML private VBox detailView;

    // ── Detail View ──
    @FXML private HBox plotTabBar;
    @FXML private StackPane plotTabContent;
    @FXML private Button plotTabOverview, plotTabCrops, plotTabSensors, plotTabNotes;
    @FXML private Button btnEditPlotDetail;
    @FXML private VBox plotOverviewPane, plotCropsPane, plotSensorsPane, plotNotesPane;
    @FXML private Label lblPlotDetailName, lblPlotDetailLocation, lblPlotDetailStatus;
    @FXML private Label lblPlotDetailCreated, lblPlotDetailArea, lblPlotDetailSoil;
    @FXML private Label lblPlotDetailIrrigation, lblPlotDetailUpdated;
    @FXML private Label lblPlotCondSource;
    @FXML private Label lblPlotTemp, lblPlotTempStatus;
    @FXML private Label lblPlotHumidity, lblPlotHumStatus;
    @FXML private Label lblPlotSoilMoisture, lblPlotSoilStatus;
    @FXML private VBox plotDetailCropsBox, soilProfileBox, plotSummaryBox;
    @FXML private VBox plotCropsListBox, sensorHistoryBox;
    @FXML private TextArea txtPlotNotes;

    private Plot selectedDetailPlot;

    // Data
    private ObservableList<PlotRecord> allRecords = FXCollections.observableArrayList();
    private FilteredList<PlotRecord> filteredRecords;
    private List<Plot> dbPlots = new ArrayList<>();
    private List<Crop> dbCrops = new ArrayList<>();
    private Map<Integer, String> plotCropMap = new HashMap<>();

    // Drawing state
    private boolean drawingMode = false;
    private final List<double[]> currentPoints = new ArrayList<>();
    private final List<Circle> currentDots = new ArrayList<>();
    private Polygon currentPreview = null;
    private int plotCounter = 1;

    // Selection / editing state
    private static class PlotEntry {
        final Polygon polygon;
        final Label label;
        final String colorHex;
        PlotEntry(Polygon polygon, Label label, String colorHex) {
            this.polygon = polygon;
            this.label = label;
            this.colorHex = colorHex;
        }
    }
    private final List<PlotEntry> allPlots = new ArrayList<>();
    private PlotEntry selectedPlot = null;
    private final List<Circle> vertexHandles = new ArrayList<>();

    // Colors for drawn plots
    private static final String[] PLOT_COLORS = {
        "#4caf50", "#66bb6a", "#81c784", "#a5d6a7", "#388e3c"
    };

    // Hint label shown when map is empty
    private Label hintLabel;

    // Manager ID for creating plots
    private static int currentManagerId = 1;
    public static void setCurrentManagerId(int id) { currentManagerId = id; }

    @FXML
    public void initialize() {
        try {
            // Critical: make transparent Pane receive mouse events
            plotMapPane.setPickOnBounds(true);
            setupDonutChart();
            setupTable();
            setupFilters();
            loadPlotData();
            setupDrawingTool();
            showEmptyHint();
            System.out.println("[PlotController] initialized — draw mode & selection active");
        } catch (Exception e) {
            System.err.println("Error initializing PlotController: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showEmptyHint() {
        if (allPlots.isEmpty()) {
            hintLabel = new Label("Click the  ✏  button, then click on the map to draw plot boundaries.\nDouble-click to finish a polygon.");
            hintLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; "
                + "-fx-text-alignment: center; -fx-alignment: center; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 4, 0, 0, 1);");
            hintLabel.setMouseTransparent(true);
            hintLabel.setLayoutX(200);
            hintLabel.setLayoutY(170);
            plotMapPane.getChildren().add(hintLabel);
        }
    }

    private void removeHintIfNeeded() {
        if (hintLabel != null) {
            plotMapPane.getChildren().remove(hintLabel);
            hintLabel = null;
        }
    }

    private Label drawHintLabel;

    private void showDrawHint() {
        removeDrawHint();
        drawHintLabel = new Label("Drawing Mode — click on map to add points  •  double-click to finish");
        drawHintLabel.setStyle("-fx-background-color: rgba(46,125,50,0.92); "
            + "-fx-text-fill: white; -fx-font-size: 12; -fx-font-weight: bold; "
            + "-fx-padding: 6 12; -fx-background-radius: 4;");
        drawHintLabel.setMouseTransparent(true);
        drawHintLabel.setLayoutX(150);
        drawHintLabel.setLayoutY(15);
        plotMapPane.getChildren().add(drawHintLabel);
    }

    private void removeDrawHint() {
        if (drawHintLabel != null) {
            plotMapPane.getChildren().remove(drawHintLabel);
            drawHintLabel = null;
        }
    }

    // ═══════════════ SELECTION & EDITING ═══════════════

    private void selectPlot(PlotEntry entry) {
        deselectPlot();
        selectedPlot = entry;

        // Highlight the selected polygon
        entry.polygon.setStroke(Color.WHITE);
        entry.polygon.setStrokeWidth(3);
        entry.polygon.getStrokeDashArray().setAll(8.0, 4.0);

        // Show draggable vertex handles
        showVertexHandles(entry.polygon);
    }

    private void deselectPlot() {
        if (selectedPlot != null) {
            // Restore original stroke
            selectedPlot.polygon.setStroke(Color.web(selectedPlot.colorHex, 0.9));
            selectedPlot.polygon.setStrokeWidth(2);
            selectedPlot.polygon.getStrokeDashArray().clear();
            selectedPlot = null;
        }
        removeVertexHandles();
    }

    private void showVertexHandles(Polygon poly) {
        removeVertexHandles();
        List<Double> pts = poly.getPoints();
        for (int i = 0; i < pts.size(); i += 2) {
            final int idx = i;
            double x = pts.get(i);
            double y = pts.get(i + 1);

            Circle handle = new Circle(x, y, 5, Color.WHITE);
            handle.setStroke(Color.web("#1b5e20"));
            handle.setStrokeWidth(2);
            handle.setCursor(Cursor.MOVE);

            // Drag to move vertex
            handle.setOnMouseDragged(event -> {
                double nx = event.getX();
                double ny = event.getY();
                handle.setCenterX(nx);
                handle.setCenterY(ny);
                pts.set(idx, nx);
                pts.set(idx + 1, ny);
                updateLabelPosition(selectedPlot);
                event.consume();
            });
            handle.setOnMousePressed(event -> event.consume());
            handle.setOnMouseReleased(event -> event.consume());

            vertexHandles.add(handle);
            plotMapPane.getChildren().add(handle);
        }
    }

    private void removeVertexHandles() {
        plotMapPane.getChildren().removeAll(vertexHandles);
        vertexHandles.clear();
    }

    private void updateLabelPosition(PlotEntry entry) {
        if (entry == null || entry.label == null) return;
        List<Double> pts = entry.polygon.getPoints();
        double cx = 0, cy = 0;
        int count = pts.size() / 2;
        for (int i = 0; i < pts.size(); i += 2) {
            cx += pts.get(i);
            cy += pts.get(i + 1);
        }
        cx /= count;
        cy /= count;
        entry.label.setLayoutX(cx - 35);
        entry.label.setLayoutY(cy - 20);
    }

    // ═══════════════ DRAWING TOOL ═══════════════

    private void setupDrawingTool() {
        // Toggle draw mode
        if (btnDrawMode != null) {
            btnDrawMode.selectedProperty().addListener((obs, oldVal, newVal) -> {
                drawingMode = newVal;
                plotMapPane.setCursor(drawingMode ? Cursor.CROSSHAIR : Cursor.DEFAULT);
                // Visual feedback: highlight map border & change button bg when active
                if (drawingMode) {
                    plotMapPane.setStyle("-fx-background-color: rgba(76,175,80,0.10); "
                        + "-fx-border-color: #4caf50; -fx-border-width: 3; -fx-border-style: dashed;");
                    btnDrawMode.setStyle("-fx-background-color: #4caf50; -fx-background-radius: 4; "
                        + "-fx-padding: 6; -fx-cursor: hand;");
                    deselectPlot();
                    removeHintIfNeeded();
                    showDrawHint();
                    System.out.println("[PlotController] Draw mode ENABLED");
                } else {
                    plotMapPane.setStyle("-fx-background-color: transparent;");
                    btnDrawMode.setStyle("-fx-background-color: white; -fx-background-radius: 4; "
                        + "-fx-padding: 6; -fx-cursor: hand;");
                    removeDrawHint();
                    cancelCurrentDrawing();
                    System.out.println("[PlotController] Draw mode DISABLED");
                }
            });
        }

        // Click on map pane background
        plotMapPane.setOnMouseClicked(event -> {
            System.out.println("[PlotController] map clicked at " + event.getX() + "," + event.getY()
                + " (drawMode=" + drawingMode + ", count=" + event.getClickCount() + ")");
            if (drawingMode) {
                if (event.getClickCount() == 2 && currentPoints.size() >= 3) {
                    finishPolygon();
                    event.consume();
                } else if (event.getClickCount() == 1) {
                    double x = event.getX();
                    double y = event.getY();
                    currentPoints.add(new double[]{x, y});

                    Circle dot = new Circle(x, y, 5, Color.WHITE);
                    dot.setStroke(Color.web("#2e7d32"));
                    dot.setStrokeWidth(2);
                    currentDots.add(dot);
                    plotMapPane.getChildren().add(dot);

                    updatePreview();
                }
            } else {
                // Clicking empty space deselects
                deselectPlot();
            }
        });

        // Delete button removes selected plot, or last drawn if none selected
        if (btnDeletePlot != null) {
            btnDeletePlot.setOnAction(e -> {
                if (selectedPlot != null) {
                    plotMapPane.getChildren().remove(selectedPlot.polygon);
                    plotMapPane.getChildren().remove(selectedPlot.label);
                    allPlots.remove(selectedPlot);
                    removeVertexHandles();
                    selectedPlot = null;
                }
            });
        }
    }

    private void updatePreview() {
        if (currentPreview != null) {
            plotMapPane.getChildren().remove(currentPreview);
        }
        if (currentPoints.size() >= 2) {
            currentPreview = new Polygon();
            for (double[] pt : currentPoints) {
                currentPreview.getPoints().addAll(pt[0], pt[1]);
            }
            currentPreview.setFill(Color.web("#4caf50", 0.25));
            currentPreview.setStroke(Color.web("#2e7d32"));
            currentPreview.setStrokeWidth(2);
            currentPreview.getStrokeDashArray().addAll(6.0, 4.0);
            currentPreview.setMouseTransparent(true);
            int insertIdx = plotMapPane.getChildren().size() - currentDots.size();
            plotMapPane.getChildren().add(Math.max(0, insertIdx), currentPreview);
        }
    }

    private void finishPolygon() {
        if (currentPreview != null) {
            plotMapPane.getChildren().remove(currentPreview);
            currentPreview = null;
        }
        plotMapPane.getChildren().removeAll(currentDots);
        currentDots.clear();

        // Snapshot the points and reset state so further clicks don't add to this polygon
        final List<double[]> finishedPoints = new ArrayList<>(currentPoints);
        currentPoints.clear();

        // Exit draw mode immediately (visual feedback)
        if (btnDrawMode != null) btnDrawMode.setSelected(false);

        // Defer the modal dialog so it doesn't block the event handler
        javafx.application.Platform.runLater(() -> {
            TextInputDialog nameDialog = new TextInputDialog("Plot " + plotCounter);
            nameDialog.setTitle("Name This Plot");
            nameDialog.setHeaderText("Enter a name for the drawn plot:");
            nameDialog.setContentText("Plot name:");
            String plotName = nameDialog.showAndWait().orElse(null);
            if (plotName == null || plotName.trim().isEmpty()) {
                return;
            }

            // Pick crop color for the polygon (first available crop or default)
            String cropName = plotCropMap.values().stream().findFirst().orElse(null);
            String color = getCropColor(cropName);

            // Create final polygon
            Polygon poly = new Polygon();
            for (double[] pt : finishedPoints) {
                poly.getPoints().addAll(pt[0], pt[1]);
            }
            poly.setFill(Color.web(color, 0.4));
            poly.setStroke(Color.web(color));
            poly.setStrokeWidth(2);
            poly.setCursor(Cursor.HAND);
            plotMapPane.getChildren().add(poly);

            // Add centered label
            double cx = 0, cy = 0;
            for (double[] pt : finishedPoints) { cx += pt[0]; cy += pt[1]; }
            cx /= finishedPoints.size();
            cy /= finishedPoints.size();

            Label lbl = new Label(plotName.trim());
            lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 2, 0, 0, 1);");
            lbl.setLayoutX(cx - 25);
            lbl.setLayoutY(cy - 8);
            lbl.setMouseTransparent(true);
            plotMapPane.getChildren().add(lbl);

            PlotEntry entry = new PlotEntry(poly, lbl, color);
            allPlots.add(entry);
            removeHintIfNeeded();

            // Make the new plot selectable
            poly.setOnMouseClicked(event -> {
                if (!drawingMode) {
                    selectPlot(entry);
                    event.consume();
                }
            });

            plotCounter++;
        });
    }

    private void cancelCurrentDrawing() {
        if (currentPreview != null) {
            plotMapPane.getChildren().remove(currentPreview);
            currentPreview = null;
        }
        plotMapPane.getChildren().removeAll(currentDots);
        currentDots.clear();
        currentPoints.clear();
    }

    // ═══════════════ DONUT CHART ═══════════════

    private javafx.scene.layout.Pane donutArcPane;

    private void setupDonutChart() {
        donutArcPane = new javafx.scene.layout.Pane();
        donutArcPane.setPrefSize(130, 130);
        chartContainer.getChildren().add(0, donutArcPane);
    }

    // ═══════════════ TABLE ═══════════════

    private void setupTable() {
        plotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // colPlotId stays resizable so it stretches to fill any remaining width
        colField.setResizable(false);
        colCrop.setResizable(false);
        colArea.setResizable(false);
        colStatus.setResizable(false);
        colSoil.setResizable(false);
        colIrrigation.setResizable(false);
        colLastActivity.setResizable(false);
        colActions.setResizable(false);

        colPlotId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("plotId"));
        colField.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("field"));
        colCrop.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("crop"));
        colArea.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("area"));
        colStatus.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
        colSoil.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("soil"));
        colIrrigation.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("irrigation"));
        colLastActivity.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("lastActivity"));

        // ── Crop column: colored dot + name ──
        colCrop.setCellFactory(col -> new TableCell<PlotRecord, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                if (item.equals("-") || item.equals("—")) { setText("—"); setGraphic(null); return; }

                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER_LEFT);

                Circle dot = new Circle(5);
                dot.setFill(Color.web(getCropColor(item)));

                Label lbl = new Label(item);
                lbl.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
                box.getChildren().addAll(dot, lbl);
                setGraphic(box);
                setText(null);
            }
        });

        // ── Status column: colored badge ──
        colStatus.setCellFactory(col -> new TableCell<PlotRecord, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }

                Label badge = new Label(item);
                badge.setStyle("-fx-padding: 3 10; -fx-background-radius: 12; -fx-font-size: 10; -fx-font-weight: bold;");

                switch (item) {
                    case "Under Cultivation":
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #d1fae5; -fx-text-fill: #065f46;");
                        break;
                    case "Available":
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #fed7aa; -fx-text-fill: #9a3412;");
                        break;
                    case "Fallow":
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #f3e8ff; -fx-text-fill: #7c3aed;");
                        break;
                    default:
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #f3f4f6; -fx-text-fill: #374151;");
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // ── Irrigation column: droplet icon + text ──
        colIrrigation.setCellFactory(col -> new TableCell<PlotRecord, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                if (item.equals("-") || item.equals("—")) { setText("—"); setGraphic(null); return; }

                HBox box = new HBox(5);
                box.setAlignment(Pos.CENTER_LEFT);
                FontIcon icon = new FontIcon("fth-droplet");
                icon.setIconSize(12);
                icon.setIconColor(Color.web("#3b82f6"));
                Label lbl = new Label(item);
                lbl.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
                box.getChildren().addAll(icon, lbl);
                setGraphic(box);
                setText(null);
            }
        });

        // ── Last Activity column: date + sub-text ──
        colLastActivity.setCellFactory(col -> new TableCell<PlotRecord, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                if (item.equals("-") || item.equals("—")) { setText("—"); setGraphic(null); return; }

                String[] parts = item.split("\n");
                VBox box = new VBox(1);
                box.setAlignment(Pos.CENTER_LEFT);
                Label dateLbl = new Label(parts[0]);
                dateLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #374151;");
                box.getChildren().add(dateLbl);
                if (parts.length > 1) {
                    Label subLbl = new Label(parts[1]);
                    subLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #9ca3af;");
                    box.getChildren().add(subLbl);
                }
                setGraphic(box);
                setText(null);
            }
        });

        // ── Actions column: view / edit / delete icons ──
        colActions.setCellFactory(col -> new TableCell<PlotRecord, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); setText(null); return; }

                HBox box = new HBox(8);
                box.setAlignment(Pos.CENTER_LEFT);

                FontIcon eye = new FontIcon("fth-eye");
                eye.setIconSize(14); eye.setIconColor(Color.web("#6b7280"));
                eye.setCursor(Cursor.HAND);
                eye.setOnMouseClicked(e -> {
                    PlotRecord rec = getTableView().getItems().get(getIndex());
                    onViewPlot(rec);
                });

                FontIcon edit = new FontIcon("fth-edit-2");
                edit.setIconSize(14); edit.setIconColor(Color.web("#6b7280"));
                edit.setCursor(Cursor.HAND);
                edit.setOnMouseClicked(e -> {
                    PlotRecord rec = getTableView().getItems().get(getIndex());
                    onEditPlotByRecord(rec);
                });

                FontIcon trash = new FontIcon("fth-trash-2");
                trash.setIconSize(14); trash.setIconColor(Color.web("#ef4444"));
                trash.setCursor(Cursor.HAND);
                trash.setOnMouseClicked(e -> {
                    PlotRecord rec = getTableView().getItems().get(getIndex());
                    onDeletePlotByRecord(rec);
                });

                box.getChildren().addAll(eye, edit, trash);
                setGraphic(box);
                setText(null);
            }
        });

        // Double-click to open details
        plotTable.setRowFactory(tv -> {
            TableRow<PlotRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onViewPlot(row.getItem());
                }
            });
            return row;
        });
    }

    // ═══════════════ FILTERS ═══════════════

    private void setupFilters() {
        filteredRecords = new FilteredList<>(allRecords, p -> true);
        plotTable.setItems(filteredRecords);

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, o, n) -> applyFilters());
        }
        if (cmbField != null) {
            cmbField.setOnAction(e -> applyFilters());
        }
        if (cmbStatus != null) {
            cmbStatus.getItems().setAll("All Status", "Under Cultivation", "Available", "Fallow");
            cmbStatus.setOnAction(e -> applyFilters());
        }
        if (cmbCrop != null) {
            cmbCrop.setOnAction(e -> applyFilters());
        }
    }

    private void populateFilterCombos() {
        if (cmbField != null) {
            List<String> fields = allRecords.stream()
                    .map(PlotRecord::getField)
                    .distinct().sorted()
                    .collect(Collectors.toList());
            fields.add(0, "All Fields");
            cmbField.getItems().setAll(fields);
        }
        if (cmbCrop != null) {
            List<String> crops = allRecords.stream()
                    .map(PlotRecord::getCrop)
                    .filter(c -> !c.equals("—"))
                    .distinct().sorted()
                    .collect(Collectors.toList());
            crops.add(0, "All Crops");
            cmbCrop.getItems().setAll(crops);
        }
    }

    private void applyFilters() {
        String search = txtSearch != null ? txtSearch.getText() : "";
        String fieldFilter = cmbField != null ? cmbField.getValue() : null;
        String statusFilter = cmbStatus != null ? cmbStatus.getValue() : null;
        String cropFilter = cmbCrop != null ? cmbCrop.getValue() : null;

        filteredRecords.setPredicate(rec -> {
            if (search != null && !search.isBlank()) {
                String lower = search.toLowerCase();
                boolean matches = rec.getPlotId().toLowerCase().contains(lower)
                        || rec.getField().toLowerCase().contains(lower)
                        || rec.getCrop().toLowerCase().contains(lower)
                        || rec.getSoil().toLowerCase().contains(lower);
                if (!matches) return false;
            }
            if (fieldFilter != null && !fieldFilter.equals("All Fields")) {
                if (!rec.getField().equals(fieldFilter)) return false;
            }
            if (statusFilter != null && !statusFilter.equals("All Status")) {
                if (!rec.getStatus().equals(statusFilter)) return false;
            }
            if (cropFilter != null && !cropFilter.equals("All Crops")) {
                if (!rec.getCrop().equals(cropFilter)) return false;
            }
            return true;
        });

        updatePagination();
    }

    private void updatePagination() {
        int total = allRecords.size();
        int showing = filteredRecords.size();
        if (lblPagination != null) {
            lblPagination.setText("Showing " + showing + " of " + total + " plots");
        }
    }

    // ═══════════════ DB DATA LOADING ═══════════════

    private void loadPlotData() {
        PlotDAO plotDAO = new PlotDAO();
        CropDAO cropDAO = new CropDAO();
        try {
            dbPlots = plotDAO.getAll();
            dbCrops = cropDAO.getAll();

            plotCropMap.clear();
            for (Crop c : dbCrops) {
                if (c.getGrowthStage() != Crop.GrowthStage.HARVESTED) {
                    // Append multiple crop names if a plot has more than one active crop
                    plotCropMap.merge(c.getPlotId(), c.getCropName(), (old, nw) -> old + ", " + nw);
                }
            }

            allRecords.clear();
            int cultivation = 0, available = 0, fallow = 0;
            double totalArea = 0, cultivatedArea = 0, availableArea = 0;

            for (Plot p : dbPlots) {
                String cropName = plotCropMap.getOrDefault(p.getPlotId(), "—");
                boolean hasCrop = !cropName.equals("—");
                // Use persisted status if set, otherwise derive from crop presence
                String status;
                if (p.getStatus() != null && !p.getStatus().isEmpty()) {
                    status = p.getStatus();
                } else {
                    status = hasCrop ? "Under Cultivation" : "Available";
                }
                if (status.equals("Under Cultivation")) cultivation++;
                else if (status.equals("Fallow")) fallow++;
                else available++;

                totalArea += p.getSizeAcres();
                if (status.equals("Under Cultivation")) cultivatedArea += p.getSizeAcres();
                else if (status.equals("Available")) availableArea += p.getSizeAcres();

                String plotLabel = p.getName() + " - " + p.getLocation();
                String area = String.format("%.1f", p.getSizeAcres());
                String updated = p.getUpdatedAt() != null
                        ? p.getUpdatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                        : "—";

                String irrigation = p.getIrrigationType() != null ? p.getIrrigationType() : "—";
                allRecords.add(new PlotRecord(
                        p.getPlotId(), plotLabel, p.getLocation(), cropName, area,
                        status, p.getSoilType(), irrigation, updated
                ));
            }

            // Update summary cards
            int total = dbPlots.size();
            lblTotalPlots.setText(String.valueOf(total));
            lblCultivation.setText(String.valueOf(cultivation));
            lblAvailable.setText(String.valueOf(available));
            lblFallow.setText(String.valueOf(fallow));

            // Update overview panel
            updateOverviewPanel(total, cultivation, available, fallow, totalArea, cultivatedArea, availableArea);

            // Populate filter combos
            populateFilterCombos();
            updatePagination();

        } catch (SQLException e) {
            System.err.println("Failed to load plot data: " + e.getMessage());
            e.printStackTrace();
            plotTable.setPlaceholder(new Label("Could not load plots: " + e.getMessage()));
        } catch (Exception e) {
            System.err.println("Unexpected error loading plot data: " + e.getMessage());
            e.printStackTrace();
            plotTable.setPlaceholder(new Label("Unexpected error: " + e.getMessage()));
        }
    }

    private void updateOverviewPanel(int total, int cultivation, int available, int fallow,
                                     double totalArea, double cultivatedArea, double availableArea) {
        if (lblChartTotal != null) lblChartTotal.setText(String.valueOf(total));

        if (total > 0) {
            String cultPct = String.format("%.1f", (cultivation * 100.0 / total));
            String availPct = String.format("%.1f", (available * 100.0 / total));
            String fallPct = String.format("%.1f", (fallow * 100.0 / total));

            if (lblLegendCultivation != null) lblLegendCultivation.setText(cultivation + " (" + cultPct + "%)");
            if (lblLegendAvailable != null) lblLegendAvailable.setText(available + " (" + availPct + "%)");
            if (lblLegendFallow != null) lblLegendFallow.setText(fallow + " (" + fallPct + "%)");
        }

        if (lblTotalArea != null) lblTotalArea.setText(String.format("%.1f ha", totalArea));
        if (lblCultivatedArea != null) {
            String pct = totalArea > 0 ? String.format(" (%.1f%%)", cultivatedArea * 100.0 / totalArea) : "";
            lblCultivatedArea.setText(String.format("%.1f ha%s", cultivatedArea, pct));
        }
        if (lblAvailableArea != null) lblAvailableArea.setText(String.format("%.1f ha", availableArea));

        if (lblStatusCultCount != null) lblStatusCultCount.setText(String.valueOf(cultivation));
        if (lblStatusAvailCount != null) lblStatusAvailCount.setText(String.valueOf(available));
        if (lblStatusFallowCount != null) lblStatusFallowCount.setText(String.valueOf(fallow));

        // Update donut chart arcs proportionally
        updateDonutChartArcs(cultivation, available, fallow);
    }

    private void updateDonutChartArcs(int cultivation, int available, int fallow) {
        if (donutArcPane == null) return;
        donutArcPane.getChildren().clear();

        int total = cultivation + available + fallow;
        if (total == 0) {
            // Draw a single grey ring as placeholder
            javafx.scene.shape.Arc empty = new javafx.scene.shape.Arc(65, 65, 50, 50, 0, 360);
            empty.setFill(Color.TRANSPARENT);
            empty.setStroke(Color.web("#e5e7eb"));
            empty.setStrokeWidth(12);
            donutArcPane.getChildren().add(empty);
            return;
        }

        double cultAngle = (cultivation * 360.0) / total;
        double availAngle = (available * 360.0) / total;
        double fallAngle = (fallow * 360.0) / total;

        double startAngle = 90;

        if (cultAngle > 0) {
            javafx.scene.shape.Arc arc1 = new javafx.scene.shape.Arc(65, 65, 50, 50, startAngle, cultAngle);
            arc1.setFill(Color.TRANSPARENT);
            arc1.setStroke(Color.web("#28a745"));
            arc1.setStrokeWidth(12);
            arc1.setType(javafx.scene.shape.ArcType.OPEN);
            donutArcPane.getChildren().add(arc1);
        }
        startAngle += cultAngle;

        if (availAngle > 0) {
            javafx.scene.shape.Arc arc2 = new javafx.scene.shape.Arc(65, 65, 50, 50, startAngle, availAngle);
            arc2.setFill(Color.TRANSPARENT);
            arc2.setStroke(Color.web("#ffc107"));
            arc2.setStrokeWidth(12);
            arc2.setType(javafx.scene.shape.ArcType.OPEN);
            donutArcPane.getChildren().add(arc2);
        }
        startAngle += availAngle;

        if (fallAngle > 0) {
            javafx.scene.shape.Arc arc3 = new javafx.scene.shape.Arc(65, 65, 50, 50, startAngle, fallAngle);
            arc3.setFill(Color.TRANSPARENT);
            arc3.setStroke(Color.web("#6f42c1"));
            arc3.setStrokeWidth(12);
            arc3.setType(javafx.scene.shape.ArcType.OPEN);
            donutArcPane.getChildren().add(arc3);
        }
    }

    // ═══════════════ CROP COLORS ═══════════════

    private static final String[] CROP_PALETTE = {
        "#ef4444", "#f59e0b", "#22c55e", "#3b82f6", "#8b5cf6",
        "#ec4899", "#14b8a6", "#f97316", "#06b6d4", "#84cc16",
        "#e11d48", "#0ea5e9", "#d946ef", "#facc15", "#10b981"
    };

    private static String getCropColor(String cropName) {
        if (cropName == null || cropName.equals("—") || cropName.isEmpty()) return "#9ca3af";
        int hash = Math.abs(cropName.hashCode());
        return CROP_PALETTE[hash % CROP_PALETTE.length];
    }

    // ═══════════════ CRUD OPERATIONS ═══════════════

    // Tracks the crops selected in the dialog so we can re-assign after save
    private List<String> dialogSelectedCrops = new ArrayList<>();

    @FXML
    private void onAddPlot() {
        Dialog<Plot> dialog = createPlotDialog(null, null);
        dialog.showAndWait().ifPresent(plot -> {
            PlotDAO plotDAO = new PlotDAO();
            try {
                plotDAO.save(plot);
                // If crops were selected, assign them to this new plot
                for (String cropName : dialogSelectedCrops) {
                    reassignCropToPlot(cropName, plot.getPlotId());
                }
                SystemLogManager.getInstance().info("PlotController",
                        "Plot '" + plot.getName() + "' created (" + plot.getSizeAcres() + " acres)", "manager");
                loadPlotData();
            } catch (SQLException e) {
                SystemLogManager.getInstance().error("PlotController",
                        "Failed to save plot: " + e.getMessage(), "system");
                showAlert("Error", "Failed to save plot: " + e.getMessage());
            }
        });
    }

    private void onEditPlotByRecord(PlotRecord rec) {
        Plot dbPlot = findPlotById(rec.getDbPlotId());
        if (dbPlot == null) { showAlert("Error", "Plot not found in database"); return; }

        String currentCrop = plotCropMap.getOrDefault(dbPlot.getPlotId(), null);
        Dialog<Plot> dialog = createPlotDialog(dbPlot, currentCrop);
        dialog.showAndWait().ifPresent(updated -> {
            PlotDAO plotDAO = new PlotDAO();
            try {
                updated.setPlotId(dbPlot.getPlotId());
                plotDAO.update(updated);
                // Reassign crops: unassign old ones, assign new ones
                reassignCropsToPlot(dialogSelectedCrops, dbPlot.getPlotId());
                SystemLogManager.getInstance().info("PlotController",
                        "Plot '" + updated.getName() + "' updated", "manager");
                loadPlotData();
            } catch (SQLException e) {
                SystemLogManager.getInstance().error("PlotController",
                        "Failed to update plot: " + e.getMessage(), "system");
                showAlert("Error", "Failed to update plot: " + e.getMessage());
            }
        });
    }

    private void reassignCropToPlot(String cropName, int plotId) {
        CropDAO cropDAO = new CropDAO();
        try {
            List<Crop> all = cropDAO.getAll();
            for (Crop c : all) {
                if (c.getCropName().equals(cropName) && c.getGrowthStage() != Crop.GrowthStage.HARVESTED) {
                    c.setPlotId(plotId);
                    cropDAO.update(c);
                    return;
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to reassign crop: " + e.getMessage());
        }
    }

    private void reassignCropsToPlot(List<String> selectedCropNames, int plotId) {
        CropDAO cropDAO = new CropDAO();
        try {
            List<Crop> all = cropDAO.getAll();
            for (Crop c : all) {
                if (c.getGrowthStage() == Crop.GrowthStage.HARVESTED) continue;
                if (c.getPlotId() == plotId && !selectedCropNames.contains(c.getCropName())) {
                    // Unassign crops that were removed
                    c.setPlotId(0);
                    cropDAO.update(c);
                }
                if (selectedCropNames.contains(c.getCropName()) && c.getPlotId() != plotId) {
                    // Assign newly selected crops
                    c.setPlotId(plotId);
                    cropDAO.update(c);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to reassign crops: " + e.getMessage());
        }
    }

    private void onDeletePlotByRecord(PlotRecord rec) {
        Plot dbPlot = findPlotById(rec.getDbPlotId());
        if (dbPlot == null) { showAlert("Error", "Plot not found in database"); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + dbPlot.getName() + "\"?\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Plot");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                PlotDAO plotDAO = new PlotDAO();
                try {
                    plotDAO.delete(dbPlot.getPlotId());
                    SystemLogManager.getInstance().info("PlotController",
                            "Plot '" + dbPlot.getName() + "' deleted", "manager");
                    loadPlotData();
                } catch (SQLException e) {
                    SystemLogManager.getInstance().error("PlotController",
                            "Failed to delete plot: " + e.getMessage(), "system");
                    showAlert("Error", "Failed to delete: " + e.getMessage()
                            + "\nThe plot may have linked crops, tasks, or sensors.");
                }
            }
        });
    }

    private void onViewPlot(PlotRecord rec) {
        Plot dbPlot = findPlotById(rec.getDbPlotId());
        if (dbPlot == null) return;
        showPlotDetail(dbPlot);
    }

    // ═══════════════ DETAIL VIEW ═══════════════

    private void showPlotDetail(Plot plot) {
        selectedDetailPlot = plot;
        populatePlotDetail(plot);
        listView.setVisible(false);
        listView.setManaged(false);
        detailView.setVisible(true);
        detailView.setManaged(true);
        onPlotTabOverview();
    }

    @FXML
    private void onBackToPlots() {
        detailView.setVisible(false);
        detailView.setManaged(false);
        listView.setVisible(true);
        listView.setManaged(true);
        loadPlotData();
    }

    @FXML
    private void onEditPlotFromDetail() {
        if (selectedDetailPlot == null) return;
        String currentCrop = plotCropMap.getOrDefault(selectedDetailPlot.getPlotId(), null);
        Dialog<Plot> dialog = createPlotDialog(selectedDetailPlot, currentCrop);
        dialog.showAndWait().ifPresent(updated -> {
            PlotDAO plotDAO = new PlotDAO();
            try {
                updated.setPlotId(selectedDetailPlot.getPlotId());
                plotDAO.update(updated);
                reassignCropsToPlot(dialogSelectedCrops, selectedDetailPlot.getPlotId());
                // Reload data and refresh detail
                loadPlotData();
                selectedDetailPlot = findPlotById(selectedDetailPlot.getPlotId());
                if (selectedDetailPlot != null) populatePlotDetail(selectedDetailPlot);
            } catch (SQLException e) {
                showAlert("Error", "Failed to update: " + e.getMessage());
            }
        });
    }

    private void populatePlotDetail(Plot plot) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        smartfarm.service.SettingsManager sm = smartfarm.service.SettingsManager.getInstance();

        // ── Info card ──
        lblPlotDetailName.setText(plot.getName());
        lblPlotDetailLocation.setText(plot.getLocation());

        String status = plot.getStatus() != null ? plot.getStatus() : "Available";
        lblPlotDetailStatus.setText(status);
        String badgeBase = "-fx-padding:3 12;-fx-background-radius:12;-fx-font-size:11;-fx-font-weight:bold;";
        switch (status) {
            case "Under Cultivation": lblPlotDetailStatus.setStyle(badgeBase + "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;"); break;
            case "Fallow": lblPlotDetailStatus.setStyle(badgeBase + "-fx-background-color:#f3e8ff;-fx-text-fill:#7c3aed;"); break;
            default: lblPlotDetailStatus.setStyle(badgeBase + "-fx-background-color:#fed7aa;-fx-text-fill:#9a3412;");
        }

        lblPlotDetailCreated.setText("Created  " + (plot.getCreatedAt() != null ? plot.getCreatedAt().toLocalDate().format(dateFmt) : "—"));
        lblPlotDetailArea.setText(String.format("%.1f acres", plot.getSizeAcres()));
        lblPlotDetailSoil.setText(plot.getSoilType() != null ? plot.getSoilType() : "—");
        lblPlotDetailIrrigation.setText(plot.getIrrigationType() != null ? plot.getIrrigationType() : "—");
        lblPlotDetailUpdated.setText(plot.getUpdatedAt() != null ? plot.getUpdatedAt().toLocalDate().format(dateFmt) : "—");

        // ── Active Crops (overview) ──
        plotDetailCropsBox.getChildren().clear();
        List<Crop> plotCrops = dbCrops.stream()
                .filter(c -> c.getPlotId() == plot.getPlotId() && c.getGrowthStage() != Crop.GrowthStage.HARVESTED)
                .collect(Collectors.toList());
        if (plotCrops.isEmpty()) {
            Label no = new Label("No active crops on this plot");
            no.setStyle("-fx-text-fill:#9ca3af;-fx-font-size:12;");
            plotDetailCropsBox.getChildren().add(no);
        } else {
            for (Crop c : plotCrops) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding:8;-fx-background-color:#f9fafb;-fx-background-radius:8;");
                Circle dot = new Circle(6, Color.web(getCropColor(c.getCropName())));
                VBox info = new VBox(2);
                Label n = new Label(c.getCropName());
                n.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#111827;");
                String stage = c.getGrowthStage().name();
                Label s = new Label(stage.charAt(0) + stage.substring(1).toLowerCase()
                        + (c.getPlantingDate() != null ? "  •  Planted " + c.getPlantingDate().format(dateFmt) : ""));
                s.setStyle("-fx-font-size:11;-fx-text-fill:#6b7280;");
                info.getChildren().addAll(n, s);
                row.getChildren().addAll(dot, info);
                plotDetailCropsBox.getChildren().add(row);
            }
        }

        // ── Conditions (latest sensor reading) ──
        try {
            smartfarm.dao.SensorDAO sensorDAO = new smartfarm.dao.SensorDAO();
            List<smartfarm.model.SensorReading> readings = sensorDAO.getRecentForPlot(plot.getPlotId(), 1);
            if (!readings.isEmpty()) {
                smartfarm.model.SensorReading r = readings.get(0);
                lblPlotTemp.setText(sm.formatTemp(r.getTemperature()));
                lblPlotTempStatus.setText(r.getTemperature() > 35 ? "High" : r.getTemperature() < 10 ? "Low" : "Normal");
                lblPlotTempStatus.setStyle("-fx-font-size:10;-fx-font-weight:bold;-fx-text-fill:" +
                        (r.getTemperature() > 35 || r.getTemperature() < 10 ? "#dc2626" : "#16a34a") + ";");

                lblPlotHumidity.setText(String.format("%.0f %%", r.getHumidity()));
                lblPlotHumStatus.setText(r.getHumidity() < 30 ? "Low" : r.getHumidity() > 80 ? "High" : "Normal");
                lblPlotHumStatus.setStyle("-fx-font-size:10;-fx-font-weight:bold;-fx-text-fill:" +
                        (r.getHumidity() < 30 || r.getHumidity() > 80 ? "#d97706" : "#16a34a") + ";");

                lblPlotSoilMoisture.setText(String.format("%.0f %%", r.getSoilMoisture()));
                lblPlotSoilStatus.setText(r.getSoilMoisture() < 20 ? "Dry" : r.getSoilMoisture() > 80 ? "Wet" : "Normal");
                lblPlotSoilStatus.setStyle("-fx-font-size:10;-fx-font-weight:bold;-fx-text-fill:" +
                        (r.getSoilMoisture() < 20 || r.getSoilMoisture() > 80 ? "#dc2626" : "#16a34a") + ";");

                lblPlotCondSource.setText("Last reading: " +
                        (r.getTimestamp() != null ? r.getTimestamp().format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) : "—"));
            } else {
                lblPlotTemp.setText("-- °C");
                lblPlotHumidity.setText("-- %");
                lblPlotSoilMoisture.setText("-- %");
                lblPlotCondSource.setText("No sensor data");
                lblPlotTempStatus.setText("—");
                lblPlotHumStatus.setText("—");
                lblPlotSoilStatus.setText("—");
            }
        } catch (SQLException e) {
            lblPlotCondSource.setText("Error loading data");
        }

        // ── Soil Profile ──
        soilProfileBox.getChildren().clear();
        addProfileRow(soilProfileBox, "Soil Type", plot.getSoilType() != null ? plot.getSoilType() : "Unknown");
        addProfileRow(soilProfileBox, "Plot Size", String.format("%.1f acres (%.2f ha)", plot.getSizeAcres(), plot.getSizeAcres() * 0.4047));
        addProfileRow(soilProfileBox, "Irrigation", plot.getIrrigationType() != null ? plot.getIrrigationType() : "None");
        addProfileRow(soilProfileBox, "Status", status);

        // ── Plot Summary ──
        plotSummaryBox.getChildren().clear();
        addProfileRow(plotSummaryBox, "Active Crops", String.valueOf(plotCrops.size()));
        addProfileRow(plotSummaryBox, "Manager ID", String.valueOf(plot.getManagerId()));
        addProfileRow(plotSummaryBox, "Created", plot.getCreatedAt() != null ? plot.getCreatedAt().toLocalDate().format(dateFmt) : "—");
        addProfileRow(plotSummaryBox, "Last Updated", plot.getUpdatedAt() != null ? plot.getUpdatedAt().toLocalDate().format(dateFmt) : "—");
    }

    private void addProfileRow(VBox container, String label, String value) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.setStyle("-fx-font-size:12;-fx-text-fill:#6b7280;");
        l.setPrefWidth(120);
        Label v = new Label(value);
        v.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#111827;");
        row.getChildren().addAll(l, v);
        container.getChildren().add(row);
    }

    // ═══════════════ DETAIL TABS ═══════════════

    @FXML private void onPlotTabOverview() { switchPlotTab(plotOverviewPane, plotTabOverview); }
    @FXML private void onPlotTabCrops()    { switchPlotTab(plotCropsPane, plotTabCrops); populateCropsTab(); }
    @FXML private void onPlotTabSensors()  { switchPlotTab(plotSensorsPane, plotTabSensors); populateSensorTab(); }
    @FXML private void onPlotTabNotes()    { switchPlotTab(plotNotesPane, plotTabNotes); }

    private void switchPlotTab(VBox activePane, Button activeTab) {
        VBox[] panes = {plotOverviewPane, plotCropsPane, plotSensorsPane, plotNotesPane};
        Button[] tabs = {plotTabOverview, plotTabCrops, plotTabSensors, plotTabNotes};
        for (VBox p : panes) { p.setVisible(false); p.setManaged(false); }
        for (Button t : tabs) { t.getStyleClass().remove("crop-tab-active"); }
        activePane.setVisible(true);
        activePane.setManaged(true);
        if (!activeTab.getStyleClass().contains("crop-tab-active")) {
            activeTab.getStyleClass().add("crop-tab-active");
        }
    }

    private void populateCropsTab() {
        if (selectedDetailPlot == null) return;
        plotCropsListBox.getChildren().clear();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        List<Crop> all = dbCrops.stream()
                .filter(c -> c.getPlotId() == selectedDetailPlot.getPlotId())
                .collect(Collectors.toList());
        if (all.isEmpty()) {
            Label no = new Label("No crops have been planted on this plot.");
            no.setStyle("-fx-text-fill:#9ca3af;-fx-font-size:13;");
            plotCropsListBox.getChildren().add(no);
        } else {
            for (Crop c : all) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding:10;-fx-background-color:#f9fafb;-fx-background-radius:8;");
                Circle dot = new Circle(6, Color.web(getCropColor(c.getCropName())));
                VBox info = new VBox(2);
                Label n = new Label(c.getCropName());
                n.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#111827;");
                String stage = c.getGrowthStage().name();
                String planted = c.getPlantingDate() != null ? c.getPlantingDate().format(dateFmt) : "—";
                Label detail = new Label(stage.charAt(0) + stage.substring(1).toLowerCase()
                        + "  •  Planted: " + planted);
                detail.setStyle("-fx-font-size:11;-fx-text-fill:#6b7280;");
                info.getChildren().addAll(n, detail);
                row.getChildren().addAll(dot, info);
                plotCropsListBox.getChildren().add(row);
            }
        }
    }

    private void populateSensorTab() {
        if (selectedDetailPlot == null) return;
        sensorHistoryBox.getChildren().clear();
        smartfarm.service.SettingsManager sm = smartfarm.service.SettingsManager.getInstance();
        try {
            smartfarm.dao.SensorDAO sensorDAO = new smartfarm.dao.SensorDAO();
            List<smartfarm.model.SensorReading> readings = sensorDAO.getRecentForPlot(selectedDetailPlot.getPlotId(), 20);
            if (readings.isEmpty()) {
                Label no = new Label("No sensor readings recorded for this plot.");
                no.setStyle("-fx-text-fill:#9ca3af;-fx-font-size:13;");
                sensorHistoryBox.getChildren().add(no);
            } else {
                // Header
                HBox header = new HBox(20);
                header.setStyle("-fx-padding:6 10;-fx-background-color:#f3f4f6;-fx-background-radius:6;");
                header.getChildren().addAll(
                        headerLabel("Timestamp", 140),
                        headerLabel("Temp", 80),
                        headerLabel("Humidity", 80),
                        headerLabel("Soil", 80));
                sensorHistoryBox.getChildren().add(header);

                for (smartfarm.model.SensorReading r : readings) {
                    HBox row = new HBox(20);
                    row.setStyle("-fx-padding:6 10;");
                    String time = r.getTimestamp() != null
                            ? r.getTimestamp().format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss"))
                            : "—";
                    row.getChildren().addAll(
                            dataLabel(time, 140),
                            dataLabel(sm.formatTemp(r.getTemperature()), 80),
                            dataLabel(String.format("%.0f%%", r.getHumidity()), 80),
                            dataLabel(String.format("%.0f%%", r.getSoilMoisture()), 80));
                    sensorHistoryBox.getChildren().add(row);
                }
            }
        } catch (SQLException e) {
            Label err = new Label("Failed to load sensor data: " + e.getMessage());
            err.setStyle("-fx-text-fill:#ef4444;-fx-font-size:12;");
            sensorHistoryBox.getChildren().add(err);
        }
    }

    private Label headerLabel(String text, double width) {
        Label l = new Label(text);
        l.setPrefWidth(width);
        l.setStyle("-fx-font-size:11;-fx-font-weight:bold;-fx-text-fill:#6b7280;");
        return l;
    }

    private Label dataLabel(String text, double width) {
        Label l = new Label(text);
        l.setPrefWidth(width);
        l.setStyle("-fx-font-size:12;-fx-text-fill:#374151;");
        return l;
    }

    private Plot findPlotById(int plotId) {
        return dbPlots.stream().filter(p -> p.getPlotId() == plotId).findFirst().orElse(null);
    }

    private Dialog<Plot> createPlotDialog(Plot existing, String currentCropName) {
        Dialog<Plot> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add New Plot" : "Edit Plot");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        nameField.setPromptText("Plot Name");

        TextField locationField = new TextField(existing != null ? existing.getLocation() : "");
        locationField.setPromptText("Location / Field");

        TextField sizeField = new TextField(existing != null ? String.valueOf(existing.getSizeAcres()) : "");
        sizeField.setPromptText("Size in acres");

        ComboBox<String> soilCombo = new ComboBox<>();
        soilCombo.getItems().addAll("Loamy", "Clay", "Sandy", "Silt", "Peaty", "Chalky", "Alluvial");
        soilCombo.setMaxWidth(Double.MAX_VALUE);
        if (existing != null && existing.getSoilType() != null) {
            soilCombo.setValue(existing.getSoilType());
        }
        soilCombo.setPromptText("Select Soil Type");

        // Crop selection — multi-select checkboxes for active (non-harvested) crops
        VBox cropCheckboxes = new VBox(4);
        cropCheckboxes.setStyle("-fx-padding: 6; -fx-border-color: #d1d5db; -fx-border-radius: 4; -fx-background-color: white; -fx-background-radius: 4;");
        List<CheckBox> cropChecks = new ArrayList<>();
        List<String> currentCropNames = currentCropName != null
                ? List.of(currentCropName.split(",\\s*")) : List.of();
        for (Crop c : dbCrops) {
            if (c.getGrowthStage() != Crop.GrowthStage.HARVESTED) {
                CheckBox cb = new CheckBox(c.getCropName());
                cb.setSelected(currentCropNames.contains(c.getCropName()));
                cropChecks.add(cb);
                cropCheckboxes.getChildren().add(cb);
            }
        }
        if (cropCheckboxes.getChildren().isEmpty()) {
            cropCheckboxes.getChildren().add(new Label("No active crops available"));
        }

        // Irrigation type
        ComboBox<String> irrigationCombo = new ComboBox<>();
        irrigationCombo.getItems().addAll("None", "Drip", "Sprinkler", "Flood", "Drone");
        irrigationCombo.setMaxWidth(Double.MAX_VALUE);
        String existingIrr = existing != null ? existing.getIrrigationType() : null;
        irrigationCombo.setValue(existingIrr != null ? existingIrr : "None");
        irrigationCombo.setPromptText("Select Irrigation");

        // Status
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Under Cultivation", "Available", "Fallow");
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        String existingStatus = existing != null ? existing.getStatus() : null;
        if (existingStatus != null) {
            statusCombo.setValue(existingStatus);
        } else {
            // Derive from crop: if crop is assigned, default to Under Cultivation
            statusCombo.setValue(currentCropName != null && !currentCropName.equals("None")
                    ? "Under Cultivation" : "Available");
        }
        statusCombo.setPromptText("Select Status");

        VBox form = new VBox(10,
                new Label("Plot Name:"), nameField,
                new Label("Location / Field:"), locationField,
                new Label("Size (acres):"), sizeField,
                new Label("Soil Type:"), soilCombo,
                new Label("Crop(s):"), cropCheckboxes,
                new Label("Status:"), statusCombo,
                new Label("Irrigation:"), irrigationCombo);
        form.setPadding(new Insets(20));
        form.setPrefWidth(380);

        ScrollPane sp = new ScrollPane(form);
        sp.setFitToWidth(true);
        sp.setPrefHeight(420);
        sp.setStyle("-fx-background-color: transparent;");
        dialog.getDialogPane().setContent(sp);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { showAlert("Validation", "Plot name is required"); return null; }
                String location = locationField.getText().trim();
                if (location.isEmpty()) { showAlert("Validation", "Location is required"); return null; }
                double size;
                try { size = Double.parseDouble(sizeField.getText().trim()); }
                catch (NumberFormatException e) { showAlert("Validation", "Invalid size value"); return null; }
                if (size <= 0) { showAlert("Validation", "Size must be greater than 0"); return null; }
                String soil = soilCombo.getValue();
                if (soil == null || soil.isEmpty()) { showAlert("Validation", "Please select a soil type"); return null; }

                // Store crop selections for post-save handling
                dialogSelectedCrops = cropChecks.stream()
                        .filter(CheckBox::isSelected)
                        .map(CheckBox::getText)
                        .collect(Collectors.toList());

                Plot plot = new Plot(name, location, size, soil, currentManagerId);
                String irr = irrigationCombo.getValue();
                plot.setIrrigationType(irr != null && !irr.equals("None") ? irr : null);
                plot.setStatus(statusCombo.getValue());
                return plot;
            }
            return null;
        });
        return dialog;
    }

    // ═══════════════ EXPORT ═══════════════

    @FXML
    private void onExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Plots");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("plots.csv");
        File file = chooser.showSaveDialog(btnExport.getScene().getWindow());
        if (file == null) return;

        try (FileWriter w = new FileWriter(file)) {
            w.write("Plot Name,Field,Crop,Area (ha),Status,Soil Type,Irrigation,Last Activity\n");
            for (PlotRecord rec : filteredRecords) {
                w.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                        rec.getPlotName(), rec.getField(), rec.getCrop(), rec.getArea(),
                        rec.getStatus(), rec.getSoil(), rec.getIrrigation(), rec.getLastActivity()));
            }
            showInfo("Export Successful", "Exported " + filteredRecords.size() + " plots to " + file.getName());
        } catch (IOException e) {
            showAlert("Error", "Failed to export: " + e.getMessage());
        }
    }

    // ═══════════════ ALERTS ═══════════════

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }

    // ═══════════════ DATA MODEL ═══════════════

    public static class PlotRecord {
        private final int dbPlotId;
        private final String plotName, field, crop, area, status, soil, irrigation, lastActivity;

        public PlotRecord(int dbPlotId, String plotName, String field, String crop, String area,
                          String status, String soil, String irrigation, String lastActivity) {
            this.dbPlotId = dbPlotId;
            this.plotName = plotName;
            this.field = field;
            this.crop = crop;
            this.area = area;
            this.status = status;
            this.soil = soil;
            this.irrigation = irrigation;
            this.lastActivity = lastActivity;
        }

        public int getDbPlotId() { return dbPlotId; }
        public String getPlotId() { return plotName; }
        public String getPlotName() { return plotName; }
        public String getField() { return field; }
        public String getCrop() { return crop; }
        public String getArea() { return area; }
        public String getStatus() { return status; }
        public String getSoil() { return soil; }
        public String getIrrigation() { return irrigation; }
        public String getLastActivity() { return lastActivity; }
    }
}
