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
import javafx.scene.layout.StackPane;
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
    @FXML private Button btnRefreshCards;

    @FXML private Label lblTemp, lblTempSub;
    @FXML private Label lblHum, lblHumSub;
    @FXML private Label lblSoil, lblSoilSub;
    @FXML private Label lblLight, lblLightSub;

    @FXML private ComboBox<String> cmbChartPeriod;
    @FXML private LineChart<String, Number> trendChart;

    @FXML private ComboBox<String> cmbMapSensors;
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

    // ── Sensor-map tile pane ──
    private javafx.scene.layout.TilePane sensorMapTile;
    private String mapMode = "All Sensors";

    private static final int MAX_CHART_POINTS = 40;
    private XYChart.Series<String, Number> tempSeries;
    private XYChart.Series<String, Number> humSeries;
    private XYChart.Series<String, Number> soilSeries;
    private XYChart.Series<String, Number> lightSeries;

    private PieChart.Data pieNormal, pieWarning, pieCritical, pieOffline;
    private Timeline autoRefreshTimeline;

    private Map<Integer, Plot> plotCache = new HashMap<>();
    private Map<String, List<Integer>> fieldToDeviceIds = new HashMap<>();

    @FXML
    public void initialize() {
        loadPlotData();

        cmbChartPeriod.setItems(FXCollections.observableArrayList("24 Hours", "7 Days", "30 Days"));
        cmbChartPeriod.getSelectionModel().selectFirst();

        cmbMapSensors.setItems(FXCollections.observableArrayList("All Sensors", "Temperature", "Humidity", "Soil Moisture", "Light Intensity"));
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

        live.lightLevelProperty().addListener((obs, oldVal, newVal) -> {
            float l = newVal.floatValue();
            if (lblLight != null) {
                lblLight.setText(String.format("%.0f", l));
                lblLightSub.setText(l < 20 ? "Dark" : l > 80 ? "Bright" : "Normal");
            }
            addChartPoint(lightSeries, l);
        });

        // Show current values immediately
        float t = live.temperatureProperty().get();
        float h = live.humidityProperty().get();
        float s = live.soilMoistureProperty().get();
        float l = live.lightLevelProperty().get();
        if (!Float.isNaN(t)) { lblTemp.setText(String.format("%.1f", t)); lblTempSub.setText(t > 35 ? "High" : t < 10 ? "Low" : "Normal"); }
        if (!Float.isNaN(h)) { lblHum.setText(String.format("%.0f", h)); lblHumSub.setText(h > 80 ? "High" : h < 30 ? "Low" : "Normal"); }
        if (!Float.isNaN(s)) { lblSoil.setText(String.format("%.0f", s)); lblSoilSub.setText(s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal"); }
        if (lblLight != null && !Float.isNaN(l)) { lblLight.setText(String.format("%.0f", l)); lblLightSub.setText(l < 20 ? "Dark" : l > 80 ? "Bright" : "Normal"); }

        // Polling fallback for remote clients: re-applies LiveSensorData every 5s
        // so cards stay current even when the same value arrives repeatedly.
        Timeline livePoll = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            float t2 = live.temperatureProperty().get();
            float h2 = live.humidityProperty().get();
            float s2 = live.soilMoistureProperty().get();
            float l2 = live.lightLevelProperty().get();
            if (!Float.isNaN(t2)) { lblTemp.setText(String.format("%.1f", t2)); lblTempSub.setText(t2 > 35 ? "High" : t2 < 10 ? "Low" : "Normal"); }
            if (!Float.isNaN(h2)) { lblHum.setText(String.format("%.0f", h2)); lblHumSub.setText(h2 > 80 ? "High" : h2 < 30 ? "Low" : "Normal"); }
            if (!Float.isNaN(s2)) { lblSoil.setText(String.format("%.0f", s2)); lblSoilSub.setText(s2 < 30 ? "Dry" : s2 > 85 ? "Wet" : "Normal"); }
            if (lblLight != null && !Float.isNaN(l2)) { lblLight.setText(String.format("%.0f", l2)); lblLightSub.setText(l2 < 20 ? "Dark" : l2 > 80 ? "Bright" : "Normal"); }
        }));
        livePoll.setCycleCount(Animation.INDEFINITE);
        livePoll.play();
    }

    // ═══════════════ MANUAL REFRESH ═══════════════

    @FXML
    private void onRefreshCards() {
        refreshCards();
    }

    /**
     * Refresh the top summary cards from the DB — reliable for remote clients
     * since the DB is shared. Falls back to in-memory LiveSensorData if no DB row exists.
     */
    private void refreshCards() {
        try {
            SensorDAO dao = new SensorDAO();
            List<SensorReading> recent = dao.getRecent(1);
            if (!recent.isEmpty()) {
                SensorReading r = recent.get(0);
                float t = r.getTemperature();
                float h = r.getHumidity();
                float s = r.getSoilMoisture();
                float li = r.getLightLevel();
                if (!Float.isNaN(t)) { lblTemp.setText(String.format("%.1f", t)); lblTempSub.setText(t > 35 ? "High" : t < 10 ? "Low" : "Normal"); }
                if (!Float.isNaN(h)) { lblHum.setText(String.format("%.0f", h)); lblHumSub.setText(h > 80 ? "High" : h < 30 ? "Low" : "Normal"); }
                if (!Float.isNaN(s)) { lblSoil.setText(String.format("%.0f", s)); lblSoilSub.setText(s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal"); }
                if (lblLight != null && !Float.isNaN(li)) { lblLight.setText(String.format("%.0f", li)); lblLightSub.setText(li < 20 ? "Dark" : li > 80 ? "Bright" : "Normal"); }
                return;
            }
        } catch (SQLException e) {
            System.err.println("refreshCards: DB query failed: " + e.getMessage());
        }
        LiveSensorData live = LiveSensorData.getInstance();
        float t = live.temperatureProperty().get();
        float h = live.humidityProperty().get();
        float s = live.soilMoistureProperty().get();
        float li = live.lightLevelProperty().get();
        if (!Float.isNaN(t)) { lblTemp.setText(String.format("%.1f", t)); lblTempSub.setText(t > 35 ? "High" : t < 10 ? "Low" : "Normal"); }
        if (!Float.isNaN(h)) { lblHum.setText(String.format("%.0f", h)); lblHumSub.setText(h > 80 ? "High" : h < 30 ? "Low" : "Normal"); }
        if (!Float.isNaN(s)) { lblSoil.setText(String.format("%.0f", s)); lblSoilSub.setText(s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal"); }
        if (lblLight != null && !Float.isNaN(li)) { lblLight.setText(String.format("%.0f", li)); lblLightSub.setText(li < 20 ? "Dark" : li > 80 ? "Bright" : "Normal"); }
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
        lightSeries = new XYChart.Series<>();
        lightSeries.setName("Light Intensity (%)");

        trendChart.getData().addAll(tempSeries, humSeries, soilSeries, lightSeries);
    }

    private void loadHistoricalChart() {
        tempSeries.getData().clear();
        humSeries.getData().clear();
        soilSeries.getData().clear();
        lightSeries.getData().clear();

        String period = cmbChartPeriod.getValue();
        int limit = MAX_CHART_POINTS;
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        if ("7 Days".equals(period)) { limit = 200; cutoff = LocalDateTime.now().minusDays(7); }
        else if ("30 Days".equals(period)) { limit = 500; cutoff = LocalDateTime.now().minusDays(30); }

        String selectedField = cmbFields.getValue();
        String selectedPlot = cmbPlots.getValue();
        List<SensorReading> readings = fetchChartReadings(selectedField, selectedPlot, limit);

        DateTimeFormatter fmt = "24 Hours".equals(period)
                ? DateTimeFormatter.ofPattern("HH:mm")
                : DateTimeFormatter.ofPattern("MM/dd HH:mm");

        final LocalDateTime cutoffFinal = cutoff;
        int count = 0;
        for (int i = readings.size() - 1; i >= 0; i--) {
            SensorReading r = readings.get(i);
            if (r.getTimestamp() != null && r.getTimestamp().isBefore(cutoffFinal)) continue;
            String label = r.getTimestamp() != null ? r.getTimestamp().format(fmt) : String.valueOf(i);
            tempSeries.getData().add(new XYChart.Data<>(label, r.getTemperature()));
            humSeries.getData().add(new XYChart.Data<>(label, r.getHumidity()));
            if (!Float.isNaN(r.getSoilMoisture())) {
                soilSeries.getData().add(new XYChart.Data<>(label, r.getSoilMoisture()));
            }
            if (!Float.isNaN(r.getLightLevel())) {
                lightSeries.getData().add(new XYChart.Data<>(label, r.getLightLevel()));
            }
            count++;
            if (count >= MAX_CHART_POINTS) break;
        }
    }

    private List<SensorReading> fetchChartReadings(String field, String plot, int limit) {
        SensorDAO dao = new SensorDAO();
        try {
            // Specific plot selected — use plot-level join to get correct device readings
            if (plot != null && !plot.equals("All Plots")) {
                for (Map.Entry<Integer, Plot> entry : plotCache.entrySet()) {
                    if (entry.getValue().getName().equals(plot)) {
                        return dao.getRecentForPlot(entry.getKey(), limit);
                    }
                }
            }
            // Specific field selected — fetch for all plots in that field
            if (field != null && !field.equals("All Fields")) {
                List<Integer> plotIds = fieldToDeviceIds.getOrDefault(field, List.of());
                List<SensorReading> all = new java.util.ArrayList<>();
                for (int pid : plotIds) {
                    all.addAll(dao.getRecentForPlot(pid, limit));
                }
                all.sort((a, b) -> b.getTimestamp() != null && a.getTimestamp() != null
                        ? b.getTimestamp().compareTo(a.getTimestamp()) : 0);
                return all;
            }
            // All plots — return recent readings globally
            return dao.getRecent(limit);
        } catch (Exception e) {
            System.err.println("Chart fetch error: " + e.getMessage());
            return new java.util.ArrayList<>();
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

        // Track latest reading per device for the pie chart (one status count per device)
        Map<Integer, SensorReading> latestPerDevice = new java.util.LinkedHashMap<>();

        for (SensorReading r : filtered) {
            latestPerDevice.putIfAbsent(r.getDeviceId(), r);

            Plot p = plotCache.get(r.getDeviceId());
            String plotName = (p != null) ? p.getName() : "Device " + r.getDeviceId();
            String time = r.getTimestamp() != null ? r.getTimestamp().format(timeFmt) : "—";

            // Temperature row
            float t = r.getTemperature();
            String tStatus = (t > 40 || t < 5) ? "Critical" : (t > 35 || t < 10) ? "Warning" : "Normal";
            rows.add(new SensorRow(plotName, "Temperature", smartfarm.service.SettingsManager.getInstance().formatTemp(t), tStatus, time));

            // Humidity row
            float h = r.getHumidity();
            String hStatus = (h > 90 || h < 20) ? "Critical" : (h > 80 || h < 30) ? "Warning" : "Normal";
            rows.add(new SensorRow(plotName, "Humidity", String.format("%.0f %%", h), hStatus, time));

            // Soil moisture row
            float s = r.getSoilMoisture();
            if (!Float.isNaN(s)) {
                String sStatus = (s < 15 || s > 95) ? "Critical" : (s < 30 || s > 85) ? "Warning" : "Normal";
                rows.add(new SensorRow(plotName, "Soil Moisture", String.format("%.0f %%", s), sStatus, time));
            }

            // Light intensity row
            float li = r.getLightLevel();
            if (!Float.isNaN(li)) {
                String liStatus = (li < 10 || li > 95) ? "Critical" : (li < 20 || li > 80) ? "Warning" : "Normal";
                rows.add(new SensorRow(plotName, "Light Intensity", String.format("%.0f %%", li), liStatus, time));
            }
        }

        // Count pie chart status per device (worst metric wins)
        int normal = 0, warning = 0, critical = 0;
        for (SensorReading r : latestPerDevice.values()) {
            int worst = deviceWorstStatus(r); // 0=normal, 1=warning, 2=critical
            if (worst == 2) critical++;
            else if (worst == 1) warning++;
            else normal++;
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

    // ═══════════════ SENSOR MAP (TILE-BASED) ═══════════════

    private void initMapCanvas() {
        if (mapCanvasPane == null) return;
        sensorMapTile = new javafx.scene.layout.TilePane();
        sensorMapTile.setHgap(8);
        sensorMapTile.setVgap(8);
        sensorMapTile.setPadding(new javafx.geometry.Insets(10));
        sensorMapTile.setPrefColumns(2);
        sensorMapTile.setStyle("-fx-background-color: transparent;");

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(sensorMapTile);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        mapCanvasPane.getChildren().setAll(scroll);
        loadMapData();
    }

    private void loadMapData() {
        Thread t = new Thread(() -> {
            try {
                PlotDAO dao = new PlotDAO();
                List<Plot> plots = dao.getAll();
                SensorDAO sDao = new SensorDAO();
                Map<Integer, SensorReading> latestReadings = new HashMap<>();
                for (Plot p : plots) {
                    List<SensorReading> rs = sDao.getRecentForPlot(p.getPlotId(), 1);
                    if (!rs.isEmpty()) latestReadings.put(p.getPlotId(), rs.get(0));
                }
                javafx.application.Platform.runLater(() -> buildSensorTiles(plots, latestReadings));
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> buildSensorTiles(List.of(), Map.of()));
            }
        }, "map-loader");
        t.setDaemon(true);
        t.start();
    }

    private void buildSensorTiles(List<Plot> plots, Map<Integer, SensorReading> readings) {
        if (sensorMapTile == null) return;
        sensorMapTile.getChildren().clear();

        if (plots.isEmpty()) {
            javafx.scene.control.Label empty = new javafx.scene.control.Label("No plots available");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13;");
            sensorMapTile.getChildren().add(empty);
            return;
        }

        for (Plot p : plots) {
            SensorReading r = readings.get(p.getPlotId());
            sensorMapTile.getChildren().add(buildPlotTile(p, r));
        }
    }

    private javafx.scene.layout.VBox buildPlotTile(Plot p, SensorReading r) {
        // Determine status color
        String borderColor = "#64748b"; // offline grey
        String bgColor = "rgba(100,116,139,0.15)";
        if (r != null) {
            int worst = deviceWorstStatus(r);
            if (worst == 2) { borderColor = "#ef4444"; bgColor = "rgba(239,68,68,0.15)"; }
            else if (worst == 1) { borderColor = "#f59e0b"; bgColor = "rgba(245,158,11,0.15)"; }
            else { borderColor = "#22c55e"; bgColor = "rgba(34,197,94,0.15)"; }
        }

        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(6);
        card.setPadding(new javafx.geometry.Insets(10));
        card.setPrefWidth(160);
        card.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-width: 0 0 0 4;" +
            "-fx-border-radius: 8;"
        );

        // Plot name
        javafx.scene.control.Label nameLabel = new javafx.scene.control.Label(p.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: #f1f5f9;");
        nameLabel.setWrapText(false);
        card.getChildren().add(nameLabel);

        // Sensor readings grid (2 columns)
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        if (r != null) {
            boolean showAll  = "All Sensors".equals(mapMode);
            boolean showTemp  = showAll || "Temperature".equals(mapMode);
            boolean showHum   = showAll || "Humidity".equals(mapMode);
            boolean showSoil  = showAll || "Soil Moisture".equals(mapMode);
            boolean showLight = showAll || "Light Intensity".equals(mapMode);

            int col = 0, row = 0;
            if (showTemp)  { grid.add(sensorCell("🌡", formatSensorTemp(r.getTemperature()), "#fb923c"), col++, row); if (col > 1) { col = 0; row++; } }
            if (showHum)   { grid.add(sensorCell("💧", fmt(r.getHumidity(), "%"), "#60a5fa"), col++, row); if (col > 1) { col = 0; row++; } }
            if (showSoil && !Float.isNaN(r.getSoilMoisture()))  { grid.add(sensorCell("🌱", fmt(r.getSoilMoisture(), "%"), "#4ade80"), col++, row); if (col > 1) { col = 0; row++; } }
            if (showLight && !Float.isNaN(r.getLightLevel()))   { grid.add(sensorCell("☀", fmt(r.getLightLevel(), "%"), "#facc15"), col, row); }
        } else {
            javafx.scene.control.Label noData = new javafx.scene.control.Label("No data");
            noData.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11;");
            grid.add(noData, 0, 0);
        }

        card.getChildren().add(grid);
        return card;
    }

    private javafx.scene.layout.VBox sensorCell(String icon, String value, String color) {
        javafx.scene.layout.VBox cell = new javafx.scene.layout.VBox(1);
        cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.Label iconLbl = new javafx.scene.control.Label(icon);
        iconLbl.setStyle("-fx-font-size: 14;");

        javafx.scene.control.Label valLbl = new javafx.scene.control.Label(value);
        valLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        cell.getChildren().addAll(iconLbl, valLbl);
        return cell;
    }

    private String fmt(float v, String unit) {
        return Float.isNaN(v) ? "--" : String.format("%.0f%s", v, unit);
    }

    private String formatSensorTemp(float t) {
        if (Float.isNaN(t)) return "--";
        return smartfarm.service.SettingsManager.getInstance().formatTemp(t);
    }

    // ═══════════════ TABLE ROW MODEL ═══════════════

    /** Returns 0=Normal, 1=Warning, 2=Critical for the worst metric in this reading. */
    private int deviceWorstStatus(SensorReading r) {
        int worst = 0;
        float t = r.getTemperature();
        if (t > 40 || t < 5) worst = 2;
        else if ((t > 35 || t < 10) && worst < 1) worst = 1;

        float h = r.getHumidity();
        if (h > 90 || h < 20) worst = 2;
        else if ((h > 80 || h < 30) && worst < 1) worst = 1;

        float s = r.getSoilMoisture();
        if (!Float.isNaN(s)) {
            if (s < 15 || s > 95) worst = 2;
            else if ((s < 30 || s > 85) && worst < 1) worst = 1;
        }

        float li = r.getLightLevel();
        if (!Float.isNaN(li)) {
            if (li < 10 || li > 95) worst = 2;
            else if ((li < 20 || li > 80) && worst < 1) worst = 1;
        }
        return worst;
    }

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
