package smartfarm.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public class AboutPage extends VBox {

    public AboutPage() {
        setSpacing(24);
        setPadding(new Insets(40, 60, 40, 60));
        setAlignment(Pos.TOP_CENTER);
        setStyle("-fx-background-color: #f8f9fa;");

        getChildren().addAll(
                buildLogoSection(),
                new Separator(),
                buildInfoCard(),
                buildTeamSection(),
                buildFooter()
        );
    }

    private VBox buildLogoSection() {
        ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/images/nobackgroundlogo.png")));
        logo.setFitWidth(80);
        logo.setFitHeight(80);
        logo.setPreserveRatio(true);

        Label appName = new Label("Agrilliant");
        appName.setFont(Font.font("System", FontWeight.BOLD, 28));
        appName.setStyle("-fx-text-fill: #1a5e1f;");

        Label tagline = new Label("Smart Farming, Brighter Future");
        tagline.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 14;");

        Label version = new Label("Version 1.0.0");
        version.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 12;");

        VBox box = new VBox(8, logo, appName, tagline, version);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(0, 0, 8, 0));
        return box;
    }

    private VBox buildInfoCard() {
        Label desc = new Label(
                "Agrilliant is a smart farm management system that connects IoT sensors " +
                "to a centralized dashboard for real-time environmental monitoring, " +
                "automated alerts, crop management, and task coordination.\n\n" +
                "The system uses ESP32 microcontrollers equipped with DHT11 and FC-28 sensors " +
                "to collect temperature, humidity, and soil moisture data. Readings are transmitted " +
                "over TCP to a Java server and displayed on a JavaFX dashboard with live updates."
        );
        desc.setWrapText(true);
        desc.setTextAlignment(TextAlignment.CENTER);
        desc.setStyle("-fx-font-size: 14; -fx-text-fill: #343a40; -fx-line-spacing: 4;");
        desc.setMaxWidth(650);

        VBox card = new VBox(desc);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(24));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");
        return card;
    }

    private VBox buildTeamSection() {
        Label heading = new Label("Tech Stack");
        heading.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
        heading.setStyle("-fx-text-fill: #1a1a2e;");

        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);

        String[][] stack = {
                {"Backend", "Java 17, JDBC, MySQL 8"},
                {"Frontend", "JavaFX 21, AtlantaFX"},
                {"IoT", "ESP32, DHT11, FC-28"},
                {"Display", "SH1106 OLED 1.3\""},
                {"Protocol", "TCP Sockets"},
                {"Build", "Maven"}
        };

        for (int i = 0; i < stack.length; i++) {
            Label key = new Label(stack[i][0]);
            key.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
            key.setStyle("-fx-text-fill: #495057;");

            Label val = new Label(stack[i][1]);
            val.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 13;");

            grid.add(key, 0, i);
            grid.add(val, 1, i);
        }

        VBox section = new VBox(12, heading, grid);
        section.setAlignment(Pos.CENTER);
        section.setPadding(new Insets(20));
        section.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");
        return section;
    }

    private HBox buildFooter() {
        Label copy = new Label("\u00A9 2026 Agrilliant. All rights reserved.");
        copy.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 12;");

        HBox footer = new HBox(copy);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(16, 0, 0, 0));
        return footer;
    }
}
