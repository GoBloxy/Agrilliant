package smartfarm.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Low-level utility for sending messages and files via the Telegram Bot API.
 *
 * Uses Java's built-in HttpClient (java.net.http) — no external libraries needed.
 * All requests are sent asynchronously to avoid blocking the JavaFX or server threads.
 *
 * Usage:
 *   TelegramNotifier.sendMessage("123456789", "Hello from Agrilliant!");
 *   TelegramNotifier.sendDocument("123456789", "/path/to/report.csv");
 */
public class TelegramNotifier {

    // ── Bot Configuration ──
    private static String BOT_TOKEN = "8772100470:AAGY9oH0kPMgMoP-i3SM9GQ8nmiUseDFqjs";
    private static String API_BASE;

    static {
        // Try to load from db.properties if available
        try (java.io.InputStream input = TelegramNotifier.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (input != null) {
                java.util.Properties prop = new java.util.Properties();
                prop.load(input);
                String token = prop.getProperty("telegram.bot.token");
                if (token != null && !token.trim().isEmpty()) {
                    BOT_TOKEN = token.trim();
                    System.out.println("[TelegramNotifier] Using bot token from db.properties");
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("[TelegramNotifier] Could not load db.properties, using fallback token.");
        }
        API_BASE = "https://api.telegram.org/bot" + BOT_TOKEN;
    }

    // Shared HttpClient — thread-safe, reusable across the application
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    // Private constructor — all methods are static
    private TelegramNotifier() {}

    // ═══════════════ SEND TEXT MESSAGE ═══════════════

    /**
     * Sends a text message to a Telegram chat.
     *
     * @param chatId  the recipient's Telegram chat ID
     * @param text    the message body (supports Telegram Markdown)
     * @return a CompletableFuture that completes when the API responds
     */
    public static CompletableFuture<HttpResponse<String>> sendMessage(String chatId, String text) {
        String url = API_BASE + "/sendMessage";

        // Use POST with form body — GET fails for long messages with emojis
        String formBody = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                        + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                        + "&parse_mode=Markdown";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("[Telegram] sendMessage failed: HTTP " + response.statusCode());
                        System.err.println("[Telegram] Response: " + response.body());
                    } else {
                        System.out.println("[Telegram] Message sent to chat " + chatId);
                    }
                    return response;
                })
                .exceptionally(ex -> {
                    System.err.println("[Telegram] sendMessage error: " + ex.getMessage());
                    return null;
                });
    }

    // ═══════════════ SEND DOCUMENT (CSV FILE) ═══════════════

    /**
     * Sends a file (e.g. CSV report) to a Telegram chat using multipart/form-data.
     *
     * @param chatId   the recipient's Telegram chat ID
     * @param filePath absolute path to the file to send
     * @return a CompletableFuture that completes when the API responds
     */
    public static CompletableFuture<HttpResponse<String>> sendDocument(String chatId, String filePath) {
        String url = API_BASE + "/sendDocument";
        String boundary = UUID.randomUUID().toString();

        try {
            Path path = Path.of(filePath);
            byte[] fileBytes = java.nio.file.Files.readAllBytes(path);
            String fileName = path.getFileName().toString();

            // Build multipart/form-data body
            String lineBreak = "\r\n";
            StringBuilder bodyBuilder = new StringBuilder();

            // Part 1: chat_id
            bodyBuilder.append("--").append(boundary).append(lineBreak);
            bodyBuilder.append("Content-Disposition: form-data; name=\"chat_id\"").append(lineBreak);
            bodyBuilder.append(lineBreak);
            bodyBuilder.append(chatId).append(lineBreak);

            // Part 2: document (file) — header only, file bytes appended separately
            String fileHeader = "--" + boundary + lineBreak
                    + "Content-Disposition: form-data; name=\"document\"; filename=\"" + fileName + "\"" + lineBreak
                    + "Content-Type: application/octet-stream" + lineBreak
                    + lineBreak;

            String fileFooter = lineBreak + "--" + boundary + "--" + lineBreak;

            // Combine all parts into a single byte array
            byte[] headerBytes = (bodyBuilder.toString() + fileHeader).getBytes(StandardCharsets.UTF_8);
            byte[] footerBytes = fileFooter.getBytes(StandardCharsets.UTF_8);
            byte[] fullBody = new byte[headerBytes.length + fileBytes.length + footerBytes.length];

            System.arraycopy(headerBytes, 0, fullBody, 0, headerBytes.length);
            System.arraycopy(fileBytes, 0, fullBody, headerBytes.length, fileBytes.length);
            System.arraycopy(footerBytes, 0, fullBody, headerBytes.length + fileBytes.length, footerBytes.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fullBody))
                    .build();

            return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            System.err.println("[Telegram] sendDocument failed: HTTP " + response.statusCode());
                            System.err.println("[Telegram] Response: " + response.body());
                        } else {
                            System.out.println("[Telegram] Document sent to chat " + chatId + ": " + fileName);
                        }
                        return response;
                    })
                    .exceptionally(ex -> {
                        System.err.println("[Telegram] sendDocument error: " + ex.getMessage());
                        return null;
                    });

        } catch (IOException e) {
            System.err.println("[Telegram] Failed to read file: " + filePath + " — " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    // ═══════════════ GET UPDATES (POLLING) ═══════════════

    /**
     * Polls for new messages. Includes detailed logging of HTTP failures.
     */
    public static CompletableFuture<String> getUpdates(int offset) {
        String url = API_BASE + "/getUpdates?timeout=30";
        if (offset != -1) {
            url += "&offset=" + offset;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("[Telegram] getUpdates failed: HTTP " + response.statusCode());
                        System.err.println("[Telegram] Response body: " + response.body());
                        return null;
                    }
                    return response.body();
                })
                .exceptionally(ex -> {
                    System.err.println("[Telegram] getUpdates network error: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Verifies if the BOT_TOKEN is valid by calling getMe.
     * Useful for debugging connection issues on startup.
     */
    public static void testConnection() {
        System.out.println("[Telegram] Testing bot token connection...");
        
        // Force delete any existing webhooks to prevent "409 Conflict" errors
        deleteWebhook();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/getMe"))
                .GET()
                .build();

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        System.out.println("[Telegram] ✅ Connection successful! Bot details: " + response.body());
                    } else if (response.statusCode() == 409) {
                        System.err.println("[Telegram] ❌ Conflict Error (409): Another bot instance is likely running or a webhook is active.");
                        System.err.println("[Telegram] ❌ Recommendation: Close all other Java processes and try again.");
                    } else {
                        System.err.println("[Telegram] ❌ Connection failed: HTTP " + response.statusCode());
                        System.err.println("[Telegram] ❌ Response: " + response.body());
                    }
                });
    }

    /**
     * Deletes any existing webhook for this bot to allow long polling.
     */
    public static void deleteWebhook() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/deleteWebhook?drop_pending_updates=true"))
                .GET()
                .build();
        
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        System.out.println("[Telegram] Webhook cleared (if any).");
                    }
                });
    }
}
