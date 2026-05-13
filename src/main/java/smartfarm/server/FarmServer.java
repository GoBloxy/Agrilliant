// Desktop build only — Android cannot keep a server socket alive in
// the background, so this class is profile-guarded out of the Android
// Maven profile and gated at runtime via `Constants.IS_ANDROID` in Main.
package smartfarm.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FarmServer {
    private static final int PORT = 8080;

    // Starts the server — blocks forever, so run this on a background thread
    public void start() {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Farm Server started on port " + PORT);

            while (true) {
                Socket deviceSocket = serverSocket.accept();
                System.out.println("Device connected: " + deviceSocket.getInetAddress());
                threadPool.submit(new SensorHandler(deviceSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
