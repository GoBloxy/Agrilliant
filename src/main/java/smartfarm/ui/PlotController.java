package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.CropDAO;
import smartfarm.dao.PlotDAO;
import smartfarm.model.Crop;
import smartfarm.model.Plot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlotController {

    @FXML private Label lblTotalPlots, lblCultivation, lblAvailable, lblFallow;
    @FXML private StackPane chartContainer;
    @FXML private Pane plotMapPane;
    @FXML private ToggleButton btnDrawMode;
    @FXML private Button btnDeletePlot;

    @FXML private TableView<PlotRecord> plotTable;
    @FXML private TableColumn<PlotRecord, String> colPlotId, colField, colCrop, colArea;
    @FXML private TableColumn<PlotRecord, String> colStatus, colSoil, colIrrigation, colLastActivity, colActions;

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

    @FXML
    public void initialize() {
        try {
            // Critical: make transparent Pane receive mouse events
            plotMapPane.setPickOnBounds(true);
            setupDonutChart();
            setupTable();
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
                if (drawingMode) {
                    deselectPlot();
                } else {
                    cancelCurrentDrawing();
                }
            });
        }

        // Click on map pane background
        plotMapPane.setOnMouseClicked(event -> {
            if (drawingMode) {
                if (event.getClickCount() == 2 && currentPoints.size() >= 3) {
                    finishPolygon();
                    event.consume();
                } else if (event.getClickCount() == 1) {
                    double x = event.getX();
                    double y = event.getY();
                    currentPoints.add(new double[]{x, y});

                    Circle dot = new Circle(x, y, 4, Color.WHITE);
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

        // Create final polygon
        Polygon poly = new Polygon();
        for (double[] pt : currentPoints) {
            poly.getPoints().addAll(pt[0], pt[1]);
        }
        String color = PLOT_COLORS[plotCounter % PLOT_COLORS.length];
        poly.setFill(Color.web(color, 0.4));
        poly.setStroke(Color.web(color));
        poly.setStrokeWidth(2);
        poly.setCursor(Cursor.HAND);
        plotMapPane.getChildren().add(poly);

        // Add centered label
        double cx = 0, cy = 0;
        for (double[] pt : currentPoints) { cx += pt[0]; cy += pt[1]; }
        cx /= currentPoints.size();
        cy /= currentPoints.size();

        Label lbl = new Label("Plot " + plotCounter);
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
        currentPoints.clear();

        // Exit draw mode
        drawingMode = false;
        if (btnDrawMode != null) btnDrawMode.setSelected(false);
        plotMapPane.setCursor(Cursor.DEFAULT);
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

    private void setupDonutChart() {
        javafx.scene.shape.Arc arc1 = new javafx.scene.shape.Arc(65, 65, 50, 50, 90, 236);
        arc1.setFill(Color.TRANSPARENT);
        arc1.setStroke(Color.web("#28a745"));
        arc1.setStrokeWidth(12);
        
        javafx.scene.shape.Arc arc2 = new javafx.scene.shape.Arc(65, 65, 50, 50, 330, 56);
        arc2.setFill(Color.TRANSPARENT);
        arc2.setStroke(Color.web("#ffc107"));
        arc2.setStrokeWidth(12);
        
        javafx.scene.shape.Arc arc3 = new javafx.scene.shape.Arc(65, 65, 50, 50, 30, 56);
        arc3.setFill(Color.TRANSPARENT);
        arc3.setStroke(Color.web("#6f42c1"));
        arc3.setStrokeWidth(12);

        javafx.scene.layout.Pane arcPane = new javafx.scene.layout.Pane();
        arcPane.setPrefSize(130, 130);
        arcPane.getChildren().addAll(arc1, arc2, arc3);
        
        chartContainer.getChildren().add(0, arcPane);
    }

    // ═══════════════ TABLE ═══════════════

    private void setupTable() {
        plotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colPlotId.setResizable(false);
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

                // Colored dot matching crop type
                Circle dot = new Circle(5);
                switch (item) {
                    case "Wheat":    dot.setFill(Color.web("#f59e0b")); break; // amber
                    case "Maize":    dot.setFill(Color.web("#eab308")); break; // yellow
                    case "Tomatoes": dot.setFill(Color.web("#ef4444")); break; // red
                    case "Beans":    dot.setFill(Color.web("#22c55e")); break; // green
                    default:         dot.setFill(Color.web("#9ca3af")); break; // gray
                }

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

        // ── Actions column: view / edit / more icons ──
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

                FontIcon edit = new FontIcon("fth-edit-2");
                edit.setIconSize(14); edit.setIconColor(Color.web("#6b7280"));
                edit.setCursor(Cursor.HAND);

                FontIcon more = new FontIcon("fth-more-vertical");
                more.setIconSize(14); more.setIconColor(Color.web("#6b7280"));
                more.setCursor(Cursor.HAND);

                box.getChildren().addAll(eye, edit, more);
                setGraphic(box);
                setText(null);
            }
        });

        // ── Data from database ──
        loadPlotData();
    }

    // ═══════════════ DB DATA LOADING ═══════════════

    private void loadPlotData() {
        PlotDAO plotDAO = new PlotDAO();
        CropDAO cropDAO = new CropDAO();
        try {
            List<Plot> plots = plotDAO.getAll();
            List<Crop> crops = cropDAO.getAll();

            // Build a map: plotId → crop name
            Map<Integer, String> plotCropMap = new HashMap<>();
            for (Crop c : crops) {
                if (c.getGrowthStage() != Crop.GrowthStage.HARVESTED) {
                    plotCropMap.put(c.getPlotId(), c.getCropName());
                }
            }

            ObservableList<PlotRecord> records = FXCollections.observableArrayList();
            int cultivation = 0, available = 0, fallow = 0;

            for (Plot p : plots) {
                String cropName = plotCropMap.getOrDefault(p.getPlotId(), "—");
                boolean hasCrop = !cropName.equals("—");
                String status = hasCrop ? "Under Cultivation" : "Available";
                if (hasCrop) cultivation++; else available++;

                String plotLabel = p.getName() + " - " + p.getLocation();
                String area = String.format("%.1f", p.getSizeAcres());
                String updated = p.getUpdatedAt() != null
                        ? p.getUpdatedAt().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
                        : "—";

                records.add(new PlotRecord(
                        plotLabel, p.getLocation(), cropName, area,
                        status, p.getSoilType(), hasCrop ? "Drip" : "—", updated
                ));
            }

            plotTable.setItems(records);

            // Update summary cards
            lblTotalPlots.setText(String.valueOf(plots.size()));
            lblCultivation.setText(String.valueOf(cultivation));
            lblAvailable.setText(String.valueOf(available));
            lblFallow.setText(String.valueOf(fallow));

        } catch (SQLException e) {
            System.err.println("Failed to load plot data: " + e.getMessage());
            plotTable.setPlaceholder(new Label("Could not load plots from database"));
        }
    }

    // ═══════════════ DATA MODEL ═══════════════

    public static class PlotRecord {
        private final String plotId, field, crop, area, status, soil, irrigation, lastActivity;

        public PlotRecord(String plotId, String field, String crop, String area,
                          String status, String soil, String irrigation, String lastActivity) {
            this.plotId = plotId;
            this.field = field;
            this.crop = crop;
            this.area = area;
            this.status = status;
            this.soil = soil;
            this.irrigation = irrigation;
            this.lastActivity = lastActivity;
        }

        public String getPlotId() { return plotId; }
        public String getField() { return field; }
        public String getCrop() { return crop; }
        public String getArea() { return area; }
        public String getStatus() { return status; }
        public String getSoil() { return soil; }
        public String getIrrigation() { return irrigation; }
        public String getLastActivity() { return lastActivity; }
    }
}
