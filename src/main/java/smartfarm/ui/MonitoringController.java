package smartfarm.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import smartfarm.dao.SensorDAO;
import javafx.util.Duration;
import smartfarm.dao.PlotDAO;
import smartfarm.model.Plot;
import smartfarm.model.SensorReading;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SensorService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MonitoringController {

    @FXML private ComboBox<String> cmbFields;
    @FXML private ComboBox<String> cmbPlots;
    @FXML private DatePicker datePicker;
    @FXML private MenuButton btnAutoRefresh;

    @FXML private Label lblTemp, lblTempSub;
    @FXML private Label lblHum, lblHumSub;
    @FXML private Label lblSoil, lblSoilSub;

    @FXML private ComboBox<String> cmbChartPeriod;
    @FXML private LineChart<String, Number> trendChart;

    @FXML private ComboBox<String> cmbMapSensors;
    @FXML private Canvas mapCanvas;
    @FXML private StackPane mapCanvasPane;

    @FXML private TableView<SensorRow> sensorTable;
    @FXML private TableColumn<SensorRow, String> colPlot;
    @FXML private TableColumn<SensorRow, String> colType;
    @FXML private TableColumn<SensorRow, String> colValue;
    @FXML private TableColumn<SensorRow, String> colStatus;
    @FXML private TableColumn<SensorRow, String> colUpdated;

    @FXML private Label lblReadingsCount;

    @FXML private PieChart statusChart;
    @FXML private Label lblNormalCount, lblWarningCount, lblCriticalCount, lblOfflineCount;
    @FXML private Label lblTotalSensors;

    // ── Sensor-map canvas ──
    private static final double[][][] FIELDS = {
        {{0.03, 0.47, 0.46, 0.03}, {0.03, 0.04, 0.31, 0.30}},
        {{0.53, 0.97, 0.97, 0.54}, {0.04, 0.03, 0.30, 0.31}},
        {{0.04, 0.46, 0.44, 0.03}, {0.34, 0.36, 0.64, 0.62}},
        {{0.54, 0.96, 0.97, 0.56}, {0.36, 0.34, 0.62, 0.64}},
        {{0.03, 0.45, 0.47, 0.03}, {0.66, 0.68, 0.97, 0.97}},
        {{0.55, 0.97, 0.97, 0.53}, {0.68, 0.66, 0.97, 0.97}},
    };
    private static final float[] DEMO_VALUES = {0.35f, 0.68f, 0.50f, 0.72f, 0.42f, 0.58f};
    private float[] mapFieldValues = DEMO_VALUES.clone();
    private String mapMode = "All Sensors";

    private static final int MAX_CHART_POINTS = 40;
    private XYChart.Series<String, Number> tempSeries;
    private XYChart.Series<String, Number> humSeries;
    private XYChart.Series<String, Number> soilSeries;

    private PieChart.Data pieNormal, pieWarning, pieCritical, pieOffline;
    private Timeline autoRefreshTimeline;

    private Map<Integer, Plot> plotCache = new HashMap<>();
    private Map<String, List<Integer>> fieldToDeviceIds = new HashMap<>();

    @FXML
    public void initialize() {
        loadPlotData();

        cmbChartPeriod.setItems(FXCollections.observableArrayList("24 Hours", "7 Days", "30 Days"));
        cmbChartPeriod.getSelectionModel().selectFirst();

        cmbMapSensors.setItems(FXCollections.observableArrayList("All Sensors", "Temperature", "Humidity", "Soil Moisture"));
        cmbMapSensors.getSelectionModel().selectFirst();
        cmbMapSensors.setOnAction(e -> {
            mapMode = cmbMapSensors.getValue() != null ? cmbMapSensors.getValue() : "All Sensors";
            loadMapData();
        });
        initMapCanvas();

        // Filter change listeners
        cmbFields.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            updatePlotsComboForField(n);
            loadSensorReadings();
        });
        cmbPlots.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> loadSensorReadings());
        datePicker.valueProperty().addListener((obs, o, n) -> loadSensorReadings());
        cmbChartPeriod.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> reloadChart());

        setupTrendChart();
        setupStatusChart();
        setupTable();
        setupAutoRefresh();
        subscribeLiveSensor();
        loadHistoricalChart();
    }

    private void loadPlotData() {
        PlotDAO plotDAO = new PlotDAO();
        ObservableList<String> plotNames = FXCollections.observableArrayList("All Plots");

        try {
            List<Plot> plots = plotDAO.getAll();
            for (Plot p : plots) {
                plotCache.put(p.getPlotId(), p);
                plotNames.add(p.getName());
                String loc = p.getLocation() != null ? p.getLocation() : "Unknown";
                fieldToDeviceIds.computeIfAbsent(loc, k -> new java.util.ArrayList<>()).add(p.getPlotId());
            }
        } catch (SQLException e) {
            System.err.println("Failed to load plots: " + e.getMessage());
        }

        // Build fields list: "All Fields" + unique locations from DB
        ObservableList<String> fieldNames = FXCollections.observableArrayList("All Fields");
        for (String loc : fieldToDeviceIds.keySet()) {
            if (!fieldNames.contains(loc)) fieldNames.add(loc);
        }

        cmbFields.setItems(fieldNames);
        cmbFields.getSelectionModel().selectFirst();
        cmbPlots.setItems(plotNames);
        cmbPlots.getSelectionModel().selectFirst();
        datePicker.setValue(null); // No date filter by default — show all readings
    }

    private void updatePlotsComboForField(String field) {
        ObservableList<String> plotNames = FXCollections.observableArrayList("All Plots");
        if (field == null || field.equals("All Fields")) {
            for (Plot p : plotCache.values()) plotNames.add(p.getName());
        } else {
            List<Integer> ids = fieldToDeviceIds.getOrDefault(field, List.of());
            for (int id : ids) {
                Plot p = plotCache.get(id);
                if (p != null) plotNames.add(p.getName());
            }
        }
        cmbPlots.setItems(plotNames);
        cmbPlots.getSelectionModel().selectFirst();
    }

    // ═══════════════ LIVE SENSOR SUBSCRIPTION ═══════════════

    private void subscribeLiveSensor() {
        LiveSensorData live = LiveSensorData.getInstance();

        live.temperatureProperty().addListener((obs, oldVal, newVal) -> {
            float t = newVal.floatValue();
            lblTemp.setText(String.format("%.1f", t));
            lblTempSub.setText(t > 35 ? "High" : t < 10 ? "Low" : "Normal");
            addChartPoint(tempSeries, t);
            refreshTableAndPie();
        });

        live.humidityProperty().addListener((obs, oldVal, newVal) -> {
            float h = newVal.floatValue();
            lblHum.setText(String.format("%.0f", h));
            lblHumSub.setText(h > 80 ? "High" : h < 30 ? "Low" : "Normal");
            addChartPoint(humSeries, h);
        });

        live.soilMoistureProperty().addListener((obs, oldVal, newVal) -> {
            float s = newVal.floatValue();
            lblSoil.setText(String.format("%.0f", s));
            lblSoilSub.setText(s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal");
            addChartPoint(soilSeries, s);
            loadMapData();
        });

        // Show current values immediately
        float t = live.temperatureProperty().get();
        float h = live.humidityProperty().get();
        float s = live.soilMoistureProperty().get();
        if (!Float.isNaN(t)) { lblTemp.setText(String.format("%.1f", t)); lblTempSub.setText(t > 35 ? "High" : t < 10 ? "Low" : "Normal"); }
        if (!Float.isNaN(h)) { lblHum.setText(String.format("%.0f", h)); lblHumSub.setText(h > 80 ? "High" : h < 30 ? "Low" : "Normal"); }
        if (!Float.isNaN(s)) { lblSoil.setText(String.format("%.0f", s)); lblSoilSub.setText(s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal"); }
    }

    // ═══════════════ TREND CHART ═══════════════

    @SuppressWarnings("unchecked")
    private void setupTrendChart() {
        trendChart.getData().clear();
        trendChart.setAnimated(false);
        trendChart.setCreateSymbols(false);

        tempSeries = new XYChart.Series<>();
        tempSeries.setName(smartfarm.service.SettingsManager.getInstance().isUseFahrenheit() ? "Temperature (°F)" : "Temperature (°C)");
        humSeries = new XYChart.Series<>();
        humSeries.setName("Humidity (%)");
        soilSeries = new XYChart.Series<>();
        soilSeries.setName("Soil Moisture (%)");

        trendChart.getData().addAll(tempSeries, humSeries, soilSeries);
    }

    private void loadHistoricalChart() {
        tempSeries.getData().clear();
        humSeries.getData().clear();
        soilSeries.getData().clear();

        SensorService svc = new SensorService();
        // Determine how many readings to fetch based on period
        String period = cmbChartPeriod.getValue();
        int limit = MAX_CHART_POINTS;
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        if ("7 Days".equals(period)) { limit = 200; cutoff = LocalDateTime.now().minusDays(7); }
        else if ("30 Days".equals(period)) { limit = 500; cutoff = LocalDateTime.now().minusDays(30); }

        List<SensorReading> readings = svc.getRecentReadings(limit);
        DateTimeFormatter fmt = "24 Hours".equals(period)
                ? DateTimeFormatter.ofPattern("HH:mm")
                : DateTimeFormatter.ofPattern("MM/dd HH:mm");

        // Filter by cutoff and plot/field selection
        String selectedField = cmbFields.getValue();
        String selectedPlot = cmbPlots.getValue();

        int count = 0;
        for (int i = readings.size() - 1; i >= 0; i--) {
            SensorReading r = readings.get(i);
            if (r.getTimestamp() != null && r.getTimestamp().isBefore(cutoff)) continue;

            // Apply field/plot filter
            if (selectedField != null && !selectedField.equals("All Fields")) {
                List<Integer> allowedIds = fieldToDeviceIds.getOrDefault(selectedField, List.of());
                if (!allowedIds.contains(r.getDeviceId())) continue;
            }
            if (selectedPlot != null && !selectedPlot.equals("All Plots")) {
                Plot p = plotCache.get(r.getDeviceId());
                if (p == null || !p.getName().equals(selectedPlot)) continue;
            }

            String label = r.getTimestamp() != null ? r.getTimestamp().format(fmt) : String.valueOf(i);
            tempSeries.getData().add(new XYChart.Data<>(label, r.getTemperature()));
            humSeries.getData().add(new XYChart.Data<>(label, r.getHumidity()));
            if (!Float.isNaN(r.getSoilMoisture())) {
                soilSeries.getData().add(new XYChart.Data<>(label, r.getSoilMoisture()));
            }
            count++;
            if (count >= MAX_CHART_POINTS) break;
        }
    }

    private void reloadChart() {
        loadHistoricalChart();
    }

    private void addChartPoint(XYChart.Series<String, Number> series, float value) {
        if (series == null) return;
        String label = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        series.getData().add(new XYChart.Data<>(label, value));
        if (series.getData().size() > MAX_CHART_POINTS) {
            series.getData().remove(0);
        }
    }

    // ═══════════════ PIE CHART (Sensor Status) ═══════════════

    private void setupStatusChart() {
        pieNormal = new PieChart.Data("Normal", 0);
        pieWarning = new PieChart.Data("Warning", 0);
        pieCritical = new PieChart.Data("Critical", 0);
        pieOffline = new PieChart.Data("Offline", 0);
        statusChart.setData(FXCollections.observableArrayList(pieNormal, pieWarning, pieCritical, pieOffline));
    }

    private void applyPieColors() {
        applyPieSliceColor(pieNormal, "#4caf50");   // green
        applyPieSliceColor(pieWarning, "#ff9800");  // orange
        applyPieSliceColor(pieCritical, "#f44336"); // red
        applyPieSliceColor(pieOffline, "#9e9e9e");  // grey
    }

    private void applyPieSliceColor(PieChart.Data data, String color) {
        if (data.getNode() != null) {
            data.getNode().setStyle("-fx-pie-color: " + color + ";");
        }
    }

    private void updateStatusChart(int normal, int warning, int critical, int offline) {
        pieNormal.setPieValue(normal);
        pieWarning.setPieValue(warning);
        pieCritical.setPieValue(critical);
        pieOffline.setPieValue(offline);

        // Apply colors after a layout pass so nodes exist
        javafx.application.Platform.runLater(this::applyPieColors);

        int total = normal + warning + critical + offline;
        lblTotalSensors.setText(String.valueOf(total));
        lblNormalCount.setText(total > 0 ? String.format("%d (%.0f%%)", normal, normal * 100.0 / total) : "0");
        lblWarningCount.setText(total > 0 ? String.format("%d (%.0f%%)", warning, warning * 100.0 / total) : "0");
        lblCriticalCount.setText(total > 0 ? String.format("%d (%.0f%%)", critical, critical * 100.0 / total) : "0");
        lblOfflineCount.setText(total > 0 ? String.format("%d (%.0f%%)", offline, offline * 100.0 / total) : "0");
    }

    // ═══════════════ TABLE ═══════════════

    private void setupTable() {
        sensorTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colPlot.setResizable(false);
        colType.setResizable(false);
        colValue.setResizable(false);
        colStatus.setResizable(false);
        colUpdated.setResizable(false);

        colPlot.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("plot"));
        colType.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("type"));
        colValue.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("value"));
        colStatus.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
        colUpdated.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("updated"));

        loadSensorReadings();
    }

    private void loadSensorReadings() {
        SensorService sensorService = new SensorService();
        List<SensorReading> readings = sensorService.getRecentReadings(200);

        // Apply filters
        String selectedField = cmbFields.getValue();
        String selectedPlot = cmbPlots.getValue();
        LocalDate selectedDate = datePicker.getValue();

        List<SensorReading> filtered = readings.stream().filter(r -> {
            // Filter by field (location)
            if (selectedField != null && !selectedField.equals("All Fields")) {
                List<Integer> allowedIds = fieldToDeviceIds.getOrDefault(selectedField, List.of());
                if (!allowedIds.contains(r.getDeviceId())) return false;
            }
            // Filter by plot name
            if (selectedPlot != null && !selectedPlot.equals("All Plots")) {
                Plot p = plotCache.get(r.getDeviceId());
                if (p == null || !p.getName().equals(selectedPlot)) return false;
            }
            // Filter by date
            if (selectedDate != null && r.getTimestamp() != null) {
                if (!r.getTimestamp().toLocalDate().equals(selectedDate)) return false;
            }
            return true;
        }).collect(Collectors.toList());

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm:ss a");
        ObservableList<SensorRow> rows = FXCollections.observableArrayList();
        int normal = 0, warning = 0, critical = 0;

        for (SensorReading r : filtered) {
            Plot p = plotCache.get(r.getDeviceId());
            String plotName = (p != null) ? p.getName() : "Device " + r.getDeviceId();
            String time = r.getTimestamp() != null ? r.getTimestamp().format(timeFmt) : "—";

            // Temperature row
            float t = r.getTemperature();
            String tStatus;
            if (t > 40 || t < 5) { tStatus = "Critical"; critical++; }
            else if (t > 35 || t < 10) { tStatus = "Warning"; warning++; }
            else { tStatus = "Normal"; normal++; }
            rows.add(new SensorRow(plotName, "Temperature", smartfarm.service.SettingsManager.getInstance().formatTemp(t), tStatus, time));

            // Humidity row
            float h = r.getHumidity();
            String hStatus;
            if (h > 90 || h < 20) { hStatus = "Critical"; critical++; }
            else if (h > 80 || h < 30) { hStatus = "Warning"; warning++; }
            else { hStatus = "Normal"; normal++; }
            rows.add(new SensorRow(plotName, "Humidity", String.format("%.0f %%", h), hStatus, time));

            // Soil moisture row
            float s = r.getSoilMoisture();
            if (!Float.isNaN(s)) {
                String sStatus;
                if (s < 15 || s > 95) { sStatus = "Critical"; critical++; }
                else if (s < 30 || s > 85) { sStatus = "Warning"; warning++; }
                else { sStatus = "Normal"; normal++; }
                rows.add(new SensorRow(plotName, "Soil Moisture", String.format("%.0f %%", s), sStatus, time));
            }
        }

        sensorTable.setItems(rows);
        lblReadingsCount.setText(rows.size() + " readings");
        updateStatusChart(normal, warning, critical, 0);
    }

    private void refreshTableAndPie() {
        loadSensorReadings();
    }

    @FXML
    private void onViewAllReadings() {
        // Clear date filter and show all
        datePicker.setValue(null);
        cmbFields.getSelectionModel().selectFirst();
        cmbPlots.getSelectionModel().selectFirst();
        loadSensorReadings();
    }

    @FXML
    private void onViewAllSensors() {
        loadSensorReadings();
    }

    // ═══════════════ AUTO REFRESH ═══════════════

    private void setupAutoRefresh() {
        for (MenuItem item : btnAutoRefresh.getItems()) {
            item.setOnAction(e -> {
                String text = item.getText();
                btnAutoRefresh.setText("Refresh: " + text);
                if (autoRefreshTimeline != null) autoRefreshTimeline.stop();
                switch (text) {
                    case "5 Seconds" -> startAutoRefresh(5);
                    case "30 Seconds" -> startAutoRefresh(30);
                    case "1 Minute" -> startAutoRefresh(60);
                    case "Off" -> { /* stopped above */ }
                }
            });
        }
    }

    private void startAutoRefresh(int seconds) {
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), e -> loadSensorReadings()));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
    }

    // ═══════════════ SENSOR MAP CANVAS ═══════════════

    private void initMapCanvas() {
        if (mapCanvas == null || mapCanvasPane == null) return;
        mapCanvas.widthProperty().bind(mapCanvasPane.widthProperty());
        mapCanvas.heightProperty().bind(mapCanvasPane.heightProperty());
        mapCanvas.widthProperty().addListener(obs -> drawMap());
        mapCanvas.heightProperty().addListener(obs -> drawMap());
        loadMapData();
    }

    private void loadMapData() {
        Thread t = new Thread(() -> {
            try {
                PlotDAO dao = new PlotDAO();
                List<Plot> plots = dao.getAll();
                SensorDAO sDao = new SensorDAO();
                Map<Integer, float[]> readings = new HashMap<>();
                for (Plot p : plots) {
                    List<SensorReading> rs = sDao.getRecentForPlot(p.getPlotId(), 1);
                    if (!rs.isEmpty()) {
                        SensorReading r = rs.get(0);
                        readings.put(p.getPlotId(), new float[]{r.getTemperature(), r.getHumidity(), r.getSoilMoisture()});
                    }
                }
                float[] vals = computeFieldValues(plots, readings);
                javafx.application.Platform.runLater(() -> {
                    mapFieldValues = vals;
                    drawMap();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(this::drawMap);
            }
        }, "map-loader");
        t.setDaemon(true);
        t.start();
    }

    private float[] computeFieldValues(List<Plot> plots, Map<Integer, float[]> readings) {
        if (readings.isEmpty()) return DEMO_VALUES.clone();
        float[] out = new float[FIELDS.length];
        for (int i = 0; i < FIELDS.length; i++) {
            if (plots.isEmpty()) { out[i] = DEMO_VALUES[i]; continue; }
            Plot p = plots.get(i % plots.size());
            float[] r = readings.get(p.getPlotId());
            if (r == null) { out[i] = DEMO_VALUES[i]; continue; }
            out[i] = switch (mapMode) {
                case "Temperature"    -> norm(r[0], 0, 50);
                case "Humidity"       -> norm(r[1], 0, 100);
                case "Soil Moisture"  -> norm(r[2], 0, 100);
                default               -> allScore(r[0], r[1], r[2]);
            };
        }
        return out;
    }

    private float norm(float v, float min, float max) {
        if (Float.isNaN(v)) return 0.5f;
        return Math.max(0f, Math.min(1f, (v - min) / (max - min)));
    }

    private float allScore(float t, float h, float s) {
        float ts = Math.abs(norm(t, 0, 50) - 0.5f) * 2;
        float hs = Math.abs(norm(h, 0, 100) - 0.6f) * 2;
        float ss = Float.isNaN(s) ? 0 : Math.abs(norm(s, 0, 100) - 0.55f) * 2;
        return Math.max(0f, Math.min(1f, (ts + hs + ss) / 3f));
    }

    private void drawMap() {
        if (mapCanvas == null) return;
        double w = mapCanvas.getWidth(), h = mapCanvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        drawForest(gc, w, h);

        Color[] palette = switch (mapMode) {
            case "Temperature"   -> new Color[]{Color.web("#ffffb2"), Color.web("#fd8d3c"), Color.web("#bd0026")};
            case "Humidity"      -> new Color[]{Color.web("#d0e8ff"), Color.web("#4a9fd4"), Color.web("#083d77")};
            case "Soil Moisture" -> new Color[]{Color.web("#f5deb3"), Color.web("#8b6914"), Color.web("#3b1a00")};
            default              -> new Color[]{Color.web("#4caf50"), Color.web("#ff9800"), Color.web("#f44336")};
        };

        float[] vals = mapFieldValues != null ? mapFieldValues : DEMO_VALUES;
        long seed = 42;
        for (int i = 0; i < FIELDS.length; i++) {
            float v = i < vals.length ? vals[i] : DEMO_VALUES[i % DEMO_VALUES.length];
            Color c = lerp(v, palette[0], palette[1], palette[2]);
            double[] xs = scalePts(FIELDS[i][0], w);
            double[] ys = scalePts(FIELDS[i][1], h);
            drawField(gc, xs, ys, c, seed += 7);
        }

        drawRoads(gc, w, h);
        drawVignette(gc, w, h);
    }

    private double[] scalePts(double[] norm, double size) {
        double[] out = new double[norm.length];
        for (int i = 0; i < norm.length; i++) out[i] = norm[i] * size;
        return out;
    }

    private void drawField(GraphicsContext gc, double[] xs, double[] ys, Color base, long seed) {
        gc.setFill(base);
        gc.fillPolygon(xs, ys, xs.length);
        java.util.Random rng = new java.util.Random(seed);
        for (int k = 0; k < 18; k++) {
            double bx = minOf(xs) + rng.nextDouble() * (maxOf(xs) - minOf(xs));
            double by = minOf(ys) + rng.nextDouble() * (maxOf(ys) - minOf(ys));
            double r2 = 3 + rng.nextDouble() * 7;
            gc.setFill(base.deriveColor(0, 0.9 + rng.nextDouble() * 0.2, 0.85 + rng.nextDouble() * 0.25, 0.35));
            gc.fillOval(bx - r2, by - r2, r2 * 2, r2 * 2);
        }
        gc.setStroke(Color.color(0, 0, 0, 0.3));
        gc.setLineWidth(1.5);
        gc.strokePolygon(xs, ys, xs.length);
    }

    private void drawForest(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#1a3a1a"));
        gc.fillRect(0, 0, w, h);
        java.util.Random rng = new java.util.Random(99L);
        for (int k = 0; k < 120; k++) {
            double x = rng.nextDouble() * w;
            double y = rng.nextDouble() * h;
            double r = 6 + rng.nextDouble() * 12;
            if (x > w * 0.1 && x < w * 0.9 && y > h * 0.1 && y < h * 0.9) continue;
            gc.setFill(Color.color(0.1, 0.28 + rng.nextDouble() * 0.12, 0.1, 0.55));
            gc.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }

    private void drawRoads(GraphicsContext gc, double w, double h) {
        gc.setStroke(Color.color(0.55, 0.5, 0.35, 0.6));
        gc.setLineWidth(4);
        gc.strokeLine(w * 0.5, 0, w * 0.5, h);
        gc.strokeLine(0, h * 0.33, w, h * 0.33);
        gc.strokeLine(0, h * 0.66, w, h * 0.66);
        gc.setStroke(Color.color(0.65, 0.6, 0.45, 0.3));
        gc.setLineWidth(2);
        gc.strokeLine(w * 0.5, 0, w * 0.5, h);
        gc.strokeLine(0, h * 0.33, w, h * 0.33);
        gc.strokeLine(0, h * 0.66, w, h * 0.66);
    }

    private void drawVignette(GraphicsContext gc, double w, double h) {
        int b = 16;
        gc.setFill(Color.color(0, 0, 0, 0.45));
        gc.fillRect(0, 0, w, b);
        gc.fillRect(0, h - b, w, b);
        gc.fillRect(0, 0, b, h);
        gc.fillRect(w - b, 0, b, h);
    }

    private Color lerp(float t, Color lo, Color mid, Color hi) {
        if (t <= 0.5f) return lo.interpolate(mid, t * 2);
        return mid.interpolate(hi, (t - 0.5f) * 2);
    }

    private double minOf(double[] a) { double m = a[0]; for (double v : a) if (v < m) m = v; return m; }
    private double maxOf(double[] a) { double m = a[0]; for (double v : a) if (v > m) m = v; return m; }

    // ═══════════════ TABLE ROW MODEL ═══════════════

    public static class SensorRow {
        private final String plot, type, value, status, updated;
        public SensorRow(String p, String t, String v, String s, String u) {
            plot = p; type = t; value = v; status = s; updated = u;
        }
        public String getPlot() { return plot; }
        public String getType() { return type; }
        public String getValue() { return value; }
        public String getStatus() { return status; }
        public String getUpdated() { return updated; }
    }
}
