package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class CropsPage extends VBox {

    private final TableView<CropRow> table = new TableView<>();
    private final ObservableList<CropRow> data = FXCollections.observableArrayList();

    public CropsPage() {
        setSpacing(20);
        setPadding(new Insets(32, 40, 32, 40));
        setStyle("-fx-background-color: #f8f9fa;");

        getChildren().addAll(buildHeader(), buildTable());
        VBox.setVgrow(table, Priority.ALWAYS);

        loadSampleData();
    }

    private HBox buildHeader() {
        Label title = new Label("Crops");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Manage your farm crops");
        subtitle.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 14;");

        VBox left = new VBox(4, title, subtitle);

        Button btnAdd = new Button("+ Add Crop");
        btnAdd.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 13; " +
                "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        btnAdd.setOnAction(e -> showAddDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(left, spacer, btnAdd);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    @SuppressWarnings("unchecked")
    private VBox buildTable() {
        TableColumn<CropRow, String> colName = new TableColumn<>("Crop Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setPrefWidth(180);

        TableColumn<CropRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colType.setPrefWidth(120);

        TableColumn<CropRow, String> colPlot = new TableColumn<>("Plot");
        colPlot.setCellValueFactory(new PropertyValueFactory<>("plot"));
        colPlot.setPrefWidth(120);

        TableColumn<CropRow, String> colPlanted = new TableColumn<>("Planted");
        colPlanted.setCellValueFactory(new PropertyValueFactory<>("planted"));
        colPlanted.setPrefWidth(120);

        TableColumn<CropRow, String> colStage = new TableColumn<>("Stage");
        colStage.setCellValueFactory(new PropertyValueFactory<>("stage"));
        colStage.setPrefWidth(120);

        TableColumn<CropRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setPrefWidth(100);

        table.getColumns().addAll(colName, colType, colPlot, colPlanted, colStage, colStatus);
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No crops added yet"));

        VBox wrapper = new VBox(table);
        wrapper.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");
        wrapper.setPadding(new Insets(12));
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private void loadSampleData() {
        data.addAll(
                new CropRow("Tomato", "Vegetable", "Plot 1", "2026-03-15", "Flowering", "Healthy"),
                new CropRow("Wheat", "Grain", "Plot 2", "2026-02-01", "Maturation", "Healthy"),
                new CropRow("Lettuce", "Vegetable", "Plot 3", "2026-04-10", "Seedling", "Needs Water"),
                new CropRow("Corn", "Grain", "Plot 4", "2026-03-20", "Vegetative", "Healthy"),
                new CropRow("Cucumber", "Vegetable", "Plot 5", "2026-04-25", "Seedling", "Healthy")
        );
    }

    private void showAddDialog() {
        Dialog<CropRow> dialog = new Dialog<>();
        dialog.setTitle("Add Crop");
        dialog.setHeaderText("Enter crop details");

        TextField tfName = new TextField();
        tfName.setPromptText("Crop name");
        ComboBox<String> cbType = new ComboBox<>(FXCollections.observableArrayList("Vegetable", "Grain", "Fruit", "Herb"));
        cbType.setPromptText("Type");
        TextField tfPlot = new TextField();
        tfPlot.setPromptText("Plot (e.g. Plot 1)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Name:"), 0, 0);
        grid.add(tfName, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        grid.add(cbType, 1, 1);
        grid.add(new Label("Plot:"), 0, 2);
        grid.add(tfPlot, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !tfName.getText().trim().isEmpty()) {
                return new CropRow(
                        tfName.getText().trim(),
                        cbType.getValue() != null ? cbType.getValue() : "Unknown",
                        tfPlot.getText().trim().isEmpty() ? "Unassigned" : tfPlot.getText().trim(),
                        java.time.LocalDate.now().toString(),
                        "Seedling",
                        "Healthy"
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(data::add);
    }

    public static class CropRow {
        private final String name, type, plot, planted, stage, status;

        public CropRow(String name, String type, String plot, String planted, String stage, String status) {
            this.name = name;
            this.type = type;
            this.plot = plot;
            this.planted = planted;
            this.stage = stage;
            this.status = status;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getPlot() { return plot; }
        public String getPlanted() { return planted; }
        public String getStage() { return stage; }
        public String getStatus() { return status; }
    }
}
