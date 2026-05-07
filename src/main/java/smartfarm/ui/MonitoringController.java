package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import smartfarm.service.LiveSensorData;

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

    @FXML private TableView<SensorReading> sensorTable;
    @FXML private TableColumn<SensorReading, String> colPlot;
    @FXML private TableColumn<SensorReading, String> colType;
    @FXML private TableColumn<SensorReading, String> colValue;
    @FXML private TableColumn<SensorReading, String> colStatus;
    @FXML private TableColumn<SensorReading, String> colUpdated;
    
    @FXML private Label lblReadingsCount;

    @FXML private PieChart statusChart;
    @FXML private Label lblNormalCount, lblWarningCount, lblCriticalCount, lblOfflineCount;

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
    }

    private void setupTrendChart() {
        XYChart.Series<String, Number> tempSeries = new XYChart.Series<>();
        tempSeries.setName("Temperature (°C)");
        tempSeries.getData().add(new XYChart.Data<>("12:00 AM", 22));
        tempSeries.getData().add(new XYChart.Data<>("3:00 AM", 20));
        tempSeries.getData().add(new XYChart.Data<>("6:00 AM", 21));
        tempSeries.getData().add(new XYChart.Data<>("9:00 AM", 26));
        tempSeries.getData().add(new XYChart.Data<>("12:00 PM", 32));
        tempSeries.getData().add(new XYChart.Data<>("3:00 PM", 34));
        tempSeries.getData().add(new XYChart.Data<>("6:00 PM", 30));
        tempSeries.getData().add(new XYChart.Data<>("9:00 PM", 25));

        XYChart.Series<String, Number> humSeries = new XYChart.Series<>();
        humSeries.setName("Humidity (%)");
        humSeries.getData().add(new XYChart.Data<>("12:00 AM", 70));
        humSeries.getData().add(new XYChart.Data<>("3:00 AM", 75));
        humSeries.getData().add(new XYChart.Data<>("6:00 AM", 78));
        humSeries.getData().add(new XYChart.Data<>("9:00 AM", 65));
        humSeries.getData().add(new XYChart.Data<>("12:00 PM", 55));
        humSeries.getData().add(new XYChart.Data<>("3:00 PM", 50));
        humSeries.getData().add(new XYChart.Data<>("6:00 PM", 60));
        humSeries.getData().add(new XYChart.Data<>("9:00 PM", 72));

        trendChart.getData().addAll(tempSeries, humSeries);
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
        colPlot.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("plot"));
        colType.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("type"));
        colValue.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("value"));
        colStatus.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
        colUpdated.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("updated"));

        ObservableList<SensorReading> data = FXCollections.observableArrayList(
            new SensorReading("Plot 1 - North Field", "Temperature", "28.6 °C", "Normal", "10:30:30 AM"),
            new SensorReading("Plot 1 - North Field", "Humidity", "65 %", "Normal", "10:30:30 AM"),
            new SensorReading("Plot 2 - East Field", "Soil Moisture", "32 %", "Low", "10:30:30 AM"),
            new SensorReading("Plot 2 - East Field", "Light Intensity", "750 lux", "Normal", "10:30:30 AM"),
            new SensorReading("Plot 3 - North Field", "Temperature", "31.2 °C", "High", "10:30:30 AM"),
            new SensorReading("Plot 3 - North Field", "Humidity", "58 %", "Normal", "10:30:30 AM")
        );
        sensorTable.setItems(data);
    }
    
    // Inner class for TableView items
    public static class SensorReading {
        private String plot, type, value, status, updated;
        public SensorReading(String p, String t, String v, String s, String u) {
            plot = p; type = t; value = v; status = s; updated = u;
        }
        public String getPlot() { return plot; }
        public String getType() { return type; }
        public String getValue() { return value; }
        public String getStatus() { return status; }
        public String getUpdated() { return updated; }
    }
}
