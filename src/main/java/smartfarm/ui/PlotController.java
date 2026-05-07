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
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.RingPlot;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.chart.fx.ChartViewer;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

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
    private int plotCounter = 10; // next plot number

    // Colors for drawn plots
    private static final String[] PLOT_COLORS = {
        "#4caf50", "#66bb6a", "#81c784", "#a5d6a7", "#388e3c"
    };

    @FXML
    public void initialize() {
        setupDonutChart();
        setupTable();
        setupDrawingTool();
    }

    // ═══════════════ DRAWING TOOL ═══════════════

    private void setupDrawingTool() {
        // Toggle draw mode
        if (btnDrawMode != null) {
            btnDrawMode.selectedProperty().addListener((obs, oldVal, newVal) -> {
                drawingMode = newVal;
                plotMapPane.setCursor(drawingMode ? Cursor.CROSSHAIR : Cursor.DEFAULT);
                if (!drawingMode) {
                    cancelCurrentDrawing();
                }
            });
        }

        // Click on map to add polygon points
        plotMapPane.setOnMouseClicked(event -> {
            if (!drawingMode) return;

            if (event.getClickCount() == 2 && currentPoints.size() >= 3) {
                // Double-click: close the polygon
                finishPolygon();
                event.consume();
            } else if (event.getClickCount() == 1) {
                // Single click: add a point
                double x = event.getX();
                double y = event.getY();
                currentPoints.add(new double[]{x, y});

                // Draw a dot at the click location
                Circle dot = new Circle(x, y, 4, Color.WHITE);
                dot.setStroke(Color.web("#2e7d32"));
                dot.setStrokeWidth(2);
                currentDots.add(dot);
                plotMapPane.getChildren().add(dot);

                // Update preview polygon
                updatePreview();
            }
        });

        // Delete button
        if (btnDeletePlot != null) {
            btnDeletePlot.setOnAction(e -> {
                // Remove last drawn polygon if any
                if (!plotMapPane.getChildren().isEmpty()) {
                    // Remove from end until we find a non-polygon
                    for (int i = plotMapPane.getChildren().size() - 1; i >= 0; i--) {
                        if (plotMapPane.getChildren().get(i) instanceof Polygon) {
                            plotMapPane.getChildren().remove(i);
                            // Also remove associated label (the one right after)
                            if (i < plotMapPane.getChildren().size() &&
                                plotMapPane.getChildren().get(i) instanceof Label) {
                                plotMapPane.getChildren().remove(i);
                            }
                            break;
                        }
                    }
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
            // Insert behind dots
            int insertIdx = plotMapPane.getChildren().size() - currentDots.size();
            plotMapPane.getChildren().add(Math.max(0, insertIdx), currentPreview);
        }
    }

    private void finishPolygon() {
        // Remove preview and dots
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
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Under Cultivation", 8);
        dataset.setValue("Available", 2);
        dataset.setValue("Fallow", 2);

        RingPlot plot = new RingPlot(dataset);
        plot.setSectionDepth(0.35);
        plot.setLabelGenerator(null);
        plot.setSectionPaint("Under Cultivation", new java.awt.Color(40, 167, 69));
        plot.setSectionPaint("Available", new java.awt.Color(255, 193, 7));
        plot.setSectionPaint("Fallow", new java.awt.Color(111, 66, 193));
        plot.setBackgroundPaint(null);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setSeparatorStroke(new java.awt.BasicStroke(3.0f));
        plot.setSeparatorPaint(java.awt.Color.WHITE);

        JFreeChart chart = new JFreeChart(null, null, plot, false);
        chart.setBackgroundPaint(null);

        ChartViewer viewer = new ChartViewer(chart);
        viewer.setPrefSize(150, 150);
        chartContainer.getChildren().add(0, viewer);
    }

    // ═══════════════ TABLE ═══════════════

    private void setupTable() {
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

        // ── Data ──
        ObservableList<PlotRecord> data = FXCollections.observableArrayList(
            new PlotRecord("Plot 1 - West Field",    "West Field",    "Wheat",    "2.5", "Under Cultivation", "Loamy",      "Drip",      "May 21, 2025\nIrrigation"),
            new PlotRecord("Plot 2 - East Field",    "East Field",    "Maize",    "3.0", "Under Cultivation", "Sandy Loam", "Sprinkler", "May 20, 2025\nFertilizer Applied"),
            new PlotRecord("Plot 3 - North Field",   "North Field",   "Tomatoes", "2.0", "Under Cultivation", "Loamy",      "Drip",      "May 21, 2025\nPest Control"),
            new PlotRecord("Plot 4 - South Field",   "South Field",   "Beans",    "1.5", "Under Cultivation", "Clay Loam",  "Drip",      "May 18, 2025\nWeeding"),
            new PlotRecord("Plot 5 - Central Field", "Central Field", "—",        "2.5", "Fallow",            "Loamy",      "—",         "May 10, 2025\nField Cleared"),
            new PlotRecord("Plot 6 - West Field",    "West Field",    "Wheat",    "2.3", "Under Cultivation", "Loamy",      "Sprinkler", "May 21, 2025\nIrrigation"),
            new PlotRecord("Plot 7 - East Field",    "East Field",    "Maize",    "2.7", "Under Cultivation", "Sandy Loam", "Drip",      "May 20, 2025\nFertilizer Applied"),
            new PlotRecord("Plot 8 - South Field",   "South Field",   "—",        "1.8", "Available",         "Loamy",      "—",         "—")
        );
        plotTable.setItems(data);
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
