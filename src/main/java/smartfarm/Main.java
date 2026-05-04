package smartfarm;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for the Smart Farm Management System.
 * Starts the JavaFX dashboard and the TCP server for ESP32 connections.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Apply AtlantaFX theme (swap to NordLight, PrimerDark, Dracula, etc. as desired)
        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());

        // TODO: Load dashboard.fxml, set scene, show stage
        // TODO: Start FarmServer on a background thread
    }

    public static void main(String[] args) {
        launch(args);
    }
}
