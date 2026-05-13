package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportsController {

    @FXML private Label lblTotalHarvest, lblAvgYield, lblTotalRecords, lblGradeAPct;
    @FXML private BarChart<String, Number> yieldChart;
    @FXML private TableView<HarvestRecord> harvestTable;
    @FXML private TableColumn<HarvestRecord, String> colDate, colCrop, colPlot, colQty, colGrade;
    @FXML private Button btnExport;

    private final HarvestDAO harvestDAO = new HarvestDAO();
    private final CropDAO cropDAO = new CropDAO();
    private List<HarvestRecord> allHarvests;
    private List<Crop> allCrops;

    @FXML
    public void initialize() {
        loadData();
        setupTableColumns();
        updateSummaryCards();
        buildYieldChart();
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
        long gradeA = allHarvests.stream().filter(r -> r.getGrade() == HarvestRecord.Grade.A).count();
        double gradeAPct = allHarvests.isEmpty() ? 0 : (gradeA * 100.0 / allHarvests.size());

        long plotCount = allCrops.stream().map(Crop::getPlotId).distinct().count();
        double avgYield = plotCount > 0 ? totalKg / plotCount : 0;

        lblTotalHarvest.setText(String.format("%.1f kg", totalKg));
        lblAvgYield.setText(String.format("%.1f kg", avgYield));
        lblTotalRecords.setText(String.valueOf(allHarvests.size()));
        lblGradeAPct.setText(String.format("%.0f%%", gradeAPct));
    }

    private void buildYieldChart() {
        yieldChart.getData().clear();
        yieldChart.setBarGap(4);
        yieldChart.setCategoryGap(20);
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        Map<String, Double> yieldByCrop = allHarvests.stream()
                .collect(Collectors.groupingBy(
                        hr -> getCropName(hr.getCropId()),
                        Collectors.summingDouble(HarvestRecord::getQuantityKg)));

        yieldByCrop.forEach((crop, kg) ->
                series.getData().add(new XYChart.Data<>(crop, kg)));

        yieldChart.getData().add(series);

        javafx.application.Platform.runLater(() -> {
            for (XYChart.Data<String, Number> data : series.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-bar-fill: #22c55e;");
                }
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
            writer.write("Date,Crop,Plot,Qty (kg),Grade\n");
            for (HarvestRecord hr : allHarvests) {
                writer.write(String.format("%s,%s,%s,%.1f,%s\n",
                        hr.getHarvestDate(),
                        getCropName(hr.getCropId()),
                        getPlotName(hr.getCropId()),
                        hr.getQuantityKg(),
                        hr.getGrade().name()));
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
