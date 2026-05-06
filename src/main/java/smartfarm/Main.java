package smartfarm;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import smartfarm.server.FarmServer;

/**
 * Entry point for the Smart Farm Management System.
 * Starts the JavaFX dashboard and the TCP server for ESP32 connections.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Apply AtlantaFX theme (swap to NordLight, PrimerDark, Dracula, etc. as desired)
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Parent root = FXMLLoader.load(getClass().getResource("/fxml/dashboard.fxml"));
        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/css/farm-theme.css").toExternalForm());
        primaryStage.setTitle("Agrilliant — Smart Farm Management System");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1300);
        primaryStage.setMinHeight(820);
        primaryStage.setMaximized(true);
        primaryStage.show();

        // Start TCP sensor server on a daemon thread (auto-stops when app closes)
        Thread serverThread = new Thread(() -> new FarmServer().start());
        serverThread.setDaemon(true);
        serverThread.setName("FarmServer-TCP");
        serverThread.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
