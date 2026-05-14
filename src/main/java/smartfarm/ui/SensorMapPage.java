package smartfarm.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import smartfarm.dao.PlotDAO;
import smartfarm.dao.SensorDAO;
import smartfarm.model.Plot;
import smartfarm.model.SensorReading;
import smartfarm.service.LiveSensorData;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SensorMapPage extends VBox {

    // 6 field polygons – normalized [0,1] scaled to canvas at draw time.
    // Each entry: [xs array, ys array] for a 4-vertex polygon.
    // Slight diagonal edges give a realistic aerial-farm perspective.
    private static final double[][][] FIELDS = {
        {{0.03, 0.47, 0.46, 0.03}, {0.03, 0.04, 0.31, 0.30}},  // top-left
        {{0.53, 0.97, 0.97, 0.54}, {0.04, 0.03, 0.30, 0.31}},  // top-right
        {{0.04, 0.46, 0.44, 0.03}, {0.34, 0.36, 0.64, 0.62}},  // mid-left
        {{0.54, 0.96, 0.97, 0.56}, {0.36, 0.34, 0.62, 0.64}},  // mid-right
        {{0.03, 0.45, 0.47, 0.03}, {0.66, 0.68, 0.97, 0.97}},  // bot-left
        {{0.55, 0.97, 0.97, 0.53}, {0.68, 0.66, 0.97, 0.97}},  // bot-right
    };

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a");

    // ── UI fields ──
    private final ComboBox<String> filterCombo = new ComboBox<>();
    private final Label lblLastUpdated = new Label();
    private final GridPane grid = new GridPane();

    private Canvas tempCanvas, humidCanvas, soilCanvas, allCanvas;
    private VBox tempCard, humidCard, soilCard, allCard;

    private final Label lblTempMin = new Label("26°C"), lblTempMax = new Label("35°C");
    private final Label lblHumMin  = new Label("40%"),  lblHumMax  = new Label("80%");
    private final Label lblSoilMin = new Label("10%"),  lblSoilMax = new Label("40%");

    // ── Data ──
    private final List<Plot> plots  = new ArrayList<>();
    private final Map<Integer, Float> tempData  = new LinkedHashMap<>();
    private final Map<Integer, Float> humidData = new LinkedHashMap<>();
    private final Map<Integer, Float> soilData  = new LinkedHashMap<>();

    private final SensorDAO sensorDAO = new SensorDAO();
    private final PlotDAO   plotDAO   = new PlotDAO();
    private Timeline refreshTimer;

    // ═══════════════════════════════ CONSTRUCTOR ═══════════════════════════════

    public SensorMapPage() {
        setPadding(new Insets(24));
        setStyle("-fx-background-color:#f5f7fa;");
        buildUI();
        loadData();
        subscribeToLive();
        startAutoRefresh();
    }

    // ═══════════════════════════════ UI BUILD ═══════════════════════════════

    private void buildUI() {
        // ── Header ──
        Label title = new Label("Sensor Map");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#111827;");
        Label sub = new Label("Visualize real-time data from your field sensors");
        sub.setStyle("-fx-font-size:13px;-fx-text-fill:#6b7280;");
        VBox titleBox = new VBox(2, title, sub);

        filterCombo.getItems().addAll("All Sensors", "Temperature", "Humidity", "Soil Moisture");
        filterCombo.setValue("All Sensors");
        filterCombo.setPrefWidth(200);
        filterCombo.setStyle("-fx-font-size:13px;");
        filterCombo.setOnAction(e -> applyFilter());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(titleBox, headerSpacer, filterCombo);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 20, 0));

        // ── Canvases ──
        tempCanvas  = new Canvas();
        humidCanvas = new Canvas();
        soilCanvas  = new Canvas();
        allCanvas   = new Canvas();

        // ── Cards ──
        tempCard  = buildCard(tempCanvas,  "Temperature",   "Air temperature across all plots",
                               "#FFE566", "#FF6B00", "#CC0000", lblTempMin, lblTempMax);
        humidCard = buildCard(humidCanvas, "Humidity",      "Relative humidity across all plots",
                               "#B8E2FF", "#3399FF", "#003DB3", lblHumMin,  lblHumMax);
        soilCard  = buildCard(soilCanvas,  "Soil Moisture", "Volumetric water content in soil",
                               "#EDD9A3", "#A07840", "#5C3418", lblSoilMin, lblSoilMax);
        allCard   = buildCard(allCanvas,   "All Sensors",   "Combined view of all sensor data",
                               "#4CAF50", "#FF9800", "#F44336",
                               new Label("Low"), new Label("High"));

        // ── 2×2 Grid ──
        ColumnConstraints cc1 = new ColumnConstraints();
        cc1.setPercentWidth(50);
        cc1.setHgrow(Priority.ALWAYS);
        ColumnConstraints cc2 = new ColumnConstraints();
        cc2.setPercentWidth(50);
        cc2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc1, cc2);
        grid.setHgap(16);
        grid.setVgap(16);
        showAllCards();

        // ── Footer ──
        Label lblAuto = new Label("Data updates automatically every 5 minutes");
        lblAuto.setStyle("-fx-font-size:11px;-fx-text-fill:#9ca3af;");
        lblLastUpdated.setStyle("-fx-font-size:11px;-fx-text-fill:#9ca3af;");
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(lblAuto, footerSpacer, lblLastUpdated);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(14, 20, 14, 20));
        footer.setStyle(
            "-fx-background-color:white;" +
            "-fx-background-radius:0 0 12 12;" +
            "-fx-border-color:#e5e7eb;" +
            "-fx-border-width:1 0 0 0;"
        );

        // ── Outer card wrapper ──
        VBox content = new VBox(header, grid);
        content.setPadding(new Insets(24));
        VBox.setVgrow(grid, Priority.ALWAYS);

        VBox outerCard = new VBox(content, footer);
        outerCard.setStyle(
            "-fx-background-color:white;" +
            "-fx-background-radius:12;" +
            "-fx-border-color:#e5e7eb;" +
            "-fx-border-radius:12;" +
            "-fx-border-width:1;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.06),8,0,0,2);"
        );
        VBox.setVgrow(outerCard, Priority.ALWAYS);
        VBox.setVgrow(content, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(outerCard);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().add(scroll);
    }

    private VBox buildCard(Canvas canvas, String title, String subtitle,
                            String c1, String c2, String c3, Label minLbl, Label maxLbl) {
        // Canvas area
        StackPane canvasPane = new StackPane(canvas);
        canvasPane.setStyle("-fx-background-color:#183B18;-fx-background-radius:10 10 0 0;");
        canvasPane.setMinHeight(270);
        canvasPane.setPrefHeight(270);

        canvas.setHeight(270);
        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.widthProperty().addListener((obs, o, n) -> {
            if (n.doubleValue() > 20) redrawAll();
        });

        // Title + subtitle
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight:700;-fx-font-size:14px;-fx-text-fill:#111827;");
        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#6b7280;");

        // Gradient bar
        Rectangle gradBar = new Rectangle(120, 8);
        gradBar.setArcWidth(8);
        gradBar.setArcHeight(8);
        gradBar.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web(c1)),
                new Stop(0.5, Color.web(c2)),
                new Stop(1.0, Color.web(c3))));
        minLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#6b7280;");
        maxLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#6b7280;");
        HBox gradRow = new HBox(6, minLbl, gradBar, maxLbl);
        gradRow.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(titleLbl, spacer, gradRow);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox infoBar = new VBox(5, topRow, subLbl);
        infoBar.setPadding(new Insets(12, 16, 14, 16));

        VBox card = new VBox(canvasPane, infoBar);
        card.setStyle(
            "-fx-background-color:white;" +
            "-fx-border-color:#e5e7eb;" +
            "-fx-border-radius:10;" +
            "-fx-background-radius:10;" +
            "-fx-border-width:1;"
        );
        GridPane.setFillWidth(card, true);
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ═══════════════════════════════ DATA ═══════════════════════════════

    private void loadData() {
        Thread t = new Thread(() -> {
            try {
                List<Plot> loaded = plotDAO.getAll();
                Map<Integer, Float> temp  = new LinkedHashMap<>();
                Map<Integer, Float> humid = new LinkedHashMap<>();
                Map<Integer, Float> soil  = new LinkedHashMap<>();

                for (Plot p : loaded) {
                    List<SensorReading> readings = sensorDAO.getRecentForPlot(p.getPlotId(), 1);
                    if (!readings.isEmpty()) {
                        SensorReading r = readings.get(0);
                        temp.put(p.getPlotId(),  r.getTemperature());
                        humid.put(p.getPlotId(), r.getHumidity());
                        if (!Float.isNaN(r.getSoilMoisture()))
                            soil.put(p.getPlotId(), r.getSoilMoisture());
                    }
                }

                Platform.runLater(() -> {
                    plots.clear();      plots.addAll(loaded);
                    tempData.clear();   tempData.putAll(temp);
                    humidData.clear();  humidData.putAll(humid);
                    soilData.clear();   soilData.putAll(soil);
                    updateLegends();
                    redrawAll();
                    lblLastUpdated.setText("Last updated: " + LocalDateTime.now().format(FMT));
                });
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, "SensorMapLoad");
        t.setDaemon(true);
        t.start();
    }

    private void updateLegends() {
        if (!tempData.isEmpty()) {
            lblTempMin.setText(String.format("%.0f°C", dataMin(tempData)));
            lblTempMax.setText(String.format("%.0f°C", dataMax(tempData)));
        }
        if (!humidData.isEmpty()) {
            lblHumMin.setText(String.format("%.0f%%", dataMin(humidData)));
            lblHumMax.setText(String.format("%.0f%%", dataMax(humidData)));
        }
        if (!soilData.isEmpty()) {
            lblSoilMin.setText(String.format("%.0f%%", dataMin(soilData)));
            lblSoilMax.setText(String.format("%.0f%%", dataMax(soilData)));
        }
    }

    // ═══════════════════════════════ DRAWING ═══════════════════════════════

    private void redrawAll() {
        float[] tv = fieldValues(tempData,  18f, 45f);
        float[] hv = fieldValues(humidData, 10f, 100f);
        float[] sv = fieldValues(soilData,  0f,  100f);
        float[] av = allValues(tv, hv, sv);

        drawCanvas(tempCanvas,  tv, Color.web("#FFE566"), Color.web("#FF6B00"), Color.web("#CC0000"));
        drawCanvas(humidCanvas, hv, Color.web("#B8E2FF"), Color.web("#3399FF"), Color.web("#003DB3"));
        drawCanvas(soilCanvas,  sv, Color.web("#EDD9A3"), Color.web("#A07840"), Color.web("#5C3418"));
        drawCanvas(allCanvas,   av, Color.web("#4CAF50"), Color.web("#FF9800"), Color.web("#F44336"));
    }

    private void drawCanvas(Canvas canvas, float[] values, Color lo, Color mi, Color hi) {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w < 20 || h < 20) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // ── Forest background ──
        gc.setFill(Color.web("#183B18"));
        gc.fillRect(0, 0, w, h);
        drawForestTexture(gc, w, h);

        // ── Field paths (farm roads between plots) ──
        gc.setFill(Color.web("#2D5F2D", 0.55));
        gc.fillRect(w * 0.485, 0, w * 0.030, h);   // vertical centre road
        gc.fillRect(0, h * 0.315, w, h * 0.028);   // horizontal top road
        gc.fillRect(0, h * 0.635, w, h * 0.030);   // horizontal bottom road

        // ── Fields ──
        for (int i = 0; i < FIELDS.length; i++) {
            double[] xs = scale(FIELDS[i][0], w);
            double[] ys = scale(FIELDS[i][1], h);
            Color fieldColor = lerp(values[i], lo, mi, hi);
            drawField(gc, xs, ys, fieldColor, i);
        }

        // ── Vignette ──
        drawVignette(gc, w, h);
    }

    private void drawField(GraphicsContext gc, double[] xs, double[] ys, Color color, int seed) {
        // Base fill
        gc.setFill(color);
        gc.fillPolygon(xs, ys, xs.length);

        // Organic texture: soft light/dark blobs simulate crop variation
        Random rng = new Random(seed * 137L + 31);
        double x0 = Arrays.stream(xs).min().orElse(0);
        double x1 = Arrays.stream(xs).max().orElse(1);
        double y0 = Arrays.stream(ys).min().orElse(0);
        double y1 = Arrays.stream(ys).max().orElse(1);
        for (int k = 0; k < 18; k++) {
            double cx = x0 + rng.nextDouble() * (x1 - x0);
            double cy = y0 + rng.nextDouble() * (y1 - y0);
            double r  = 7 + rng.nextDouble() * 22;
            double a  = 0.04 + rng.nextDouble() * 0.11;
            gc.setFill(rng.nextBoolean() ? Color.web("#ffffff", a) : Color.web("#000000", a));
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
        // Field border
        gc.setStroke(Color.web("#ffffff", 0.22));
        gc.setLineWidth(1.3);
        gc.strokePolygon(xs, ys, xs.length);
    }

    private void drawForestTexture(GraphicsContext gc, double w, double h) {
        String[] shades = {"#1A4A1A", "#0F3B0F", "#225522", "#183C18", "#2A5E2A"};
        Random rng = new Random(77);
        for (int i = 0; i < 160; i++) {
            double x, y;
            // Concentrate trees around the 4 edges
            switch (i % 4) {
                case 0: x = rng.nextDouble() * w;           y = rng.nextDouble() * h * 0.04; break;
                case 1: x = rng.nextDouble() * w;           y = h - rng.nextDouble() * h * 0.04; break;
                case 2: x = rng.nextDouble() * w * 0.04;   y = rng.nextDouble() * h; break;
                default:x = w - rng.nextDouble() * w * 0.04;y = rng.nextDouble() * h; break;
            }
            double r = 4 + rng.nextDouble() * 11;
            double a = 0.5 + rng.nextDouble() * 0.5;
            gc.setFill(Color.web(shades[rng.nextInt(shades.length)], a));
            gc.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }

    private void drawVignette(GraphicsContext gc, double w, double h) {
        int depth = 18;
        for (int t = 0; t < depth; t++) {
            double a = 0.012 * (depth - t);
            gc.setFill(Color.web("#000000", a));
            gc.fillRect(t, 0, 1, h);        // left
            gc.fillRect(w - t - 1, 0, 1, h);// right
            gc.fillRect(0, t, w, 1);        // top
            gc.fillRect(0, h - t - 1, w, 1);// bottom
        }
    }

    // ═══════════════════════════════ HELPERS ═══════════════════════════════

    private float[] fieldValues(Map<Integer, Float> data, float min, float max) {
        float[] vals = new float[FIELDS.length];
        if (data.isEmpty()) {
            // Plausible demo spread so the page isn't blank on first launch
            float[] demo = {0.35f, 0.68f, 0.50f, 0.72f, 0.42f, 0.58f};
            return demo;
        }
        List<Float> readings = new ArrayList<>(data.values());
        for (int i = 0; i < vals.length; i++) {
            float raw = readings.get(i % readings.size());
            vals[i] = Math.max(0f, Math.min(1f, (raw - min) / (max - min)));
        }
        return vals;
    }

    private float[] allValues(float[] t, float[] h, float[] s) {
        float[] result = new float[FIELDS.length];
        for (int i = 0; i < FIELDS.length; i++) {
            // Deviation from optimal: t≈40%, h≈50%, s≈40% of range
            float ts = Math.abs(t[i] - 0.40f) * 2.5f;
            float hs = Math.abs(h[i] - 0.50f) * 2.0f;
            float ss = Math.abs(s[i] - 0.40f) * 2.5f;
            result[i] = Math.min(1f, (ts + hs + ss) / 3f);
        }
        return result;
    }

    private Color lerp(float t, Color lo, Color mi, Color hi) {
        if (t <= 0.5f) return lo.interpolate(mi, t * 2f);
        return mi.interpolate(hi, (t - 0.5f) * 2f);
    }

    private double[] scale(double[] norm, double size) {
        double[] out = new double[norm.length];
        for (int i = 0; i < norm.length; i++) out[i] = norm[i] * size;
        return out;
    }

    private float dataMin(Map<Integer, Float> m) {
        return m.values().stream().min(Float::compareTo).orElse(0f);
    }

    private float dataMax(Map<Integer, Float> m) {
        return m.values().stream().max(Float::compareTo).orElse(100f);
    }

    // ═══════════════════════════════ FILTER ═══════════════════════════════

    private void applyFilter() {
        String sel = filterCombo.getValue();
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();

        if ("All Sensors".equals(sel)) {
            showAllCards();
        } else {
            // Single-card full-width view
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
            VBox card = switch (sel) {
                case "Temperature"  -> tempCard;
                case "Humidity"     -> humidCard;
                case "Soil Moisture"-> soilCard;
                default             -> allCard;
            };
            grid.add(card, 0, 0);
        }
        redrawAll();
    }

    private void showAllCards() {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        ColumnConstraints cc1 = new ColumnConstraints();
        cc1.setPercentWidth(50); cc1.setHgrow(Priority.ALWAYS);
        ColumnConstraints cc2 = new ColumnConstraints();
        cc2.setPercentWidth(50); cc2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc1, cc2);
        grid.add(tempCard,  0, 0);
        grid.add(humidCard, 1, 0);
        grid.add(soilCard,  0, 1);
        grid.add(allCard,   1, 1);
    }

    // ═══════════════════════════════ LIVE & REFRESH ═══════════════════════════════

    private void subscribeToLive() {
        LiveSensorData live = LiveSensorData.getInstance();
        live.temperatureProperty().addListener((obs, o, n) -> Platform.runLater(() -> {
            if (!plots.isEmpty()) tempData.put(plots.get(0).getPlotId(), n.floatValue());
            updateLegends();
            redrawAll();
            lblLastUpdated.setText("Last updated: " + LocalDateTime.now().format(FMT));
        }));
        live.humidityProperty().addListener((obs, o, n) -> Platform.runLater(() -> {
            if (!plots.isEmpty()) humidData.put(plots.get(0).getPlotId(), n.floatValue());
            updateLegends();
            redrawAll();
        }));
        live.soilMoistureProperty().addListener((obs, o, n) -> Platform.runLater(() -> {
            float v = n.floatValue();
            if (!plots.isEmpty() && !Float.isNaN(v)) {
                soilData.put(plots.get(0).getPlotId(), v);
                updateLegends();
                redrawAll();
            }
        }));
    }

    private void startAutoRefresh() {
        refreshTimer = new Timeline(new KeyFrame(Duration.minutes(5), e -> loadData()));
        refreshTimer.setCycleCount(Animation.INDEFINITE);
        refreshTimer.play();
    }
}
