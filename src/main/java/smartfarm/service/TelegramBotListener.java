package smartfarm.service;

import smartfarm.model.Alert;
import smartfarm.model.SensorReading;
import smartfarm.util.CSVExporter;
import smartfarm.util.TelegramNotifier;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Background listener that polls the Telegram Bot API for incoming commands.
 * 
 * Commands supported:
 *  - "report", "/report", "give me report" -> sends the current alert summary.
 *  - "/start" -> sends a welcome message and reveals the user's chat ID.
 */
public class TelegramBotListener implements Runnable {

    private final AlertService alertService;
    private final SensorService sensorService;
    private int lastUpdateId = -1;
    private boolean running = true;

    public TelegramBotListener(AlertService alertService, SensorService sensorService) {
        this.alertService = alertService;
        this.sensorService = sensorService;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        System.out.println("[BotListener] Telegram Bot Listener started...");
        
        while (running) {
            try {
                // Poll for updates (long polling is handled by the timeout param in getUpdates)
                String jsonResponse = TelegramNotifier.getUpdates(lastUpdateId + 1).get();

                if (jsonResponse != null && jsonResponse.contains("\"ok\":true")) {
                    processUpdates(jsonResponse);
                }

                // Small delay to prevent tight loop if an error occurs
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("[BotListener] Error polling updates: " + e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void processUpdates(String json) {
        // Regex to find message objects: {"message_id":..., "chat":{"id":123...}, "text":"..."}
        // This is a simplified regex for parsing JSON without a library
        Pattern updatePattern = Pattern.compile("\"update_id\":(\\d+)");
        Pattern chatPattern = Pattern.compile("\"chat\":\\{\"id\":(-?\\d+)");
        Pattern textPattern = Pattern.compile("\"text\":\"([^\"]+)\"");

        Matcher updateMatcher = updatePattern.matcher(json);
        int lastFoundId = lastUpdateId;

        // Find all update_ids in the response
        while (updateMatcher.find()) {
            int updateId = Integer.parseInt(updateMatcher.group(1));
            if (updateId > lastFoundId) {
                lastFoundId = updateId;
                
                // For this update, find the chat ID and text
                // Note: This assumes one message per update and sequential ordering in JSON
                int startPos = updateMatcher.start();
                int endPos = json.indexOf("\"update_id\"", startPos + 1);
                if (endPos == -1) endPos = json.length();
                
                String updateBlock = json.substring(startPos, endPos);
                
                Matcher chatMatcher = chatPattern.matcher(updateBlock);
                Matcher textMatcher = textPattern.matcher(updateBlock);
                
                if (chatMatcher.find() && textMatcher.find()) {
                    String chatId = chatMatcher.group(1);
                    String text = textMatcher.group(1).toLowerCase();
                    
                    handleCommand(chatId, text);
                }
            }
        }
        lastUpdateId = lastFoundId;
    }

    private void handleCommand(String chatId, String text) {
        System.out.println("[BotListener] Processing command: '" + text + "' from " + chatId);

        try {
            if (text.contains("report") || text.equals("/report")) {
                List<Alert> unresolved = alertService.getUnresolvedAlerts();
                List<SensorReading> recent = sensorService.getRecentReadings(20); // Last 20 readings
                String csv = CSVExporter.exportSensorData(recent);
                
                TelegramService.sendDailyReport(chatId, unresolved, csv);
                System.out.println("[BotListener] Full report (with CSV) sent to " + chatId);
            }
            else if (text.equals("/status")) {
                List<Alert> unresolved = alertService.getUnresolvedAlerts();
                long criticalCount = unresolved.stream().filter(Alert::isCritical).count();
                
                String status = "📊 *Farm Status Summary*\n\n" +
                               "🔴 Critical Alerts: *" + criticalCount + "*\n" +
                               "⚠️ Total Unresolved: *" + unresolved.size() + "*\n\n" +
                               "Type `/alerts` for details or `/report` for the full status.";
                TelegramNotifier.sendMessage(chatId, status);
            }
            else if (text.equals("/alerts")) {
                List<Alert> unresolved = alertService.getUnresolvedAlerts();
                if (unresolved.isEmpty()) {
                    TelegramNotifier.sendMessage(chatId, "✅ *No active alerts!* Your farm is running smoothly.");
                    return;
                }
                
                StringBuilder msg = new StringBuilder("🚨 *Active Alerts List*\n\n");
                for (Alert a : unresolved) {
                    String emoji = a.isCritical() ? "🔴" : "⚠️";
                    msg.append(emoji).append(" *").append(a.getAlertType()).append("*\n")
                       .append("└ ").append(a.getMessage()).append("\n\n");
                }
                TelegramNotifier.sendMessage(chatId, msg.toString());
            }
            else if (text.equals("/start") || text.equals("/help")) {
                String help = "👋 *Agrilliant Bot Command Menu*\n\n" +
                              "🔹 `/status` — Quick health summary\n" +
                              "🔹 `/report` — Full daily report\n" +
                              "🔹 `/alerts` — List all active alerts\n" +
                              "🔹 `/help` — Show this menu\n\n" +
                              "Your Chat ID: `" + chatId + "`";
                TelegramNotifier.sendMessage(chatId, help);
            }
            else {
                TelegramNotifier.sendMessage(chatId, "❓ Unknown command. Try `/help` to see what I can do.");
            }
        } catch (Exception e) {
            System.err.println("[BotListener] Error executing command '" + text + "': " + e.getMessage());
            e.printStackTrace();
            TelegramNotifier.sendMessage(chatId, "❌ *Bot Error*\nAn error occurred while processing your request. Please try again later.");
        }
    }
}
