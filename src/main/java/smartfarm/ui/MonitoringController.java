package smartfarm.ui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import smartfarm.model.SensorReading;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SensorService;
import smartfarm.ui.async.AsyncCalls;

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
    @FXML private Label lblLight, lblLightSub;
    @FXML private Label lblWind, lblWindSub;

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

    // ─── B9 lifecycle: keep listener references so subscribeLiveSensor's
    //     side effects on the shared LiveSensorData singleton can be
    //     undone when this controller's scene graph is unmounted (e.g.
    //     when DashboardController.loadFxmlPage swaps in a different
    //     page). Without this, every visit to the Monitoring tab leaks
    //     one set of listeners and keeps the old controller alive. ─
    private ChangeListener<Number> tempListener;
    private ChangeListener<Number> humListener;
    private ChangeListener<Number> soilListener;
    private XYChart.Series<String, Number> trendTempSeries;
    private XYChart.Series<String, Number> trendHumSeries;
    private boolean trendAppendScheduled;
    private int trendSampleIndex;

    private static final int TREND_MAX_POINTS = 96;
    private static final DateTimeFormatter TREND_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TREND_DATE_TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @FXML
    public void initialize() {
        // Initialize ComboBoxes
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
        subscribeLiveSensor();
    }

    private void subscribeLiveSensor() {
        LiveSensorData live = LiveSensorData.getInstance();

        tempListener = (obs, oldVal, newVal) -> {
            float t = newVal.floatValue();
            lblTemp.setText(String.format("%.1f°C", t));
            lblTempSub.setText(t > 35 ? "High" : t < 10 ? "Low" : "Normal");
            scheduleTrendAppend();
        };
        humListener = (obs, oldVal, newVal) -> {
            float h = newVal.floatValue();
            lblHum.setText(String.format("%.0f%%", h));
            lblHumSub.setText(h > 80 ? "High" : h < 30 ? "Low" : "Normal");
            scheduleTrendAppend();
        };
        soilListener = (obs, oldVal, newVal) -> {
            float s = newVal.floatValue();
            lblSoil.setText(String.format("%.0f%%", s));
            lblSoilSub.setText(s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal");
        };

        live.temperatureProperty().addListener(tempListener);
        live.humidityProperty().addListener(humListener);
        live.soilMoistureProperty().addListener(soilListener);

        if (!Float.isNaN(live.temperatureProperty().get())
                || !Float.isNaN(live.humidityProperty().get())) {
            scheduleTrendAppend();
        }

        // B9 lifecycle: auto-detach when this controller's scene graph is
        // unmounted. DashboardController.loadFxmlPage replaces the page
        // root's parent's children, which clears the scene from this
        // subtree. sceneProperty fires once (old → null) at that point;
        // we react by removing our listeners from the LiveSensorData
        // singleton so the controller becomes GC-eligible. The scene
        // listener itself is owned by lblTemp, which is GC'd with the
        // rest of the subtree — no chain-of-references leak.
        lblTemp.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                unsubscribeLiveSensor();
            }
        });
    }

    private void unsubscribeLiveSensor() {
        LiveSensorData live = LiveSensorData.getInstance();
        if (tempListener != null) {
            live.temperatureProperty().removeListener(tempListener);
            tempListener = null;
        }
        if (humListener != null) {
            live.humidityProperty().removeListener(humListener);
            humListener = null;
        }
        if (soilListener != null) {
            live.soilMoistureProperty().removeListener(soilListener);
            soilListener = null;
        }
    }

    private void setupTrendChart() {
        trendTempSeries = new XYChart.Series<>();
        trendTempSeries.setName("Temperature (°C)");
        trendHumSeries = new XYChart.Series<>();
        trendHumSeries.setName("Humidity (%)");
        trendChart.getData().clear();
        trendChart.getData().add(trendTempSeries);
        trendChart.getData().add(trendHumSeries);
        cmbChartPeriod.valueProperty().addListener((obs, oldPeriod, newPeriod) -> resetTrendChart());
    }

    private void scheduleTrendAppend() {
        if (trendAppendScheduled) return;
        trendAppendScheduled = true;
        Platform.runLater(this::appendTrendSnapshot);
    }

    private void resetTrendChart() {
        trendSampleIndex = 0;
        trendAppendScheduled = false;
        trendTempSeries.getData().clear();
        trendHumSeries.getData().clear();
        appendTrendSnapshot();
    }

    private void appendTrendSnapshot() {
        trendAppendScheduled = false;
        if (trendTempSeries == null || trendHumSeries == null) return;

        LiveSensorData live = LiveSensorData.getInstance();
        float temperature = live.temperatureProperty().get();
        float humidity = live.humidityProperty().get();
        if (Float.isNaN(temperature) && Float.isNaN(humidity)) return;

        String label = formatTrendLabel(LocalDateTime.now(), trendSampleIndex++);
        int maxPoints = maxTrendPoints();
        if (!Float.isNaN(temperature)) {
            appendBounded(trendTempSeries, label, temperature, maxPoints);
        }
        if (!Float.isNaN(humidity)) {
            appendBounded(trendHumSeries, label, humidity, maxPoints);
        }
    }

    private void appendBounded(XYChart.Series<String, Number> series, String label, Number value, int maxPoints) {
        series.getData().add(new XYChart.Data<>(label, value));
        while (series.getData().size() > maxPoints) {
            series.getData().remove(0);
        }
    }

    private int maxTrendPoints() {
        String period = cmbChartPeriod.getValue();
        if ("7 Days".equals(period)) return 84;
        if ("30 Days".equals(period)) return 90;
        return TREND_MAX_POINTS;
    }

    private String formatTrendLabel(LocalDateTime now, int sampleIndex) {
        DateTimeFormatter fmt = "24 Hours".equals(cmbChartPeriod.getValue())
                ? TREND_TIME_FMT
                : TREND_DATE_TIME_FMT;
        return now.format(fmt) + " #" + sampleIndex;
    }

    private void setupStatusChart() {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                new PieChart.Data("Normal", 4),
                new PieChart.Data("Warning", 1),
                new PieChart.Data("Critical", 0),
                new PieChart.Data("Offline", 0)
        );
        statusChart.setData(pieChartData);
        // Note: JavaFX PieChart handles colors via CSS. We can add style classes if needed.
    }

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
        // P2.2: async. SensorService.getRecentReadings does a JDBC SELECT — used
        // to block the FX thread on every Monitoring tab open. Fetch on the DB
        // executor, build rows + update labels on FX thread.
        SensorService sensorService = new SensorService();
        AsyncCalls.runAndApply(
                () -> sensorService.getRecentReadings(50),
                (List<SensorReading> readings) -> {
                    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm:ss a");
                    ObservableList<SensorRow> rows = FXCollections.observableArrayList();
                    int normal = 0, warning = 0;

                    for (SensorReading r : readings) {
                        String plotName = "Plot " + (r.getDeviceId() > 0 ? r.getDeviceId() : "?");
                        String time = r.getTimestamp() != null ? r.getTimestamp().format(timeFmt) : "—";

                        // Temperature row
                        float t = r.getTemperature();
                        String tStatus = t > 35 ? "High" : t < 10 ? "Low" : "Normal";
                        if (tStatus.equals("Normal")) normal++; else warning++;
                        rows.add(new SensorRow(plotName, "Temperature", String.format("%.1f °C", t), tStatus, time));

                        // Humidity row
                        float h = r.getHumidity();
                        String hStatus = h > 80 ? "High" : h < 30 ? "Low" : "Normal";
                        if (hStatus.equals("Normal")) normal++; else warning++;
                        rows.add(new SensorRow(plotName, "Humidity", String.format("%.0f %%", h), hStatus, time));

                        // Soil moisture row
                        float s = r.getSoilMoisture();
                        if (!Float.isNaN(s)) {
                            String sStatus = s < 30 ? "Dry" : s > 85 ? "Wet" : "Normal";
                            if (sStatus.equals("Normal")) normal++; else warning++;
                            rows.add(new SensorRow(plotName, "Soil Moisture", String.format("%.0f %%", s), sStatus, time));
                        }
                    }

                    sensorTable.setItems(rows);
                    lblReadingsCount.setText(rows.size() + " readings");
                    lblNormalCount.setText(String.valueOf(normal));
                    lblWarningCount.setText(String.valueOf(warning));
                    lblCriticalCount.setText("0");
                    lblOfflineCount.setText("0");
                },
                err -> {
                    sensorTable.setItems(FXCollections.observableArrayList());
                    lblReadingsCount.setText("0 readings");
                    System.err.println("Failed to load sensor readings: " + err.getMessage());
                }
        );
    }

    // Inner class for TableView items
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
