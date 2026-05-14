package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import smartfarm.dao.CropDAO;
import smartfarm.dao.HarvestDAO;
import smartfarm.model.Crop;
import smartfarm.model.HarvestRecord;
import smartfarm.ui.async.AsyncCalls;
import smartfarm.util.CSVExporter;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportsController {

    @FXML private Label lblTotalHarvest, lblAvgYield, lblRevenue, lblProfitMargin;
    @FXML private BarChart<String, Number> yieldChart;
    @FXML private TableView<HarvestRecord> harvestTable;
    @FXML private TableColumn<HarvestRecord, String> colDate, colCrop, colPlot, colQty, colGrade, colRevenue;
    @FXML private Button btnExport;

    private final HarvestDAO harvestDAO = new HarvestDAO();
    private final CropDAO cropDAO = new CropDAO();
    // P2.2: default to empty lists so cell factories + summary methods
    // tolerate being called before the async loadData() completes.
    private List<HarvestRecord> allHarvests = List.of();
    private List<Crop> allCrops = List.of();
    private static final double PRICE_PER_KG = 2.5;
    private static final double COST_PER_KG = 0.8;

    @FXML
    public void initialize() {
        // P2.2: setupTableColumns is data-independent (cell factories read
        // allCrops at render time). Everything else — summary cards, yield
        // chart, table populate — moves into loadData's apply because each
        // reads the just-fetched lists.
        setupTableColumns();
        loadData();
    }

    private void loadData() {
        AsyncCalls.runAndApply(
                () -> new ReportsData(harvestDAO.getAll(), cropDAO.getAll()),
                data -> {
                    allHarvests = data.harvests();
                    allCrops = data.crops();
                    updateSummaryCards();
                    buildYieldChart();
                    populateTable();
                },
                err -> {
                    allHarvests = List.of();
                    allCrops = List.of();
                    updateSummaryCards();
                    buildYieldChart();
                    populateTable();
                    System.err.println("Failed to load reports data: " + err.getMessage());
                }
        );
    }

    private record ReportsData(List<HarvestRecord> harvests, List<Crop> crops) {}

    private void setupTableColumns() {
        harvestTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colDate.setResizable(false);
        colCrop.setResizable(false);
        colPlot.setResizable(false);
        colQty.setResizable(false);
        colGrade.setResizable(false);
        colRevenue.setResizable(false);

        colDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getHarvestDate() != null ? data.getValue().getHarvestDate().toString() : "--"));
        colCrop.setCellValueFactory(data -> {
            String cropName = getCropName(data.getValue().getCropId());
            return new javafx.beans.property.SimpleStringProperty(cropName);
        });
        colPlot.setCellValueFactory(data -> {
            String plot = getPlotName(data.getValue().getCropId());
            return new javafx.beans.property.SimpleStringProperty(plot);
        });
        colQty.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        String.format("%.1f", data.getValue().getQuantityKg())));
        colGrade.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getGrade().name()));
        colRevenue.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        String.format("$%.2f", data.getValue().getQuantityKg() * PRICE_PER_KG)));
    }

    private String getCropName(int cropId) {
        return allCrops.stream()
                .filter(c -> c.getCropId() == cropId)
                .map(Crop::getCropName)
                .findFirst().orElse("Crop #" + cropId);
    }

    private String getPlotName(int cropId) {
        return allCrops.stream()
                .filter(c -> c.getCropId() == cropId)
                .map(c -> "Plot " + c.getPlotId())
                .findFirst().orElse("--");
    }

    private void updateSummaryCards() {
        double totalKg = allHarvests.stream().mapToDouble(HarvestRecord::getQuantityKg).sum();
        double totalRevenue = totalKg * PRICE_PER_KG;
        double totalCost = totalKg * COST_PER_KG;
        double profitMargin = totalRevenue > 0 ? ((totalRevenue - totalCost) / totalRevenue) * 100 : 0;

        long plotCount = allCrops.stream().map(Crop::getPlotId).distinct().count();
        double avgYield = plotCount > 0 ? totalKg / plotCount : 0;

        lblTotalHarvest.setText(String.format("%.1f kg", totalKg));
        lblAvgYield.setText(String.format("%.1f kg", avgYield));
        lblRevenue.setText(String.format("$%.2f", totalRevenue));
        lblProfitMargin.setText(String.format("%.0f%%", profitMargin));
    }

    private void buildYieldChart() {
        yieldChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        Map<String, Double> yieldByCrop = allHarvests.stream()
                .collect(Collectors.groupingBy(
                        hr -> getCropName(hr.getCropId()),
                        Collectors.summingDouble(HarvestRecord::getQuantityKg)));

        yieldByCrop.forEach((crop, kg) ->
                series.getData().add(new XYChart.Data<>(crop, kg)));

        yieldChart.getData().add(series);

        for (XYChart.Data<String, Number> data : series.getData()) {
            data.getNode().setStyle("-fx-bar-fill: #22c55e;");
        }
    }

    private void populateTable() {
        harvestTable.setItems(FXCollections.observableArrayList(allHarvests));
    }

    @FXML
    private void onExport() {
        try {
            StringBuilder csv = new StringBuilder("Date,Crop,Plot,Qty (kg),Grade,Revenue\n");
            for (HarvestRecord hr : allHarvests) {
                csv.append(String.format("%s,%s,%s,%.1f,%s,$%.2f\n",
                        hr.getHarvestDate(),
                        getCropName(hr.getCropId()),
                        getPlotName(hr.getCropId()),
                        hr.getQuantityKg(),
                        hr.getGrade().name(),
                        hr.getQuantityKg() * PRICE_PER_KG));
            }
            File saved = CSVExporter.saveCsv(csv.toString(), "harvest_report.csv");
            showAlert("Export", "Report exported to " + saved.getName(), Alert.AlertType.INFORMATION);
        } catch (IOException e) {
            showAlert("Error", "Failed to export: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
