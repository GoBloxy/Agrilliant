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
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import smartfarm.model.SensorReading;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SensorService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    @FXML private Pane mapPane;

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

    private static final int MAX_CHART_POINTS = 40;
    private XYChart.Series<String, Number> tempSeries;
    private XYChart.Series<String, Number> humSeries;
    private XYChart.Series<String, Number> soilSeries;

    private PieChart.Data pieNormal, pieWarning, pieCritical, pieOffline;
    private Timeline autoRefreshTimeline;

    @FXML
    public void initialize() {
        cmbFields.setItems(FXCollections.observableArrayList("All Fields", "North Field", "East Field", "South Field"));
        cmbFields.getSelectionModel().selectFirst();

        cmbPlots.setItems(FXCollections.observableArrayList("All Plots", "Plot 1", "Plot 2", "Plot 3"));
        cmbPlots.getSelectionModel().selectFirst();

        cmbChartPeriod.setItems(FXCollections.observableArrayList("24 Hours", "7 Days", "30 Days"));
        cmbChartPeriod.getSelectionModel().selectFirst();

        cmbMapSensors.setItems(FXCollections.observableArrayList("All Sensors", "Temperature", "Humidity", "Soil Moisture"));
        cmbMapSensors.getSelectionModel().selectFirst();

        setupTrendChart();
        setupStatusChart();
        setupTable();
        setupAutoRefresh();
        subscribeLiveSensor();
        loadHistoricalChart();
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
        tempSeries.setName("Temperature (°C)");
        humSeries = new XYChart.Series<>();
        humSeries.setName("Humidity (%)");
        soilSeries = new XYChart.Series<>();
        soilSeries.setName("Soil Moisture (%)");

        trendChart.getData().addAll(tempSeries, humSeries, soilSeries);
    }

    private void loadHistoricalChart() {
        SensorService svc = new SensorService();
        List<SensorReading> readings = svc.getRecentReadings(MAX_CHART_POINTS);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Readings come newest-first, reverse to plot chronologically
        for (int i = readings.size() - 1; i >= 0; i--) {
            SensorReading r = readings.get(i);
            String label = r.getTimestamp() != null ? r.getTimestamp().format(fmt) : String.valueOf(i);
            tempSeries.getData().add(new XYChart.Data<>(label, r.getTemperature()));
            humSeries.getData().add(new XYChart.Data<>(label, r.getHumidity()));
            if (!Float.isNaN(r.getSoilMoisture())) {
                soilSeries.getData().add(new XYChart.Data<>(label, r.getSoilMoisture()));
            }
        }
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
        List<SensorReading> readings = sensorService.getRecentReadings(50);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm:ss a");

        ObservableList<SensorRow> rows = FXCollections.observableArrayList();
        int normal = 0, warning = 0, critical = 0;

        for (SensorReading r : readings) {
            String plotName = "Plot " + (r.getDeviceId() > 0 ? r.getDeviceId() : "?");
            String time = r.getTimestamp() != null ? r.getTimestamp().format(timeFmt) : "—";

            // Temperature row
            float t = r.getTemperature();
            String tStatus;
            if (t > 40 || t < 5) { tStatus = "Critical"; critical++; }
            else if (t > 35 || t < 10) { tStatus = "Warning"; warning++; }
            else { tStatus = "Normal"; normal++; }
            rows.add(new SensorRow(plotName, "Temperature", String.format("%.1f °C", t), tStatus, time));

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
        SensorService sensorService = new SensorService();
        List<SensorReading> readings = sensorService.getRecentReadings(500);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm:ss a");

        ObservableList<SensorRow> rows = FXCollections.observableArrayList();
        int normal = 0, warning = 0, critical = 0;

        for (SensorReading r : readings) {
            String plotName = "Plot " + (r.getDeviceId() > 0 ? r.getDeviceId() : "?");
            String time = r.getTimestamp() != null ? r.getTimestamp().format(timeFmt) : "—";

            float t = r.getTemperature();
            String tStatus;
            if (t > 40 || t < 5) { tStatus = "Critical"; critical++; }
            else if (t > 35 || t < 10) { tStatus = "Warning"; warning++; }
            else { tStatus = "Normal"; normal++; }
            rows.add(new SensorRow(plotName, "Temperature", String.format("%.1f °C", t), tStatus, time));

            float h = r.getHumidity();
            String hStatus;
            if (h > 90 || h < 20) { hStatus = "Critical"; critical++; }
            else if (h > 80 || h < 30) { hStatus = "Warning"; warning++; }
            else { hStatus = "Normal"; normal++; }
            rows.add(new SensorRow(plotName, "Humidity", String.format("%.0f %%", h), hStatus, time));

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
        lblReadingsCount.setText(rows.size() + " readings (all)");
        updateStatusChart(normal, warning, critical, 0);
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
