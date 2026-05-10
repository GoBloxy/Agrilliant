package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.CropDAO;
import smartfarm.model.Crop;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class CropController {

    @FXML private Label lblTotalCrops, lblGrowing, lblReady, lblAtRisk, lblPagination;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbPlot, cmbStage;
    @FXML private TableView<Crop> cropTable;
    @FXML private TableColumn<Crop, String> colCropName, colPlot, colVariety, colPlantedDate, colHarvestDate, colStatus, colActions;
    @FXML private Button btnAddCrop, btnExport;

    private final CropDAO cropDAO = new CropDAO();
    private final ObservableList<Crop> allCrops = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadCrops();
        setupFilters();
        updateSummaryCards();
    }

    private void setupTableColumns() {
        colCropName.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getCropName()));
        colPlot.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty("Plot " + data.getValue().getPlotId()));
        colVariety.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty("—"));
        colPlantedDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getPlantingDate() != null ? data.getValue().getPlantingDate().toString() : "—"));
        colHarvestDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getHarvestDate() != null ? data.getValue().getHarvestDate().toString() : "—"));
        colStatus.setCellValueFactory(data -> {
            Crop c = data.getValue();
            String status;
            if (c.getGrowthStage() == Crop.GrowthStage.HARVESTED) status = "Harvested";
            else if (c.getGrowthStage() == Crop.GrowthStage.FRUITING) status = "Ready";
            else if (c.isOverdue()) status = "At Risk";
            else status = "Growing";
            return new javafx.beans.property.SimpleStringProperty(status);
        });
        colStatus.setCellFactory(col -> new TableCell<Crop, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    switch (item) {
                        case "Growing"   -> badge.setStyle("-fx-background-color:#d1fae5;-fx-text-fill:#065f46;");
                        case "Ready"     -> badge.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#92400e;");
                        case "At Risk"   -> badge.setStyle("-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;");
                        case "Harvested" -> badge.setStyle("-fx-background-color:#e0e7ff;-fx-text-fill:#3730a3;");
                    }
                    badge.setStyle(badge.getStyle() + "-fx-padding:2 8;-fx-background-radius:10;-fx-font-size:11;-fx-font-weight:bold;");
                    setGraphic(badge);
                }
            }
        });

        colActions.setCellFactory(col -> new TableCell<Crop, String>() {
            private final Button editBtn = new Button("", new FontIcon("fth-edit-2"));
            private final Button delBtn = new Button("", new FontIcon("fth-trash-2"));
            private final HBox box = new HBox(6, editBtn, delBtn);
            {
                editBtn.getStyleClass().add("icon-btn");
                delBtn.getStyleClass().add("icon-btn");
                editBtn.setOnAction(e -> {
                    Crop c = getTableRow() != null ? getTableRow().getItem() : null;
                    if (c != null) onEditCrop(c);
                });
                delBtn.setOnAction(e -> {
                    Crop c = getTableRow() != null ? getTableRow().getItem() : null;
                    if (c != null) onDeleteCrop(c);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadCrops() {
        try {
            allCrops.setAll(cropDAO.getAll());
        } catch (SQLException e) {
            allCrops.clear();
        }
        applyFilters();
    }

    private void setupFilters() {
        cmbPlot.getItems().add("All Plots");
        allCrops.stream().map(c -> "Plot " + c.getPlotId()).distinct().sorted()
                .forEach(p -> cmbPlot.getItems().add(p));
        cmbPlot.setValue("All Plots");
        cmbPlot.setOnAction(e -> applyFilters());

        cmbStage.getItems().addAll("All Stages", "Growing", "Ready", "At Risk", "Harvested");
        cmbStage.setValue("All Stages");
        cmbStage.setOnAction(e -> applyFilters());

        txtSearch.textProperty().addListener((obs, old, val) -> applyFilters());
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String plot = cmbPlot.getValue();
        String stage = cmbStage.getValue();

        List<Crop> filtered = allCrops.stream()
                .filter(c -> search.isEmpty() || c.getCropName().toLowerCase().contains(search))
                .filter(c -> "All Plots".equals(plot) || ("Plot " + c.getPlotId()).equals(plot))
                .filter(c -> {
                    if ("All Stages".equals(stage)) return true;
                    String s = getStatusLabel(c);
                    return s.equals(stage);
                })
                .collect(Collectors.toList());

        cropTable.setItems(FXCollections.observableArrayList(filtered));
        lblPagination.setText("Showing 1 to " + filtered.size() + " of " + allCrops.size() + " crops");
    }

    private String getStatusLabel(Crop c) {
        if (c.getGrowthStage() == Crop.GrowthStage.HARVESTED) return "Harvested";
        if (c.getGrowthStage() == Crop.GrowthStage.FRUITING) return "Ready";
        if (c.isOverdue()) return "At Risk";
        return "Growing";
    }

    private void updateSummaryCards() {
        int total = allCrops.size();
        long growing = allCrops.stream().filter(c -> getStatusLabel(c).equals("Growing")).count();
        long ready = allCrops.stream().filter(c -> getStatusLabel(c).equals("Ready")).count();
        long atRisk = allCrops.stream().filter(c -> getStatusLabel(c).equals("At Risk")).count();

        lblTotalCrops.setText(String.valueOf(total));
        lblGrowing.setText(String.valueOf(growing));
        lblReady.setText(String.valueOf(ready));
        lblAtRisk.setText(String.valueOf(atRisk));
    }

    @FXML
    private void onAddCrop() {
        Dialog<Crop> dialog = createCropDialog(null);
        dialog.showAndWait().ifPresent(crop -> {
            try {
                cropDAO.save(crop);
                loadCrops();
                updateSummaryCards();
                setupFilters();
            } catch (SQLException e) {
                showAlert("Error", "Failed to save: " + e.getMessage());
            }
        });
    }

    private void onEditCrop(Crop crop) {
        if (crop == null) return;
        Dialog<Crop> dialog = createCropDialog(crop);
        dialog.showAndWait().ifPresent(updated -> {
            try {
                updated.setCropId(crop.getCropId());
                cropDAO.update(updated);
                loadCrops();
                updateSummaryCards();
            } catch (SQLException e) {
                showAlert("Error", "Failed to update: " + e.getMessage());
            }
        });
    }

    private void onDeleteCrop(Crop crop) {
        if (crop == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + crop.getCropName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    cropDAO.delete(crop.getCropId());
                    loadCrops();
                    updateSummaryCards();
                } catch (SQLException e) {
                    showAlert("Error", "Failed to delete: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void onExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Crops");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("crops.csv");
        File file = chooser.showSaveDialog(btnExport.getScene().getWindow());
        if (file == null) return;

        try (FileWriter w = new FileWriter(file)) {
            w.write("Crop Name,Plot,Planted Date,Harvest Date,Stage,Expected Yield\n");
            for (Crop c : allCrops) {
                w.write(String.format("%s,Plot %d,%s,%s,%s,%.1f\n",
                        c.getCropName(), c.getPlotId(),
                        c.getPlantingDate(), c.getHarvestDate(),
                        c.getGrowthStage(), c.getExpectedYield()));
            }
            showAlert("Export", "Exported to " + file.getName());
        } catch (IOException e) {
            showAlert("Error", "Failed to export: " + e.getMessage());
        }
    }

    private Dialog<Crop> createCropDialog(Crop existing) {
        Dialog<Crop> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Crop" : "Edit Crop");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getCropName() : "");
        nameField.setPromptText("Crop Name");

        TextField plotField = new TextField(existing != null ? String.valueOf(existing.getPlotId()) : "");
        plotField.setPromptText("Plot ID");

        DatePicker plantedPicker = new DatePicker(existing != null ? existing.getPlantingDate() : LocalDate.now());
        DatePicker harvestPicker = new DatePicker(existing != null ? existing.getHarvestDate() : LocalDate.now().plusMonths(3));

        TextField yieldField = new TextField(existing != null ? String.valueOf(existing.getExpectedYield()) : "");
        yieldField.setPromptText("Expected Yield (kg)");

        ComboBox<Crop.GrowthStage> stageCombo = new ComboBox<>();
        stageCombo.getItems().addAll(Crop.GrowthStage.values());
        stageCombo.setValue(existing != null ? existing.getGrowthStage() : Crop.GrowthStage.SEED);
        stageCombo.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(10,
                new Label("Crop Name:"), nameField,
                new Label("Plot ID:"), plotField,
                new Label("Planted Date:"), plantedPicker,
                new Label("Harvest Date:"), harvestPicker,
                new Label("Expected Yield (kg):"), yieldField,
                new Label("Growth Stage:"), stageCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { showAlert("Validation", "Crop name is required"); return null; }
                int plotId;
                try { plotId = Integer.parseInt(plotField.getText().trim()); }
                catch (NumberFormatException e) { showAlert("Validation", "Invalid Plot ID"); return null; }
                double yield;
                try { yield = Double.parseDouble(yieldField.getText().trim()); }
                catch (NumberFormatException e) { yield = 0; }

                Crop crop = new Crop(name, plantedPicker.getValue(), harvestPicker.getValue(), plotId, yield);
                crop.setGrowthStage(stageCombo.getValue());
                return crop;
            }
            return null;
        });
        return dialog;
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}