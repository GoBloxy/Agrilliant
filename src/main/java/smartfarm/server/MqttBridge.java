package smartfarm.server;

import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

/**
 * Server-side MQTT publisher.
 * After FarmServer receives sensor data over TCP (raw sockets),
 * this bridge re-publishes it to an MQTT broker so that ALL connected
 * JavaFX clients (on any machine) receive the data in real time.
 *
 * Uses its own background thread managed by the Paho client (built on TCP sockets).
 */
public class MqttBridge {

    private static MqttBridge instance;

    private MqttAsyncClient client;
    private String topicPrefix;
    private int qos;
    private final Gson gson = new Gson();
    private volatile boolean connected = false;

    private MqttBridge() {}

    public static synchronized MqttBridge getInstance() {
        if (instance == null) instance = new MqttBridge();
        return instance;
    }

    /**
     * Connect to the MQTT broker. Call once during app startup.
     * Runs connection on a daemon thread so it doesn't block the UI.
     */
    public void connect() {
        Thread connectThread = new Thread(() -> {
            try {
                Properties props = loadConfig();
                String broker = props.getProperty("mqtt.broker", "tcp://broker.hivemq.com:1883");
                topicPrefix = props.getProperty("mqtt.topic.prefix", "agrilliant/sensors");
                qos = Integer.parseInt(props.getProperty("mqtt.qos", "1"));
                String clientId = props.getProperty("mqtt.client.id.prefix", "agrilliant-")
                        + "server-" + UUID.randomUUID().toString().substring(0, 8);

                client = new MqttAsyncClient(broker, clientId, new MemoryPersistence());
                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setCleanSession(true);
                opts.setAutomaticReconnect(true);
                opts.setConnectionTimeout(10);
                opts.setKeepAliveInterval(30);

                // Optional auth (for HiveMQ Cloud or private brokers)
                String user = props.getProperty("mqtt.username");
                String pass = props.getProperty("mqtt.password");
                if (user != null && !user.isBlank()) opts.setUserName(user);
                if (pass != null && !pass.isBlank()) opts.setPassword(pass.toCharArray());

                // TLS support — required for ssl:// brokers like HiveMQ Cloud
                if (broker.startsWith("ssl://")) {
                    opts.setSocketFactory(javax.net.ssl.SSLSocketFactory.getDefault());
                }

                client.connect(opts).waitForCompletion(10_000);
                connected = true;
                System.out.println("[MqttBridge] Connected to broker: " + broker);

            } catch (Exception e) {
                System.err.println("[MqttBridge] Failed to connect: " + e.getMessage());
                connected = false;
            }
        }, "MqttBridge-Connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /**
     * Publish a sensor reading to the MQTT broker.
     * Called from SensorService after TCP handler delivers a reading.
     * Non-blocking — uses async publish.
     */
    public void publishReading(String deviceCode, float temperature, float humidity, float soilMoisture) {
        if (!connected || client == null) return;

        try {
            SensorPayload payload = new SensorPayload(deviceCode, temperature, humidity, soilMoisture,
                    System.currentTimeMillis());
            String json = gson.toJson(payload);
            String topic = topicPrefix + "/" + deviceCode;

            MqttMessage msg = new MqttMessage(json.getBytes());
            msg.setQos(qos);
            msg.setRetained(false);

            client.publish(topic, msg);

        } catch (MqttException e) {
            System.err.println("[MqttBridge] Publish failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                System.out.println("[MqttBridge] Disconnected");
            }
        } catch (MqttException e) {
            System.err.println("[MqttBridge] Disconnect error: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/mqtt.properties")) {
            if (is != null) props.load(is);
        } catch (IOException e) {
            System.err.println("[MqttBridge] Could not load mqtt.properties, using defaults");
        }
        return props;
    }

    // JSON payload structure shared between publisher and subscriber
    public static class SensorPayload {
        public String deviceCode;
        public float temperature;
        public float humidity;
        public float soilMoisture;
        public long timestamp;

        public SensorPayload() {}

        public SensorPayload(String deviceCode, float temperature, float humidity, float soilMoisture, long timestamp) {
            this.deviceCode = deviceCode;
            this.temperature = temperature;
            this.humidity = humidity;
            this.soilMoisture = soilMoisture;
            this.timestamp = timestamp;
        }
    }
}
