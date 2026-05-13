package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import smartfarm.dao.CropDAO;
import smartfarm.dao.HarvestDAO;
import smartfarm.model.Crop;
import smartfarm.model.HarvestRecord;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ReportsController {

    @FXML private Label lblTotalHarvest, lblAvgYield, lblRevenue, lblProfitMargin;
    @FXML private BarChart<String, Number> yieldChart;
    @FXML private LineChart<String, Number> monthlyChart;
    @FXML private PieChart gradeChart;
    @FXML private TableView<HarvestRecord> harvestTable;
    @FXML private TableColumn<HarvestRecord, String> colDate, colCrop, colPlot, colQty, colGrade, colRevenue;
    @FXML private Button btnExport;

    private final HarvestDAO harvestDAO = new HarvestDAO();
    private final CropDAO cropDAO = new CropDAO();
    private List<HarvestRecord> allHarvests;
    private List<Crop> allCrops;
    private static final double PRICE_PER_KG = 2.5;
    private static final double COST_PER_KG = 0.8;

    @FXML
    public void initialize() {
        loadData();
        setupTableColumns();
        updateSummaryCards();
        buildYieldChart();
        buildMonthlyChart();
        buildGradeChart();
        populateTable();
    }

    private void loadData() {
        try {
            allHarvests = harvestDAO.getAll();
            allCrops = cropDAO.getAll();
        } catch (SQLException e) {
            allHarvests = List.of();
            allCrops = List.of();
        }
    }

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

    private void buildMonthlyChart() {
        monthlyChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Harvest (kg)");

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");
        Map<String, Double> monthlyYield = new TreeMap<>();

        for (HarvestRecord hr : allHarvests) {
            if (hr.getHarvestDate() != null) {
                String key = hr.getHarvestDate().format(monthFmt);
                monthlyYield.merge(key, hr.getQuantityKg(), Double::sum);
            }
        }

        monthlyYield.forEach((month, kg) ->
                series.getData().add(new XYChart.Data<>(month, kg)));

        monthlyChart.getData().add(series);
    }

    private void buildGradeChart() {
        Map<String, Long> gradeCounts = allHarvests.stream()
                .collect(Collectors.groupingBy(
                        hr -> "Grade " + hr.getGrade().name(),
                        Collectors.counting()));

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        gradeCounts.forEach((grade, count) ->
                pieData.add(new PieChart.Data(grade + " (" + count + ")", count)));

        if (pieData.isEmpty()) {
            pieData.add(new PieChart.Data("No Data", 1));
        }

        gradeChart.setData(pieData);

        // Apply colors after layout pass
        javafx.application.Platform.runLater(() -> {
            String[] colors = {"#22c55e", "#f59e0b", "#ef4444", "#6366f1"};
            int i = 0;
            for (PieChart.Data d : gradeChart.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle("-fx-pie-color: " + colors[i % colors.length] + ";");
                }
                i++;
            }
        });
    }

    private void populateTable() {
        harvestTable.setItems(FXCollections.observableArrayList(allHarvests));
    }

    @FXML
    private void onExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Harvest Report");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("harvest_report.csv");
        File file = chooser.showSaveDialog(btnExport.getScene().getWindow());
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Date,Crop,Plot,Qty (kg),Grade,Revenue\n");
            for (HarvestRecord hr : allHarvests) {
                writer.write(String.format("%s,%s,%s,%.1f,%s,$%.2f\n",
                        hr.getHarvestDate(),
                        getCropName(hr.getCropId()),
                        getPlotName(hr.getCropId()),
                        hr.getQuantityKg(),
                        hr.getGrade().name(),
                        hr.getQuantityKg() * PRICE_PER_KG));
            }
            showAlert("Export", "Report exported to " + file.getName(), Alert.AlertType.INFORMATION);
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
