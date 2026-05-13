package smartfarm;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import smartfarm.model.User;
import smartfarm.server.FarmServer;
import smartfarm.server.MqttBridge;
import smartfarm.server.MqttSensorSubscriber;
import smartfarm.service.AuthService;
import smartfarm.service.SessionManager;
import smartfarm.ui.DashboardController;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Parent root;
        String savedEmail = SessionManager.loadSession();
        User restoredUser = null;

        // Try to restore previous session
        if (savedEmail != null) {
            try {
                AuthService authService = new AuthService();
                restoredUser = authService.restoreSession(savedEmail);
            } catch (Exception e) {
                SessionManager.clearSession();
            }
        }

        if (restoredUser != null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            root = loader.load();
            DashboardController controller = loader.getController();
            controller.setCurrentUser(restoredUser);
        } else {
            root = FXMLLoader.load(getClass().getResource("/fxml/signin.fxml"));
        }

        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(getClass().getResource("/css/farm-theme.css").toExternalForm());
        primaryStage.setTitle("Agrilliant — Smart Farm Management System");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1300);
        primaryStage.setMinHeight(820);
        primaryStage.setMaximized(true);
        primaryStage.setOnCloseRequest(e -> {
            System.out.println("Shutting down...");
            System.exit(0);
        });
        primaryStage.show();

        // Start TCP sensor server on a daemon thread (auto-stops when app closes)
        Thread serverThread = new Thread(() -> new FarmServer().start());
        serverThread.setDaemon(true);
        serverThread.setName("FarmServer-TCP");
        serverThread.start();

        // Start MQTT bridge (publishes TCP-received data to broker for other clients)
        MqttBridge.getInstance().connect();

        // Start MQTT subscriber (receives data from broker → LiveSensorData → UI)
        MqttSensorSubscriber.getInstance().start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
