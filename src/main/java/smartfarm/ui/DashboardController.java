package smartfarm.ui;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;

public class DashboardController {

    // ── Sensor Cards ──
    @FXML private Label lblTemperature, lblHumidity, lblSoilMoisture;
    @FXML private Label lblTempPlot, lblHumPlot, lblSoilPlot;
    @FXML private Label lblTempStatus, lblHumStatus, lblSoilStatus;

    // ── Chart ──
    @FXML private LineChart<String, Number> sensorChart;

    // ── Tables ──
    @FXML private TableView<?> cropTable, workerTable, harvestTable;

    // ── Lists ──
    @FXML private ListView<?> alertListView, taskListView;

    // ── Top Bar ──
    @FXML private Label lblDate, lblUserName, lblUserRole;

    // ── Sidebar Status ──
    @FXML private Label lblSystemStatus, lblDbStatus, lblSensorStatus;

    @FXML
    public void initialize() {
        // TODO: populate tables, start chart updates, set date/user info
    }
}
