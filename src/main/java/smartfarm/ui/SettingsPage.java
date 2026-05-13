package smartfarm.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import smartfarm.service.SettingsManager;

public class SettingsPage extends VBox {

    private final TextField txtServerIp = new TextField("192.168.8.141");
    private final TextField txtServerPort = new TextField("8080");
    private final Spinner<Integer> spnReadInterval = new Spinner<>(5, 300, 60, 5);
    private final ToggleButton tglAlerts = new ToggleButton("On");
    private final ComboBox<String> cmbTempUnit = new ComboBox<>();

    public SettingsPage() {
        setSpacing(24);
        setPadding(new Insets(32, 40, 32, 40));
        setStyle("-fx-background-color: #f8f9fa;");

        getChildren().addAll(
                buildHeader(),
                buildSection("Server Connection", buildServerSection()),
                buildSection("Data & Storage", buildDataSection()),
                buildSection("Appearance", buildAppearanceSection()),
                buildSection("Notifications", buildNotificationSection()),
                buildSaveBar()
        );
    }

    private HBox buildHeader() {
        Label title = new Label("Settings");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Configure your system preferences");
        subtitle.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 14;");

        VBox left = new VBox(4, title, subtitle);
        HBox header = new HBox(left);
        header.setPadding(new Insets(0, 0, 8, 0));
        return header;
    }

    private VBox buildSection(String title, GridPane content) {
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
        lbl.setStyle("-fx-text-fill: #1a1a2e;");

        VBox section = new VBox(12, lbl, content);
        section.setPadding(new Insets(20));
        section.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");
        return section;
    }

    private GridPane buildServerSection() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        txtServerIp.setPrefWidth(220);
        txtServerPort.setPrefWidth(100);

        grid.add(new Label("Server IP"), 0, 0);
        grid.add(txtServerIp, 1, 0);
        grid.add(new Label("Port"), 2, 0);
        grid.add(txtServerPort, 3, 0);

        return grid;
    }

    private GridPane buildDataSection() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        spnReadInterval.setPrefWidth(120);
        Label hint = new Label("seconds between database saves");
        hint.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12;");

        grid.add(new Label("DB Save Interval"), 0, 0);
        grid.add(spnReadInterval, 1, 0);
        grid.add(hint, 2, 0);

        return grid;
    }

    private GridPane buildAppearanceSection() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        cmbTempUnit.getItems().addAll("Celsius (°C)", "Fahrenheit (°F)");
        cmbTempUnit.setValue(SettingsManager.getInstance().isUseFahrenheit() ? "Fahrenheit (°F)" : "Celsius (°C)");
        cmbTempUnit.setPrefWidth(180);
        cmbTempUnit.setOnAction(e ->
                SettingsManager.getInstance().setUseFahrenheit("Fahrenheit (°F)".equals(cmbTempUnit.getValue())));

        grid.add(new Label("Temperature Unit"), 0, 0);
        grid.add(cmbTempUnit, 1, 0);

        return grid;
    }

    private GridPane buildNotificationSection() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        tglAlerts.setSelected(true);
        tglAlerts.setStyle("-fx-font-size: 12;");
        tglAlerts.selectedProperty().addListener((obs, o, n) -> tglAlerts.setText(n ? "On" : "Off"));

        grid.add(new Label("Enable Alerts"), 0, 0);
        grid.add(tglAlerts, 1, 0);

        return grid;
    }

    private HBox buildSaveBar() {
        Button btnSave = new Button("Save Changes");
        btnSave.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 14; " +
                "-fx-padding: 10 28; -fx-background-radius: 6; -fx-cursor: hand;");
        btnSave.setOnAction(e -> {
            SettingsManager.getInstance().setUseFahrenheit(
                    "Fahrenheit (°F)".equals(cmbTempUnit.getValue()));
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Settings saved.", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        });

        Button btnReset = new Button("Reset to Defaults");
        btnReset.setStyle("-fx-font-size: 14; -fx-padding: 10 28; -fx-background-radius: 6;");
        btnReset.setOnAction(e -> {
            txtServerIp.setText("192.168.8.141");
            txtServerPort.setText("8080");
            spnReadInterval.getValueFactory().setValue(60);
            cmbTempUnit.setValue("Celsius (°C)");
            SettingsManager.getInstance().setUseFahrenheit(false);
            tglAlerts.setSelected(true);
        });

        HBox bar = new HBox(12, btnSave, btnReset);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 0, 0, 0));
        return bar;
    }
}
