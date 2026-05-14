package smartfarm.ui;

import com.gluonhq.charm.glisten.control.CharmListCell;
import com.gluonhq.charm.glisten.control.CharmListView;
import com.gluonhq.charm.glisten.control.ListTile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import smartfarm.dao.CropDAO;
import smartfarm.dao.HarvestDAO;
import smartfarm.model.Crop;
import smartfarm.model.HarvestRecord;
import smartfarm.ui.async.AsyncCalls;

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
    @FXML private CharmListView<HarvestRecord, String> harvestList;
    @FXML private Button btnRecordHarvest;

    private final HarvestDAO harvestDAO = new HarvestDAO();
    private final CropDAO cropDAO = new CropDAO();
    private final ObservableList<HarvestRecord> allRecords = FXCollections.observableArrayList();
    private final Map<Integer, Crop> cropCache = new HashMap<>();

    @FXML
    public void initialize() {
        // P2.2: loadCropCache + loadRecords are async; updateSummaryCards is
        // driven from loadRecords's apply so it always reads fresh allRecords.
        // Cell factories read cropCache per-render so they tolerate the empty
        // cache during the in-flight loadCropCache.
        setupCellFactory();
        setupFilters();
        loadCropCache();
        loadRecords();
    }

    private void loadCropCache() {
        // P2.2: async. After cropCache populates, refresh the list so cells
        // re-render with real crop names and plot labels.
        AsyncCalls.runAndApply(
                cropDAO::getAll,
                crops -> {
                    for (Crop c : crops) {
                        cropCache.put(c.getCropId(), c);
                    }
                    harvestList.refresh();
                },
                err -> {
                    // cache stays empty if DB unreachable / not configured
                    System.err.println("Failed to load crop cache: " + err.getMessage());
                }
        );
    }

    /**
     * Builds each list row as a Gluon {@link ListTile}: a green-tinted
     * package-icon badge on the left, three text lines (crop + plot,
     * quantity + date, quality + revenue), and edit/delete icon buttons
     * on the right.
     */
    private void setupCellFactory() {
        harvestList.setCellFactory(view -> new HarvestListCell());
    }

    private final class HarvestListCell extends CharmListCell<HarvestRecord> {
        private final ListTile tile = new ListTile();
        private final Button editBtn = new Button("", new FontIcon("fth-edit-2"));
        private final Button delBtn  = new Button("", new FontIcon("fth-trash-2"));
        private final HBox actionBox = new HBox(6, editBtn, delBtn);

        HarvestListCell() {
            editBtn.getStyleClass().add("icon-btn");
            delBtn.getStyleClass().add("icon-btn");
            actionBox.setAlignment(Pos.CENTER);
            editBtn.setOnAction(e -> {
                HarvestRecord r = getItem();
                if (r != null) onEditRecord(r);
            });
            delBtn.setOnAction(e -> {
                HarvestRecord r = getItem();
                if (r != null) onDeleteRecord(r);
            });
            tile.setSecondaryGraphic(actionBox);

            FontIcon badgeIcon = new FontIcon("fth-package");
            badgeIcon.setIconSize(20);
            StackPane badge = new StackPane(badgeIcon);
            badge.setStyle(
                    "-fx-background-color:#e8f5e9;-fx-background-radius:10;"
                    + "-fx-min-width:42;-fx-min-height:42;-fx-pref-width:42;-fx-pref-height:42;");
            tile.setPrimaryGraphic(badge);
        }

        @Override
        public void updateItem(HarvestRecord r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Crop c = cropCache.get(r.getCropId());
            String cropName = c != null ? c.getCropName() : "Crop #" + r.getCropId();
            String plotLbl  = c != null ? "Plot " + c.getPlotId() : "--";
            String qty      = String.format("%.1f kg", r.getQuantityKg());
            String date     = r.getHarvestDate().toString();
            String quality  = "Grade " + r.getGrade().name();
            String revenue  = String.format("$%.2f", r.getQuantityKg() * PRICE_PER_KG);

            tile.setTextLine(0, cropName + "  ·  " + plotLbl);
            tile.setTextLine(1, qty + "  ·  " + date);
            tile.setTextLine(2, quality + "  ·  " + revenue);

            setText(null);
            setGraphic(tile);
        }
    }

    private void loadRecords() {
        // P2.2: async. updateSummaryCards moves here so it reads the just-
        // populated allRecords. applyFilters drives the rendered list view.
        AsyncCalls.runAndApply(
                harvestDAO::getAll,
                records -> {
                    allRecords.setAll(records);
                    applyFilters();
                    updateSummaryCards();
                },
                err -> {
                    allRecords.clear();
                    applyFilters();
                    updateSummaryCards();
                    System.err.println("Failed to load harvest records: " + err.getMessage());
                }
        );
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
        harvestList.setItems(FXCollections.observableArrayList(filtered));
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
            // P2.2: async save. loadRecords drives updateSummaryCards from its
            // apply so we don't call it again here.
            AsyncCalls.runAndApply(
                    () -> { harvestDAO.save(record); return null; },
                    ignored -> loadRecords(),
                    err -> showAlert("Error", "Failed to save: " + err.getMessage())
            );
        });
    }

    private void onEditRecord(HarvestRecord record) {
        if (record == null) return;
        Dialog<HarvestRecord> dialog = createHarvestDialog(record);
        dialog.showAndWait().ifPresent(updated -> {
            updated.setRecordId(record.getRecordId());
            // P2.2: async update.
            AsyncCalls.runAndApply(
                    () -> { harvestDAO.update(updated); return null; },
                    ignored -> loadRecords(),
                    err -> showAlert("Error", "Failed to update: " + err.getMessage())
            );
        });
    }

    private void onDeleteRecord(HarvestRecord record) {
        if (record == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete harvest record #" + record.getRecordId() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            // P2.2: async delete.
            AsyncCalls.runAndApply(
                    () -> { harvestDAO.delete(record.getRecordId()); return null; },
                    ignored -> loadRecords(),
                    err -> showAlert("Error", "Failed to delete: " + err.getMessage())
            );
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

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String selected = cropCombo.getValue();
                if (selected == null || !cropNameToId.containsKey(selected)) {
                    showAlert("Validation", "Please select a crop");
                    return null;
                }
                try {
                    double qty = Double.parseDouble(qtyField.getText().trim());
                    if (qty <= 0) {
                        showAlert("Validation", "Quantity must be greater than zero");
                        return null;
                    }
                    return new HarvestRecord(datePicker.getValue(), qty,
                            gradeCombo.getValue(), cropNameToId.get(selected));
                } catch (NumberFormatException e) {
                    showAlert("Validation", "Invalid quantity");
                    return null;
                }
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