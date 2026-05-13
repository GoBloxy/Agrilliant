package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.CropDAO;
import smartfarm.dao.HarvestDAO;
import smartfarm.model.Crop;
import smartfarm.model.HarvestRecord;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HarvestController {

    private static final double PRICE_PER_KG = 2.5;
    private static final double COST_PER_KG = 0.8;

    @FXML private Label lblTotalHarvested, lblTotalRevenue, lblTotalCost, lblNetProfit;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbQuality;
    @FXML private TableView<HarvestRecord> harvestTable;
    @FXML private TableColumn<HarvestRecord, String> colCrop, colPlot, colQuantity, colDate, colQuality, colRevenue, colActions;
    @FXML private Button btnRecordHarvest;

    private final HarvestDAO harvestDAO = new HarvestDAO();
    private final CropDAO cropDAO = new CropDAO();
    private final ObservableList<HarvestRecord> allRecords = FXCollections.observableArrayList();
    private final Map<Integer, Crop> cropCache = new HashMap<>();

    @FXML
    public void initialize() {
        loadCropCache();
        setupTableColumns();
        loadRecords();
        setupFilters();
        updateSummaryCards();
    }

    private void loadCropCache() {
        try {
            for (Crop c : cropDAO.getAll()) {
                cropCache.put(c.getCropId(), c);
            }
        } catch (SQLException e) {
            // cache will be empty
        }
    }

    private void setupTableColumns() {
        harvestTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colCrop.setResizable(false);
        colPlot.setResizable(false);
        colQuantity.setResizable(false);
        colDate.setResizable(false);
        colQuality.setResizable(false);
        colRevenue.setResizable(false);
        colActions.setResizable(false);

        colCrop.setCellValueFactory(data -> {
            Crop c = cropCache.get(data.getValue().getCropId());
            return new javafx.beans.property.SimpleStringProperty(
                    c != null ? c.getCropName() : "Crop #" + data.getValue().getCropId());
        });
        colPlot.setCellValueFactory(data -> {
            Crop c = cropCache.get(data.getValue().getCropId());
            return new javafx.beans.property.SimpleStringProperty(
                    c != null ? "Plot " + c.getPlotId() : "--");
        });
        colQuantity.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        String.format("%.1f", data.getValue().getQuantityKg())));
        colDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getHarvestDate().toString()));
        colQuality.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getGrade().name()));
        colRevenue.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        String.format("$%.2f", data.getValue().getQuantityKg() * PRICE_PER_KG)));

        colActions.setCellFactory(col -> new TableCell<HarvestRecord, String>() {
            private final Button editBtn = new Button("", new FontIcon("fth-edit-2"));
            private final Button delBtn = new Button("", new FontIcon("fth-trash-2"));
            private final HBox box = new HBox(6, editBtn, delBtn);
            {
                editBtn.getStyleClass().add("icon-btn");
                delBtn.getStyleClass().add("icon-btn");
                editBtn.setOnAction(e -> {
                    HarvestRecord r = getTableRow() != null ? getTableRow().getItem() : null;
                    if (r != null) onEditRecord(r);
                });
                delBtn.setOnAction(e -> {
                    HarvestRecord r = getTableRow() != null ? getTableRow().getItem() : null;
                    if (r != null) onDeleteRecord(r);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadRecords() {
        try {
            allRecords.setAll(harvestDAO.getAll());
        } catch (SQLException e) {
            allRecords.clear();
        }
        applyFilters();
    }

    private void setupFilters() {
        cmbQuality.getItems().addAll("All", "A", "B", "C", "REJECT");
        cmbQuality.setValue("All");
        cmbQuality.setOnAction(e -> applyFilters());
        txtSearch.textProperty().addListener((obs, old, val) -> applyFilters());
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String quality = cmbQuality.getValue();
        List<HarvestRecord> filtered = allRecords.stream()
                .filter(r -> {
                    if (search.isEmpty()) return true;
                    Crop c = cropCache.get(r.getCropId());
                    return (c != null && c.getCropName().toLowerCase().contains(search))
                            || r.getGrade().name().toLowerCase().contains(search);
                })
                .filter(r -> "All".equals(quality) || r.getGrade().name().equals(quality))
                .collect(Collectors.toList());
        harvestTable.setItems(FXCollections.observableArrayList(filtered));
        if (filtered.isEmpty() && !search.isEmpty()) {
            harvestTable.setPlaceholder(new Label("No harvest records matching \"" + search + "\""));
        } else if (filtered.isEmpty()) {
            harvestTable.setPlaceholder(new Label("No harvest records found"));
        }
    }

    private void updateSummaryCards() {
        double totalKg = allRecords.stream().mapToDouble(HarvestRecord::getQuantityKg).sum();
        double revenue = totalKg * PRICE_PER_KG;
        double cost = totalKg * COST_PER_KG;
        double profit = revenue - cost;

        lblTotalHarvested.setText(String.format("%.1f kg", totalKg));
        lblTotalRevenue.setText(String.format("$%.2f", revenue));
        lblTotalCost.setText(String.format("$%.2f", cost));
        lblNetProfit.setText(String.format("$%.2f", profit));
    }

    @FXML
    private void onRecordHarvest() {
        Dialog<HarvestRecord> dialog = createHarvestDialog(null);
        dialog.showAndWait().ifPresent(record -> {
            try {
                harvestDAO.save(record);
                loadRecords();
                updateSummaryCards();
            } catch (SQLException e) {
                showAlert("Error", "Failed to save: " + e.getMessage());
            }
        });
    }

    private void onEditRecord(HarvestRecord record) {
        if (record == null) return;
        Dialog<HarvestRecord> dialog = createHarvestDialog(record);
        dialog.showAndWait().ifPresent(updated -> {
            try {
                updated.setRecordId(record.getRecordId());
                harvestDAO.update(updated);
                loadRecords();
                updateSummaryCards();
            } catch (SQLException e) {
                showAlert("Error", "Failed to update: " + e.getMessage());
            }
        });
    }

    private void onDeleteRecord(HarvestRecord record) {
        if (record == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete harvest record #" + record.getRecordId() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    harvestDAO.delete(record.getRecordId());
                    loadRecords();
                    updateSummaryCards();
                } catch (SQLException e) {
                    showAlert("Error", "Failed to delete: " + e.getMessage());
                }
            }
        });
    }

    private Dialog<HarvestRecord> createHarvestDialog(HarvestRecord existing) {
        Dialog<HarvestRecord> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Record Harvest" : "Edit Harvest");
        dialog.setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        ComboBox<String> cropCombo = new ComboBox<>();
        cropCombo.setPromptText("Select Crop");
        cropCombo.setMaxWidth(Double.MAX_VALUE);
        Map<String, Integer> cropNameToId = new HashMap<>();
        for (Crop c : cropCache.values()) {
            String label = c.getCropName() + " (Plot " + c.getPlotId() + ")";
            cropCombo.getItems().add(label);
            cropNameToId.put(label, c.getCropId());
        }
        if (existing != null) {
            Crop ec = cropCache.get(existing.getCropId());
            if (ec != null) {
                String label = ec.getCropName() + " (Plot " + ec.getPlotId() + ")";
                cropCombo.setValue(label);
            }
        }

        TextField qtyField = new TextField(existing != null ? String.valueOf(existing.getQuantityKg()) : "");
        qtyField.setPromptText("Quantity (kg)");

        DatePicker datePicker = new DatePicker(existing != null ? existing.getHarvestDate() : LocalDate.now());

        ComboBox<HarvestRecord.Grade> gradeCombo = new ComboBox<>();
        gradeCombo.getItems().addAll(HarvestRecord.Grade.values());
        gradeCombo.setValue(existing != null ? existing.getGrade() : HarvestRecord.Grade.A);
        gradeCombo.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(10,
                new Label("Crop:"), cropCombo,
                new Label("Quantity (kg):"), qtyField,
                new Label("Date:"), datePicker,
                new Label("Quality:"), gradeCombo);
        form.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(form);

        Button saveBtnNode = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveBtnNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String selected = cropCombo.getValue();
            if (selected == null || !cropNameToId.containsKey(selected)) {
                showAlert("Validation", "Please select a crop"); event.consume(); return;
            }
            try {
                double qty = Double.parseDouble(qtyField.getText().trim());
                if (qty <= 0) { showAlert("Validation", "Quantity must be greater than zero"); event.consume(); }
            } catch (NumberFormatException e) {
                showAlert("Validation", "Invalid quantity"); event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String selected = cropCombo.getValue();
                double qty = Double.parseDouble(qtyField.getText().trim());
                return new HarvestRecord(datePicker.getValue(), qty,
                        gradeCombo.getValue(), cropNameToId.get(selected));
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