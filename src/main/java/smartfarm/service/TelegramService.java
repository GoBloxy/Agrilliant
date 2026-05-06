package smartfarm.service;

import smartfarm.model.Alert;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import smartfarm.util.TelegramNotifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business-level Telegram notification service.
 *
 * Responsibilities:
 *   1. sendDailyReport()  — sends a summary of unresolved alerts + CSV to the farmer
 *   2. notifyWorkerOfTask() — notifies a specific worker about a newly assigned task
 *
 * Uses TelegramNotifier (util layer) for the actual HTTP calls.
 * All sends are fire-and-forget (async) — failures are logged but never thrown.
 */
public class TelegramService {

    // ── Farmer Configuration (replace with your actual Telegram chat ID) ──
    private static final String FARMER_CHAT_ID = "YOUR_FARMER_CHAT_ID";

    // Timestamp format used in report headers
    private static final DateTimeFormatter REPORT_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMM d yyyy — hh:mm a");

    // Private constructor — all methods are static
    private TelegramService() {}

    // ═══════════════ DAILY REPORT ═══════════════

    /**
     * Sends the daily farm status report to the farmer's Telegram.
     *
     * The message includes:
     *   - Report timestamp
     *   - Count of critical and total unresolved alerts
     *   - Bullet-list of each critical alert's message
     *   - The raw CSV content appended at the bottom
     *
     * @param unresolved  list of currently unresolved alerts (from AlertService)
     * @param csvContent  pre-built CSV string to append (from CSVExporter)
     */
    /**
     * Sends a report to a SPECIFIC chat ID (e.g. when requested by a user/worker).
     */
    public static void sendDailyReport(String chatId, List<Alert> unresolved, String csvContent) {
        String timestamp = LocalDateTime.now().format(REPORT_FMT);
        List<Alert> critical = unresolved.stream()
                .filter(Alert::isCritical)
                .collect(Collectors.toList());

        StringBuilder msg = new StringBuilder();
        msg.append("🌾 *Agrilliant Farm Status Update*\n");
        msg.append("🕐 ").append(timestamp).append("\n");
        msg.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

        msg.append("📊 *Current Summary*\n");
        msg.append("🔴 Critical alerts:    *").append(critical.size()).append("*\n");
        msg.append("⚠️  Unresolved alerts: *").append(unresolved.size()).append("*\n\n");

        if (critical.isEmpty()) {
            msg.append("✅ No critical alerts — all systems normal.\n\n");
        } else {
            msg.append("🚨 *Critical Alert Details*\n");
            for (Alert alert : critical) {
                msg.append("  • [Plot ").append(alert.getPlotId()).append("] ")
                   .append(alert.getMessage()).append("\n");
            }
            msg.append("\n");
        }

        if (csvContent != null && !csvContent.isBlank()) {
            msg.append("━━━━━━━━━━━━━━━━━━━━━━\n");
            msg.append("📄 *Recent Sensor Data*\n");
            msg.append("```\n");
            String truncated = csvContent.length() > 1000
                    ? csvContent.substring(0, 1000) + "\n... (truncated)"
                    : csvContent;
            msg.append(truncated).append("\n");
            msg.append("```");
        }

        TelegramNotifier.sendMessage(chatId, msg.toString());
    }

    public static void sendDailyReport(List<Alert> unresolved, String csvContent) {
        sendDailyReport(FARMER_CHAT_ID, unresolved, csvContent);
    }

    // ═══════════════ WORKER TASK NOTIFICATION ═══════════════

    /**
     * Notifies a worker via Telegram that a new task has been assigned to them.
     *
     * @param worker  the worker to notify (must have a non-null telegramChatId)
     * @param task    the task that was assigned
     */
    public static void notifyWorkerOfTask(Worker worker, Task task) {
        String chatId = worker.getTelegramChatId();

        // Guard: skip if the worker has no Telegram configured
        if (chatId == null || chatId.isBlank()) {
            System.out.println("[TelegramService] Worker " + worker.getFullName()
                    + " has no Telegram chat ID — notification skipped.");
            return;
        }

        String taskUrl = "https://agrilliant.app/tasks/" + task.getTaskId();

        // ── Build the message ──
        StringBuilder msg = new StringBuilder();
        msg.append("👷 *New Task Assigned — Agrilliant*\n\n");
        msg.append("Hello, *").append(worker.getFullName()).append("*!\n");
        msg.append("You have been assigned a new farm task.\n\n");

        msg.append("📋 *Task Details*\n");
        msg.append("📝 Description: ").append(task.getDescription()).append("\n");
        msg.append("📍 Plot ID:      *").append(task.getPlotId()).append("*\n");
        msg.append("📅 Due Date:     *").append(task.getDueDate()).append("*\n\n");

        msg.append("🔗 [View full task details](").append(taskUrl).append(")\n\n");
        msg.append("_Please acknowledge this task as soon as possible._");

        // Send async — failure logs to stderr but never throws
        TelegramNotifier.sendMessage(chatId, msg.toString())
                .thenAccept(response -> {
                    if (response != null && response.statusCode() == 200) {
                        System.out.println("[TelegramService] Worker '" + worker.getFullName()
                                + "' notified of task #" + task.getTaskId());
                    }
                });
    }
}
