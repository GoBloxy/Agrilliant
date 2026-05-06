package smartfarm;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import smartfarm.server.FarmServer;
import smartfarm.service.AlertService;
import smartfarm.service.TelegramService;

import smartfarm.service.TelegramBotListener;
import smartfarm.model.Alert;
import smartfarm.model.SensorReading;
import smartfarm.service.SensorService;
import smartfarm.util.CSVExporter;
import smartfarm.util.TelegramNotifier;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for the Smart Farm Management System.
 * Starts the JavaFX dashboard, the TCP server for ESP32 connections,
 * and the daily Telegram report scheduler.
 */
public class Main extends Application {

    // Scheduler for the daily Telegram report — daemon so it stops when the app closes
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DailyReport-Scheduler");
                t.setDaemon(true);
                return t;
            });

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

        // Start Telegram Bot Command Listener (daemon)
        TelegramNotifier.testConnection(); // Test token on startup
        Thread botThread = new Thread(new TelegramBotListener(new AlertService(), new SensorService()));
        botThread.setDaemon(true);
        botThread.setName("TelegramBot-Listener");
        botThread.start();

        // Schedule daily Telegram report (first run after 24 hours, then every 24 hours)
        scheduleDailyReport();
    }

    /**
     * Schedules the daily farm status report to be sent via Telegram every 24 hours.
     *
     * The task:
     *   1. Fetches all unresolved alerts from AlertService
     *   2. Gets a CSV snapshot of recent sensor data from CSVExporter
     *   3. Delegates to TelegramService.sendDailyReport()
     *
     * Failures are caught and logged — a report error never crashes the app.
     */
    private static void scheduleDailyReport() {
        AlertService alertService = new AlertService();

        SensorService sensorService = new SensorService();

        Runnable reportTask = () -> {
            try {
                System.out.println("[Scheduler] Running daily Telegram report...");
                List<Alert> unresolved = alertService.getUnresolvedAlerts();
                List<SensorReading> recent = sensorService.getRecentReadings(50);
                String csv = CSVExporter.exportSensorData(recent);

                TelegramService.sendDailyReport(unresolved, csv);
                System.out.println("[Scheduler] Daily report dispatched.");

            } catch (RuntimeException e) {
                // Never crash the scheduler — just log and try again tomorrow
                System.err.println("[Scheduler] Daily report failed: " + e.getMessage());
            }
        };

        // First execution: after 24 hours. Repeats every 24 hours.
        scheduler.scheduleAtFixedRate(reportTask, 24, 24, TimeUnit.HOURS);
        System.out.println("[Scheduler] Daily Telegram report scheduled (first run in 24 hours).");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
